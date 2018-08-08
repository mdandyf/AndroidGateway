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

    private ExecutionTask<String> executionTask;

    public FairExhaustivePolling(Context context, boolean mProcessing, IGatewayService iGatewayService) {
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

    public void start() {
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N * 2);

        executionTask.scheduleWithThreadPoolExecutor(new FEPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        executionTask.scheduleWithThreadPoolExecutor(new FEPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS);
    }

    public void stop() {
        mProcessing = false;
        executionTask.stopScheduler();
        executionTask.terminateScheduler();
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
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME_HALF);
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
                        if (!mProcessing) { return; }

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
