package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm.WSM;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PriorityBasedWithWSM {

    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_HALF; // set scanning and reading time half of original scan time
    private int PROCESSING_TIME; // set processing time to 60 seconds
    private int TIME_MEASURE_POWER = 500; // measure power every 0.5 seconds

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledThreadPoolExecutor scheduler2;
    private ScheduledThreadPoolExecutor schedulerPowerMeasure;
    private ScheduledFuture<?> future;
    private ScheduledFuture<?> future2;

    private IGatewayService iGatewayService;
    private PowerEstimator powerEstimator;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private boolean mConnecting;

    private int cycleCounter = 0;
    private int maxConnectTime = 0;
    private long powerUsage = 0;

    private ExecutionTask<String> executionTask;

    public PriorityBasedWithWSM(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        try {
            this.SCAN_TIME = iGatewayService.getTimeSettings("ScanningTime");
            this.SCAN_TIME_HALF = iGatewayService.getTimeSettings("ScanningTime2");
            this.PROCESSING_TIME = iGatewayService.getTimeSettings("ProcessingTime");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        mConnecting = false;
        future.cancel(true);
        future2.cancel(true);
        scheduler.shutdownNow();
        scheduler2.shutdownNow();
    }

    public void start() {
        try {
            mConnecting = false;
            powerEstimator = new PowerEstimator(context);

            int N = Runtime.getRuntime().availableProcessors();
            executionTask = new ExecutionTask<>(N, N * 2);
            scheduler = executionTask.scheduleWithThreadPoolExecutor(new FPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
            future = executionTask.getFuture();

            scheduler2 = executionTask.scheduleWithThreadPoolExecutor(new FPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS);
            future2 = executionTask.getFuture();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class FPStartScanning implements Runnable {

        @Override
        public void run() {
            try {
                cycleCounter++;
                iGatewayService.setCycleCounter(cycleCounter);
                if (cycleCounter > 1) {
                    broadcastClrScrn();
                }
                broadcastUpdate("Start new cycle");

                broadcastUpdate("Cycle number " + cycleCounter);
                mProcessing = true;
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {

                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) {
                        //iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null, 0);

                        iGatewayService.startScanKnownDevices(device);
                    }

                    // do normal scanning only for half of normal scanning time
                    /*iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME_HALF);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();*/

                    iGatewayService.startScan(SCAN_TIME_HALF);
                    iGatewayService.stopScanning();
                    mScanning = iGatewayService.getScanState();

                    if (!mProcessing) {
                        future.cancel(false);
                        return;
                    }

                    waitThread(100);

                    if (!mProcessing) {
                        future.cancel(false);
                        return;
                    }
                    connectFP();
                } else {
                    // do normal scanning
                    /*iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();*/

                    iGatewayService.startScan(SCAN_TIME);
                    iGatewayService.stopScanning();
                    mScanning = iGatewayService.getScanState();

                    if (!mProcessing) {
                        future.cancel(false);
                        return;
                    }

                    waitThread(100);

                    if (!mProcessing) {
                        future.cancel(false);
                        return;
                    }
                    connectRR();
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 1st iteration connect using Round Robin Method
        private void connectRR() {
            try {
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time for all devices is " + maxConnectTime / 1000 + " s");
                } else {
                    return;
                }

                // do connecting by Round Robin
                for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                    mConnecting = true;
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                    broadcastServiceInterface("Start service interface");

                    startStopPowerMeasure(device, "Start");

                    PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());
                    schedulerPowerMeasure = executionTask.scheduleWithThreadPoolExecutor(doMeasurePower(), 0, TIME_MEASURE_POWER, TimeUnit.MILLISECONDS);

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        return;
                    }

                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");

                    schedulerPowerMeasure.shutdownNow();
                    startStopPowerMeasure(device, "Stop");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2nd and so on iterations, connect using Fixed Priority Scheduling by device ranking
        private void connectFP() {
            try {
                /*registerBroadcast(); // start listening to disconnected Gatt and or finished read data*/
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                Map<BluetoothDevice, Double> mapRankedDevices;
                if (scanResults.size() != 0) {
                    broadcastUpdate("\n");
                    broadcastUpdate("Start ranking device with WSM algorithm...");
                    mapRankedDevices = doRankDeviceWSM(scanResults);
                    broadcastUpdate("Finish ranking device...");
                } else {
                    broadcastUpdate("No nearby device(s) available...");
                    return;
                }

                // calculate timer for connection (to obtain Round Robin Scheduling)
                int remainingTime = PROCESSING_TIME - SCAN_TIME;

                if (mapRankedDevices.size() > 0) {
                    broadcastUpdate("\n");
                    maxConnectTime = remainingTime / mapRankedDevices.size();
                    broadcastUpdate("Connecting to " + mapRankedDevices.size() + " device(s)");
                    broadcastUpdate("Maximum connection time for all devices is " + maxConnectTime / 1000 + " s");
                    broadcastUpdate("\n");

                    connect(mapRankedDevices);

                } else {
                    broadcastUpdate("No nearby device(s) available");
                    return;
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // implementation of connect after ranking the devices
        private void connect(Map<BluetoothDevice, Double> mapRankedDevices) {
            try {
                for (Map.Entry entry : mapRankedDevices.entrySet()) {
                    mConnecting = true;
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    startStopPowerMeasure(device, "Start");

                    PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());
                    schedulerPowerMeasure = executionTask.scheduleWithThreadPoolExecutor(doMeasurePower(), 0, TIME_MEASURE_POWER, TimeUnit.MILLISECONDS);

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        return;
                    }

                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");

                    schedulerPowerMeasure.shutdownNow();
                    startStopPowerMeasure(device, "Stop");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        // implementation of Ranking Devices based on WSM
        private Map<BluetoothDevice, Double> doRankDeviceWSM(List<BluetoothDevice> devices) {
            Map<BluetoothDevice, Double> result = new ConcurrentHashMap<>();
            try {
                WSM wsm = new WSM(devices, iGatewayService, powerEstimator.getBatteryRemainingPercent());
                broadcastUpdate("Sorting devices by their priorities...");
                result = wsm.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    private class FPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            if (!mProcessing) {
                future2.cancel(true);
                return;
            }
            broadcastUpdate("Update all device states...");
            if (mProcessing) {
                try {
                    iGatewayService.updateAllDeviceStates(null);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                future2.cancel(false);
            }
        }
    }


    /**
     * =================================================================================================================================
     * Method Routines Section
     * =================================================================================================================================
     */

    private synchronized Runnable doMeasurePower() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    long currentNow = powerEstimator.getCurrentNow();
                    if (currentNow < 0) {
                        currentNow = currentNow * -1;
                    }
                    powerUsage = powerUsage + (currentNow * new Long(powerEstimator.getVoltageNow()));
                    if (!mConnecting) {
                        schedulerPowerMeasure.shutdownNow();return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private synchronized void startStopPowerMeasure(BluetoothDevice mDevice, String type) {
        switch (type) {
            case "Start":
                powerUsage = 0;
                powerEstimator.start();
                break;
            case "Stop":
                mConnecting = false;
                powerEstimator.stop();

                try {
                    iGatewayService.updateDatabaseDevicePowerUsage(mDevice.getAddress(), powerUsage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    private void waitThread(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            context.sendBroadcast(intent);
        }
    }

    private void broadcastClrScrn() {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_NEW_CYCLE);
            context.sendBroadcast(intent);
        }
    }

    private void broadcastServiceInterface(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
            intent.putExtra("message", message);
            context.sendBroadcast(intent);
        }
    }
}
