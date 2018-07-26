package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.EExecutionType;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// implementation of Semaphore Scheduling Gateway Controller

public class Semaphore {
    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_HALF;
    private int PROCESSING_TIME; // set processing time to 60 seconds

    private IGatewayService iGatewayService;
    private PowerEstimator mServicePE;

    private boolean mBoundPE;
    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;
    private long powerUsage = 0;

    private ExecutionTask<String> executionTask;

    public Semaphore(Context context, boolean mProcessing, IGatewayService iGatewayService) {
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
        mProcessing = false;
        executionTask.terminateExecutorPools();
    }

    public void start() {
        mProcessing = true;
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N*2);
        executionTask.setExecutionType(EExecutionType.SINGLE_THREAD_POOL);
        executionTask.submitRunnable(new SemaphoreSchedulling());
    }

    private class SemaphoreSchedulling implements Runnable {

        @Override
        public void run() {
            while (mProcessing) {
                cycleCounter++;
                if(cycleCounter > 1) {broadcastClrScrn();}
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                broadcastUpdate("Cycle number " + cycleCounter);
                try {
                    iGatewayService.setCycleCounter(cycleCounter);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();

                    mScanning = iGatewayService.getScanState();

                    if (!mProcessing) { return; }

                    List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                    // do Semaphore for Connecting method
                    for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                        // only known manufacturer that will be used to connect
                        broadcastServiceInterface("Start service interface");
                        iGatewayService.doConnect(device.getAddress());

                        if (!mProcessing) { return; }

                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
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
