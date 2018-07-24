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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Implementation of Round Robin Scheduling Gateway Controller
public class RoundRobin {

    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;
    private IGatewayService iGatewayService;
    private PowerEstimator mServicePE;

    private boolean mBoundPE;
    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;
    private long powerUsage = 0;

    private ThreadTrackingPriority process;
    private ThreadTrackingPriority processConnecting;
    private ThreadTrackingPriority processPowerMeasurement;

    private ExecutionTask<String> executionTask;

    public RoundRobin(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }

    public void stop() {
        mProcessing = false;
        future.cancel(true);
        scheduler.shutdownNow();
    }

    public void start() {
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N * 2);
        scheduler = executionTask.scheduleWithThreadPoolExecutor(new RRStartScanning(), 0, PROCESSING_TIME + 10, MILLISECONDS);
        future = executionTask.getFuture();
    }

    private class RRStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                cycleCounter++;
                if (cycleCounter > 1) {
                    broadcastClrScrn();
                }
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                broadcastUpdate("Cycle number " + cycleCounter);
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null, 0);
                iGatewayService.execScanningQueue();
                mScanning = iGatewayService.getScanState();
                waitThread(100);

                if (!mProcessing) {
                    future.cancel(false);
                    return;
                }
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            try {
                scanResults = iGatewayService.getScanResults();

                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
                } else {
                    return;
                }

                if (!mProcessing) {
                    future.cancel(false);
                    return;
                }

                // do connecting by Round Robin
                for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                    broadcastServiceInterface("Start service interface");
                    processConnecting = new ThreadTrackingPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        future.cancel(false);
                        return;
                    }
                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);
                    processConnecting.interruptThread();

            }
        } catch(RemoteException e)

        {
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

    private void broadcastServiceInterface(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
            intent.putExtra("message", message);
            context.sendBroadcast(intent);
        }
    }

}
