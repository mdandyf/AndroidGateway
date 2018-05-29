package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayController;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.thread.ProcessPriority;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Implementation of Round Robin Scheduling Gateway Controller
public class RoundRobin implements Runnable {

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

    private ProcessPriority process;
    private ProcessPriority processConnecting;
    private ProcessPriority processPowerMeasurement;

    public RoundRobin(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }

    public void cancelled() {
        mProcessing = false;
        future.cancel(true);
        scheduler.shutdownNow();
        scheduler = null;
        if(mScanning) { try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null);iGatewayService.execScanningQueue();mScanning = false; } catch (RemoteException e) { e.printStackTrace(); }}
        if (processConnecting != null) { processConnecting.interruptThread(); }
        if (processPowerMeasurement != null) { processPowerMeasurement.interruptThread(); }
    }

    @Override
    public void run() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        future = scheduler.scheduleAtFixedRate(new RRStartScanning(), 0, PROCESSING_TIME + 10, MILLISECONDS);
    }

    private class RRStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = iGatewayService.getScanState();
                waitThread(SCAN_TIME);
                stop();
                waitThread(100);

                if(!mProcessing) {future.cancel(false);return;}
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

            if(!mProcessing) {future.cancel(false);return;}
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
                } else {return;}

                if(!mProcessing) {future.cancel(false);return;}

                // do connecting by Round Robin
                for (final BluetoothDevice device : scanResults) {
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();
                    processUserChoiceAlert(device.getAddress(), device.getName());
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if(!mProcessing) {future.cancel(false);return;}
                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);
                    processConnecting.interruptThread();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private Runnable doMeasurePower() {
        return new Runnable() {
            @Override
            public void run() {
                if(!mBoundPE) {
                    broadcastUpdate("Power Executor Calculation Failed...");
                    return;
                }
                boolean process = true;
                while (process) {
                    int voltage = mServicePE.getVoltageNow();
                    long current = mServicePE.getCurrentNow();

                    if(voltage <= 0) {
                        voltage = voltage * -1;
                    }

                    if(current <= 0) {
                        current = current * -1;
                    }

                    powerUsage = powerUsage + (voltage * current);
                    process = false;
                }
            }
        };
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
