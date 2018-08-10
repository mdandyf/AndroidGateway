package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm.ANP;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PriorityBasedWithANP {

    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds
    private static final int NUMBER_OF_MAX_CONNECT_DEVICES = 10; // set max 10 devices connect before listening to disconnection time

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledThreadPoolExecutor scheduler2;
    private ScheduledFuture<?> future;
    private ScheduledFuture<?> future2;
    private Thread threadMeasurePower;

    private Thread sleepThread;
    private IGatewayService iGatewayService;
    private PowerEstimator powerEstimator;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private boolean isBroadcastRegistered;

    private int cycleCounter = 0;
    private int maxConnectTime = 0;
    private long powerUsage = 0;
    private int connectCounter = 0;

    private ExecutionTask<String> executionTask;

    public PriorityBasedWithANP(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }

    public void stop() {
        future.cancel(true);future2.cancel(true);
        scheduler.shutdownNow();scheduler2.shutdownNow();
    }

    public void start() {
        try {
            isBroadcastRegistered = false;
            powerEstimator = new PowerEstimator(context);

            int N = Runtime.getRuntime().availableProcessors();
            executionTask = new ExecutionTask<>(N, N*2);
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
                if(cycleCounter > 1) {broadcastClrScrn();}
                broadcastUpdate("Start new cycle");

                broadcastUpdate("Cycle number " + cycleCounter);
                mProcessing = true;
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {

                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) { iGatewayService.startScanKnownDevices(device);/*iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null, 0);*/ }

                    // do normal scanning only for half of normal scanning time
                    /*iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME / 2);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();*/

                    iGatewayService.startScan(SCAN_TIME / 2);
                    iGatewayService.stopScanning();
                    mScanning = iGatewayService.getScanState();

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
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);*/

                    iGatewayService.startScan(SCAN_TIME);
                    iGatewayService.stopScanning();

                    //iGatewayService.execScanningQueue();
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
                iGatewayService.setScanResultNonVolatile(scanResults);

                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time for all devices is " + maxConnectTime / 1000 + " s");
                } else {
                    return;
                }

                // do connecting by Round Robin
                for (final BluetoothDevice device : scanResults) {
                    broadcastServiceInterface("Start service interface");
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    setMeasurePower("Start", device);
                    PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) { return; }

                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");

                    setMeasurePower("Stop", device);

                    iGatewayService.updateDatabaseDevicePowerUsage(device.getAddress(), powerUsage);
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
                iGatewayService.setScanResultNonVolatile(scanResults);

                Map<BluetoothDevice, Double> mapRankedDevices;
                if (scanResults.size() != 0) {
                    broadcastUpdate("\n");
                    broadcastUpdate("Start ranking device with ANP algorithm...");
                    mapRankedDevices = doRankDeviceANP(scanResults);
                    broadcastUpdate("Finish ranking device...");
                } else {
                    broadcastUpdate("No nearby device(s) available...");
                    return;
                }

                // calculate timer for connection (to obtain Round Robin Scheduling)
                int remainingTime = PROCESSING_TIME - SCAN_TIME;

                if(mapRankedDevices.size() > 0) {
                    broadcastUpdate("\n");
                    maxConnectTime = remainingTime / mapRankedDevices.size();
                    broadcastUpdate("Connecting to " + mapRankedDevices.size() + " device(s)");
                    broadcastUpdate("Maximum connection time for all devices is " + maxConnectTime / 1000 + " s");
                    broadcastUpdate("\n");

                    connect(mapRankedDevices, remainingTime);
                } else {
                    broadcastUpdate("No nearby device(s) available");
                    return;
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // implementation of connect after ranking the devices
        private void connect(Map<BluetoothDevice, Double> mapRankedDevices, int remainingTime) {
            try {
                for (Map.Entry entry : mapRankedDevices.entrySet()) {
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    setMeasurePower("Start", device);
                    Thread connectingThread = executionTask.executeRunnableInThread(doConnecting(device.getAddress()), "Thread Connecting " + device.getAddress(), Thread.MAX_PRIORITY);

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) { return; }

                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(10);
                    executionTask.interruptThread(connectingThread);

                    setMeasurePower("Stop", device);
                    iGatewayService.updateDatabaseDevicePowerUsage(device.getAddress(), powerUsage);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        // implementation of Ranking Devices based on ANP
        private Map<BluetoothDevice, Double> doRankDeviceANP(List<BluetoothDevice> devices) {
            Map<BluetoothDevice, Double> result = new ConcurrentHashMap<>();
            try {
                ANP anp = new ANP(devices, iGatewayService, powerEstimator.getBatteryRemainingPercent());
                broadcastUpdate("Sorting devices by their priorities...");
                result = anp.call();
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
                    if (currentNow < 0) { currentNow = currentNow * -1; }
                    powerUsage = powerUsage + (currentNow * new Long(powerEstimator.getVoltageNow()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private synchronized void setMeasurePower(String type, BluetoothDevice device) {
        switch (type) {
            case "Start":
                powerUsage = 0;
                powerEstimator.start();
                threadMeasurePower = executionTask.executeRunnableInThread(doMeasurePower(), "Thread Power Measure " + device.getAddress(), Thread.MIN_PRIORITY);
                break;
            case "Stop":
                threadMeasurePower.interrupt();
                powerEstimator.stop();
                break;
        }
    }

    private Runnable doConnecting(final String macAddress) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.doConnect(macAddress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void waitThread(long time) {
        try {
            sleepThread = Thread.currentThread();
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
