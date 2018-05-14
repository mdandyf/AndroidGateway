package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.thread.ProcessPriority;

import java.util.List;

// implementation of Semaphore Scheduling Gateway Controller

public class Semaphore extends AsyncTask<Void, Void, Void> {
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

    private ProcessPriority processPowerMeasurement;

    public Semaphore(Context context, boolean mProcessing, IGatewayService iGatewayService, PowerEstimator mServicePE, boolean mBoundPE) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.mServicePE = mServicePE;
        this.mBoundPE = mBoundPE;
    }

    @Override
    protected void onCancelled(Void aVoid) {
        super.onCancelled(aVoid);
        mProcessing = false;
        if(mScanning) { try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null);iGatewayService.execScanningQueue();mScanning = false; } catch (RemoteException e) { e.printStackTrace(); }}
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        mProcessing = false;
        if(mScanning) { try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null);iGatewayService.execScanningQueue();mScanning = false; } catch (RemoteException e) { e.printStackTrace(); }}
        if (processPowerMeasurement != null) { processPowerMeasurement.interruptThread(); }
    }

    @Override
    protected Void doInBackground(Void... voids) {
        mProcessing = true;

        while (mProcessing) {
            broadcastUpdate("\n");
            broadcastUpdate("Start new cycle...");
            cycleCounter++;
            broadcastUpdate("Cycle number " + cycleCounter);
            try {
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = iGatewayService.getScanState();

                waitThread(SCAN_TIME);

                // do Normal Scanning Method
                broadcastUpdate("\n");
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = iGatewayService.getScanState();

                if (isCancelled()) { this.cancel(true); return null; }

                waitThread(100);
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                // do Semaphore for Connecting method
                for (final BluetoothDevice device : scanResults) {
                    processPowerMeasurement = new ProcessPriority(10);
                    processPowerMeasurement.newThread(doMeasurePower()).start();

                    iGatewayService.doConnect(device.getAddress());
                    processUserChoiceAlert(device.getAddress(), device.getName());
                    if (isCancelled()) { this.cancel(true); return null; }

                    processPowerMeasurement.interruptThread();
                    iGatewayService.updateDatabaseDevicePowerUsage(device.getAddress(), powerUsage);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
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

    private void processUserChoiceAlert(String macAddress, String deviceName) {
        try {
            String userChoice = iGatewayService.getDeviceUsrChoice(macAddress);
            if(deviceName == null) {deviceName = "Unknown";};
            if(userChoice == null || userChoice == "") broadcastAlertDialog("Start Service Interface of Device " + macAddress + "-" + deviceName, macAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
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

    private void broadcastAlertDialog(String message, String macAddress) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.USER_CHOICE_SERVICE);
            intent.putExtra("message", message);
            intent.putExtra("macAddress", macAddress);
            context.sendBroadcast(intent);
        }
    }
}
