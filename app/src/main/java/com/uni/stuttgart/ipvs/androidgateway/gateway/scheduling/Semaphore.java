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
import java.util.concurrent.Future;

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

    private ExecutionTask<Void> executionTask;
    private Future<Void> future;

    public Semaphore(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask) {
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
    
    public void stop() {
        mProcessing = false;
        future.cancel(true);
    }

    public void start() {
        executionTask.setExecutionType(EExecutionType.SINGLE_THREAD_POOL);
        future = executionTask.submitRunnable(new SemaphoreSchedulling());
    }

    private class SemaphoreSchedulling implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && mProcessing) {
                cycleCounter++;
                if(cycleCounter > 1) {broadcastClrScrn();}
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                broadcastUpdate("Cycle number " + cycleCounter);

                try {
                    iGatewayService.setCycleCounter(cycleCounter);

                    iGatewayService.startScan(SCAN_TIME);
                    iGatewayService.stopScanning();

                    mScanning = iGatewayService.getScanState();

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }

                    List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                    iGatewayService.setScanResultNonVolatile(scanResults);

                    // do Semaphore for Connecting method
                    for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                        // only known manufacturer that will be used to connect
                        iGatewayService.broadcastServiceInterface("Start service interface");
                        iGatewayService.doConnect(device.getAddress());

                        if (!mProcessing) {
                            future.cancel(true);
                            Thread.currentThread().interrupt();
                            return;
                        }

                    }

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
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
}
