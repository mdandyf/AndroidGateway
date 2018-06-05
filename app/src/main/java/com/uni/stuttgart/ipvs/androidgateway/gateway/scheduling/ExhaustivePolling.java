package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// implementation of Scheduling using Exhaustive Polling
public class ExhaustivePolling {

    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

    private IGatewayService iGatewayService;

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private ExecutionTask<String> executionTask;

    public ExhaustivePolling(Context context, boolean mProcessing, IGatewayService iGatewayService) {
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
        executionTask.submitRunnableSingleThread(new RunEPScheduling());
    }

    private class RunEPScheduling implements Runnable {
        @Override
        public void run() {
            try {
                while (mProcessing) {
                    // do Exhaustive Polling Part
                    broadcastUpdate("\n");
                    broadcastUpdate("Start new cycle");
                    cycleCounter++;
                    broadcastUpdate("Cycle number " + cycleCounter);
                    boolean isDataExist = iGatewayService.checkDevice(null);
                    if (isDataExist) {
                        List<String> devices = iGatewayService.getListActiveDevices();
                        for (String device : devices) { iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null, 0); }
                        // do normal scanning only for half of normal scanning time
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME/2);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
                        iGatewayService.execScanningQueue();
                        mScanning = iGatewayService.getScanState();
                    } else {
                        // do normal scanning
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
                        iGatewayService.execScanningQueue();
                        mScanning = iGatewayService.getScanState();
                    }

                    if(!mProcessing) {return;}
                    List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                    // do Connecting by using Semaphore
                    for (final BluetoothDevice device : scanResults) {
                        // only known manufacturer that will be used to connect
                        boolean isMfgExist = processMfgChoice(device.getAddress());
                        if(isMfgExist) {
                            broadcastServiceInterface("Start service interface");
                            iGatewayService.doConnect(device.getAddress());
                        }
                        if (!mProcessing) { return; }
                    }

                }
            } catch (Exception e) {
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
