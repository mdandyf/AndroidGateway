package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm.AHP;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

// implementation of Scheduling using Exhaustive Polling
public class ExhaustivePollingWithAHP implements IGatewayScheduler {

    private IGatewayService iGatewayService;

    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_OTHER; // set scanning and reading time half of original scan time
    private int PROCESSING_TIME; // set processing time to 60 seconds

    private BluetoothDevice mDevice;
    private boolean mScanning;
    private boolean mConnecting;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;

    private ExecutionTask<Void> executionTask;
    private Future<Void> future;
    private Thread threadPowerMeasure;
    private long powerUsage = 0;
    private PowerEstimator powerEstimator;

    public ExhaustivePollingWithAHP(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.executionTask = executionTask;

        try {
            this.SCAN_TIME = iGatewayService.getTimeSettings("ScanningTime");
            this.SCAN_TIME_OTHER = iGatewayService.getTimeSettings("ScanningTime2");
            this.PROCESSING_TIME = iGatewayService.getTimeSettings("ProcessingTime");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        mProcessing = false; mConnecting = false;
        future.cancel(true);
    }

    public void start() {
        mProcessing = true; mConnecting = false;
        powerEstimator = new PowerEstimator(context);
        future = executionTask.submitRunnable(new RunEPScheduling());
    }

    private class RunEPScheduling implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && mProcessing) {
                // do Exhaustive Polling Part
                cycleCounter++;
                if (cycleCounter > 1) { broadcastClrScrn(); }
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                broadcastUpdate("Cycle number " + cycleCounter);

                try {
                    iGatewayService.setCycleCounter(cycleCounter);
                    boolean isDataExist = iGatewayService.checkDevice(null);
                    if (isDataExist) {
                        // Devices are listed in DB
                        List<String> devices = iGatewayService.getListActiveDevices();
                        // search for known device listed in database
                        for (String device : devices) {
                            iGatewayService.startScanKnownDevices(device);
                        }
                        // do normal scanning only for half of normal scanning time

                        iGatewayService.startScan(SCAN_TIME_OTHER);
                        iGatewayService.stopScan();
                        mScanning = iGatewayService.getScanState();

                        if (!mProcessing) {
                            future.cancel(true);
                            Thread.currentThread().interrupt();
                            return;
                        }

                        connectAHP();
                    } else {
                        // do normal scanning

                        iGatewayService.startScan(SCAN_TIME);
                        iGatewayService.stopScan();
                        mScanning = iGatewayService.getScanState();

                        if (!mProcessing) {
                            future.cancel(true);
                            Thread.currentThread().interrupt();
                            return;
                        }

                        connectSemaphore();
                    }

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Log.d("Thread", "Thread EPAHP " + Thread.currentThread().getId() + " is interrupted");
                }
            }

        }

        /**
         * =================================================================================================================================
         * Type of connections
         * =================================================================================================================================
         */

        private void connectSemaphore() {
            try {
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                iGatewayService.setScanResultNonVolatile(scanResults);

                // do Connecting by using Semaphore
                for (final BluetoothDevice device : scanResults) {
                    mConnecting = true;
                    mDevice = device;
                    iGatewayService.broadcastServiceInterface("Start service interface");
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    powerUsage = 0;
                    powerEstimator.start();
                    threadPowerMeasure = executionTask.executeRunnableInThread(doMeasurePower(), "Thread Power" + device.getAddress(), Thread.MIN_PRIORITY);

                    iGatewayService.doConnect(mDevice.getAddress());

                    stopPowerMeasure();

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void connectAHP() {
            try {
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                iGatewayService.setScanResultNonVolatile(scanResults);

                if(scanResults.size() != 0) {
                    broadcastUpdate("Start ranking device with AHP algorithm...");
                } else {
                    broadcastUpdate("No nearby device(s) available...");
                    return;
                }

                Map<BluetoothDevice, Double> mapRankedDevices = doRankDeviceAHP(scanResults);

                // do Connecting by using Semaphore
                for (Map.Entry entry : mapRankedDevices.entrySet()) {
                    BluetoothDevice device = (BluetoothDevice)  entry.getKey();

                    mConnecting = true;
                    mDevice = device;
                    iGatewayService.broadcastServiceInterface("Start service interface");
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    powerUsage = 0;
                    powerEstimator.start();
                    threadPowerMeasure = executionTask.executeRunnableInThread(doMeasurePower(), "Thread Power" + device.getAddress(), Thread.MIN_PRIORITY);

                    iGatewayService.doConnect(mDevice.getAddress());

                    stopPowerMeasure();

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // implementation of Ranking Devices based on AHP
        private Map<BluetoothDevice, Double> doRankDeviceAHP(List<BluetoothDevice> devices) {
            Map<BluetoothDevice, Double> result = new ConcurrentHashMap<>();
            try {
                AHP ahp = new AHP(devices, iGatewayService, powerEstimator.getBatteryRemainingPercent());
                broadcastUpdate("Sorting devices by their priorities...");
                result = ahp.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        /**
         * =================================================================================================================================
         * Device Power Usage Measurement routines
         * =================================================================================================================================
         */

        private synchronized Runnable doMeasurePower() {
            return new Runnable() {
                @Override
                public void run() {
                    while(mConnecting) {
                        try {
                            long currentNow = powerEstimator.getCurrentNow();
                            if (currentNow < 0) { currentNow = currentNow * -1; }
                            powerUsage = powerUsage + (currentNow * new Long(powerEstimator.getVoltageNow()));
                            if(!mConnecting) {threadPowerMeasure.interrupt();break;}
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
        }

        private synchronized void stopPowerMeasure() {
            mConnecting = false;
            powerEstimator.stop();

            try {
                iGatewayService.updateDatabaseDevicePowerUsage(mDevice.getAddress(), powerUsage);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        /**
         * =================================================================================================================================
         * Other routines
         * =================================================================================================================================
         */

        /**
         * Clear the screen in Gateway Tab
         */
        private void broadcastClrScrn() {
            if (mProcessing) {
                final Intent intent = new Intent(GatewayService.START_NEW_CYCLE);
                context.sendBroadcast(intent);
            }
        }

        private void broadcastUpdate(String message) {
            if (mProcessing) {
                final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
                intent.putExtra("command", message);
                context.sendBroadcast(intent);
            }
        }
    }
}
