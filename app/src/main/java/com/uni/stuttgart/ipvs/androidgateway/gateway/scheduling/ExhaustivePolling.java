package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

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
import java.util.concurrent.ScheduledThreadPoolExecutor;

// implementation of Scheduling using Exhaustive Polling
public class ExhaustivePolling {

    private IGatewayService iGatewayService;

    private int SCAN_TIME; // set scanning and reading time to 10 seoonds
    private int SCAN_TIME_OTHER; // set scanning and reading time half of original scan time
    private int PROCESSING_TIME; // set processing time to 60 seconds

    private boolean mScanning;
    private Context context;
    private boolean mProcessing;

    private int cycleCounter = 0;
    private ExecutionTask<Void> executionTask;
    private Future<?> futureEP;


    public ExhaustivePolling(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.executionTask = executionTask;

        try {
            this.SCAN_TIME = iGatewayService.getTimeSettings("ScanningTime");
            this.SCAN_TIME_OTHER = iGatewayService.getTimeSettings("ScanningTime2");
            this.PROCESSING_TIME = iGatewayService.getTimeSettings("ProcessingTime");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        mProcessing = false;
        futureEP.cancel(true);
    }

    public void start() {
        mProcessing = true;
        futureEP = executionTask.submitRunnable(new RunEPScheduling());
    }

    private class RunEPScheduling implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && mProcessing) {
                // do Exhaustive Polling Part
                cycleCounter++;
                if (cycleCounter > 1) {
                    broadcastClrScrn();
                }
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                broadcastUpdate("Cycle number " + cycleCounter);

                try {
                    iGatewayService.setCycleCounter(cycleCounter);
                    boolean isDataExist = iGatewayService.checkDevice(null);
                    if (isDataExist) {
                        // Devices are listed in DB
                        List<String> devices = iGatewayService.getListActiveDevices();
                        // search for known device listed in database
                        for (String device : devices) {
                            iGatewayService.startScanKnownDevices(device);
                        }

                        // do normal scanning only for half of normal scanning time
                        iGatewayService.startScan(SCAN_TIME_OTHER);
                        iGatewayService.stopScan();
                        mScanning = iGatewayService.getScanState();
                    } else {
                        // do normal scanning

                        iGatewayService.startScan(SCAN_TIME);
                        iGatewayService.stopScan();
                        mScanning = iGatewayService.getScanState();
                    }

                    List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                    iGatewayService.setScanResultNonVolatile(scanResults);

                    if (!mProcessing) {
                        futureEP.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }

                    // do Connecting by using Semaphore
                    for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                        iGatewayService.broadcastServiceInterface("Start service interface");
                        iGatewayService.doConnect(device.getAddress());

                        if (!mProcessing) {
                            futureEP.cancel(true);
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (!mProcessing) {
                        futureEP.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Log.d("Thread", "Thread EP " + Thread.currentThread().getId() + " is interrupted");
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
