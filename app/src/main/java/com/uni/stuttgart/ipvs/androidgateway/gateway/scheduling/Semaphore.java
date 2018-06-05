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
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import java.util.List;
import java.util.Map;

// implementation of Semaphore Scheduling Gateway Controller

public class Semaphore {
    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

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
    }
    
    public void stop() {
        mProcessing = false;
        executionTask.terminateExecutorPools();
    }

    public void start() {
        mProcessing = true;
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N*2);
        executionTask.submitRunnableSingleThread(new SemaphoreSchedulling());
    }

    private class SemaphoreSchedulling implements Runnable {

        @Override
        public void run() {
            while (mProcessing) {
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                    iGatewayService.execScanningQueue();

                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();

                    if (!mProcessing) { return; }

                    waitThread(100);
                    List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                    // do Semaphore for Connecting method
                    for (final BluetoothDevice device : scanResults) {
                        // only known manufacturer that will be used to connect
                        boolean isMfgExist = processMfgChoice(device.getAddress());
                        if(isMfgExist) {
                            broadcastServiceInterface("Start service interface");
                            iGatewayService.doConnect(device.getAddress());
                        }

                        if (!mProcessing) { return; }

                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void waitThread(int time) {
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

    private boolean processMfgChoice(String macAddress) {
        boolean isMfgExist = false;
        try {
            isMfgExist = iGatewayService.isDeviceManufacturerKnown(macAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return isMfgExist;
    }

    private void broadcastServiceInterface(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
            intent.putExtra("message", message);
            context.sendBroadcast(intent);
        }
    }
}
