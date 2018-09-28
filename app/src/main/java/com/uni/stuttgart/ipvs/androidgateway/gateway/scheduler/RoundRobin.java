package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Implementation of Round Robin Scheduling Gateway Controller
public class RoundRobin implements IGatewayScheduler {

    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_HALF;
    private int PROCESSING_TIME; // set processing time to 60 seconds

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;
    private IGatewayService iGatewayService;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;

    private ExecutionTask<Void> executionTask;

    public RoundRobin(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask) {
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
        scheduler.shutdownNow();
    }

    public void start() {
        scheduler = executionTask.scheduleWithThreadPoolExecutor(new RRStartScanning(), 0, PROCESSING_TIME + 10, MILLISECONDS);
        future = executionTask.getFuture();
    }

    private class RRStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    cycleCounter++;
                    iGatewayService.setCycleCounter(cycleCounter);
                    if (cycleCounter > 1) {
                        broadcastClrScrn();
                    }
                    broadcastUpdate("\n");
                    broadcastUpdate("Start new cycle...");
                    broadcastUpdate("Cycle number " + cycleCounter);

                    iGatewayService.startScan(SCAN_TIME);
                    iGatewayService.stopScanning();
                    mScanning = iGatewayService.getScanState();
                    waitThread(100);

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }

                    connect();

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

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            try {
                scanResults = iGatewayService.getScanResults();
                iGatewayService.setScanResultNonVolatile(scanResults);

                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
                } else {
                    return;
                }

                if (!mProcessing) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    return;
                }

                // do connecting by Round Robin
                for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                    iGatewayService.broadcastServiceInterface("Start service interface");
                    PBluetoothGatt parcelBluetoothGatt = iGatewayService.doConnecting(device.getAddress());

                    // set timer to xx seconds
                    waitThread(maxConnectTime);

                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);

                    iGatewayService.doDisconnected(parcelBluetoothGatt, "GatewayController");

                    if (!mProcessing) {
                        future.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
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
