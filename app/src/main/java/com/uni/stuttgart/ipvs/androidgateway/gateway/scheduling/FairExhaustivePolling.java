package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

// implementation Fair Exhaustive Polling (FEP) Scheduling Gateway Controller
public class FairExhaustivePolling {
    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledThreadPoolExecutor scheduler2;
    private ScheduledFuture<?> future;
    private ScheduledFuture<?> future2;

    private IGatewayService iGatewayService;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;

    private Thread thread;
    private ExecutionTask<String> executionTask;

    public FairExhaustivePolling(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }

    public void start() {
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N * 2);

        scheduler = executionTask.scheduleWithThreadPoolExecutor(new FEPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        future = executionTask.getFuture();

        scheduler2 = executionTask.scheduleWithThreadPoolExecutor(new FEPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS);
        future2 = executionTask.getFuture();
    }

    public void stop() {
        mProcessing = false;
        future.cancel(true);
        future2.cancel(true);
        scheduler.shutdownNow();
        scheduler2.shutdownNow();
    }

    private class FEPStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                iGatewayService.setProcessing(mProcessing);
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                // do polling slaves part
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {
                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) { iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null); }
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                    waitThread(SCAN_TIME/2); // if timer fails, force to stop
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                    waitThread(SCAN_TIME); // if timer fails, force to stop
                }

                stop();
                waitThread(100);
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            broadcastUpdate("\n");
            if(mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if (!mProcessing) { future.cancel(true); return; }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            List<String> devices = null;
            try {
                scanResults = iGatewayService.getScanResults();
                devices = iGatewayService.getListActiveDevices();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            if (!mProcessing) { future.cancel(true); return; }
            if(devices == null || devices.size() <= 0) { return; }

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : scanResults) {
                try {
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (devices.contains(device.getAddress())) {
                    thread = executionTask.executeRunnableInThread(doConnecting(device.getAddress()), "Connecting " + device.getAddress(), 10);
                    processUserChoiceAlert(device.getAddress(), device.getName());
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) { future.cancel(true); return; }
                    broadcastUpdate("Wait time finished, disconnected...");
                    try {
                        iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    waitThread(10);
                    thread.interrupt();
                }
            }
        }
    }

    private class FEPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            if (!mProcessing) { future2.cancel(true); return; }
            broadcastUpdate("Update all device states...");
            try {
                iGatewayService.updateAllDeviceStates(null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
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

    private void waitThread(int time) {
        if (!!mProcessing) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            context.sendBroadcast(intent);
        }
    }

    private void processUserChoiceAlert(String macAddress, String deviceName) {
        try {
            String userChoice = iGatewayService.getDeviceUsrChoice(macAddress);
            if(deviceName == null) {deviceName = "Unknown";};
            if(userChoice == null || userChoice == "") broadcastAlertDialog("Start Service Interface of Device " + macAddress + "-" + deviceName, macAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void broadcastAlertDialog(String message, String macAddress) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.USER_CHOICE_SERVICE);
            intent.putExtra("message", message);
            intent.putExtra("macAddress", macAddress);
            context.sendBroadcast(intent);
        }
    }

}
