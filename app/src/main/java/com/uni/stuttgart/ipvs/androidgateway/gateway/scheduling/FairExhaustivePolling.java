package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// implementation Fair Exhaustive Polling (FEP) Scheduling Gateway Controller
public class FairExhaustivePolling {
    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_HALF; // set scanning and reading time to half
    private int PROCESSING_TIME; // set processing time to 60 seconds

    private IGatewayService iGatewayService;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;

    private ExecutionTask<Void> executionTask;
    private ScheduledThreadPoolExecutor scheduleFEP;
    private ScheduledThreadPoolExecutor scheduleDB;
    private Future<?> futureFEP;
    private Future<?> futureDB;

    public FairExhaustivePolling(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.executionTask = executionTask;

        try {
            this.SCAN_TIME = iGatewayService.getTimeSettings("ScanningTime");
            this.SCAN_TIME_HALF = iGatewayService.getTimeSettings("ScanningTime2");
            this.PROCESSING_TIME = iGatewayService.getTimeSettings("ProcessingTime");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        scheduleFEP = executionTask.scheduleWithThreadPoolExecutor(new FEPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        futureFEP = executionTask.getFuture();
        scheduleDB = executionTask.scheduleWithThreadPoolExecutor(new FEPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS);
        futureDB = executionTask.getFuture();
    }

    public void stop() {
        mProcessing = false;
        futureFEP.cancel(true);
        futureDB.cancel(true);
        scheduleFEP.shutdown();
        scheduleFEP.shutdownNow();
        scheduleDB.shutdown();
        scheduleDB.shutdownNow();
    }

    private class FEPStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                cycleCounter++;
                iGatewayService.setCycleCounter(cycleCounter);
                if (cycleCounter > 1) {
                    broadcastClrScrn();
                }
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                broadcastUpdate("Cycle number " + cycleCounter);

                // do polling slaves part
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {
                    // Devices are listed in DB
                    List<String> devices = iGatewayService.getListActiveDevices();
                    // search for known device listed in database
                    for (String device : devices) {
                        iGatewayService.startScanKnownDevices(device);
                    }
                    // do normal scanning only for half of normal scanning time
                    sleepThread(100);
                    iGatewayService.startScan(SCAN_TIME_HALF);
                    mScanning = iGatewayService.getScanState();
                } else {
                    // do normal scanning
                    iGatewayService.startScan(SCAN_TIME);
                    mScanning = iGatewayService.getScanState();
                }

                if (!mProcessing) {
                    futureFEP.cancel(true);
                    Thread.currentThread().interrupt();
                    return;
                }

                stop();
                sleepThread(100);
                connect();


                if (!mProcessing) {
                    futureFEP.cancel(true);
                    Thread.currentThread().interrupt();
                    return;
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Log.d("Thread", "Thread FEP " + Thread.currentThread().getId() + " is interrupted");
            }
        }

        private void stop() {
            broadcastUpdate("\n");
            if (mScanning) {
                try {
                    iGatewayService.stopScanning();
                    mScanning = iGatewayService.getScanState();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if (!mProcessing) {
                futureFEP.cancel(true);
                Thread.currentThread().interrupt();
                return;
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            List<String> devices = null;
            try {
                scanResults = iGatewayService.getScanResults();
                iGatewayService.setScanResultNonVolatile(scanResults);

                devices = iGatewayService.getListActiveDevices();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            if (!mProcessing) {
                futureFEP.cancel(true);
                Thread.currentThread().interrupt();
                return;
            }

            if (devices == null || devices.size() <= 0) {
                return;
            }

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;
            ;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                try {
                    iGatewayService.broadcastServiceInterface("Start service interface");
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (devices.contains(device.getAddress())) {
                    try {
                        PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());

                        // set timer to xx seconds
                        sleepThread(maxConnectTime);
                        broadcastUpdate("Wait time finished, disconnected...");
                        iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");

                        if (!mProcessing) {
                            futureFEP.cancel(true);
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class FEPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            if (!mProcessing) {
                futureDB.cancel(true);
                Thread.currentThread().interrupt();
                return;
            }
            broadcastUpdate("Update all device states...");
            try {
                iGatewayService.updateAllDeviceStates(null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void sleepThread(int time) {
        if (mProcessing) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

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
