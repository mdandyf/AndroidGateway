package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                cycleCounter++;
                iGatewayService.setCycleCounter(cycleCounter);
                if(cycleCounter > 1) {broadcastClrScrn();}
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                broadcastUpdate("Cycle number " + cycleCounter);
                // do polling slaves part
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {
                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) {
                        iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null, 0);
                    }
                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME / 2);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
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
            if (mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if (!mProcessing) {
                future.cancel(true);
                return;
            }
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

            if (!mProcessing) {
                future.cancel(true);
                return;
            }

            if (devices == null || devices.size() <= 0) { return; }

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;
            ;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                try {
                    broadcastServiceInterface("Start service interface");
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (devices.contains(device.getAddress())) {
                    try {
                        PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());
                        // set timer to xx seconds
                        waitThread(maxConnectTime);
                        if (!mProcessing) {
                            future.cancel(true);
                            return;
                        }
                        broadcastUpdate("Wait time finished, disconnected...");
                        iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");
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
                future2.cancel(true);
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

    private void waitThread(int time) {
        if (!!mProcessing) {
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

    private void broadcastServiceInterface(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
            intent.putExtra("message", message);
            context.sendBroadcast(intent);
        }
    }

}
