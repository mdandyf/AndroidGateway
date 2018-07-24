package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm.WSM;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.EExecutionType;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// implementation of Scheduling using Exhaustive Polling
public class ExhaustivePollingWithWSM {

    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int SCAN_TIME_HALF = SCAN_TIME / 2; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

    private IGatewayService iGatewayService;

    private BluetoothDevice mDevice;
    private boolean mScanning;
    private boolean mConnecting;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;

    private ExecutionTask<String> executionTask;
    private ScheduledThreadPoolExecutor schedulerPower;
    private long powerUsage = 0;
    private PowerEstimator powerEstimator;

    public ExhaustivePollingWithWSM(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }

    public void stop() {
        mProcessing = false; mConnecting = false;
        executionTask.terminateExecutorPools();
        stopPowerMeasure();
        unregisterBroadcastListener();
    }

    public void start() {
        mProcessing = true; mConnecting = false;
        registerBroadcastListener();
        int N = Runtime.getRuntime().availableProcessors();

        powerEstimator = new PowerEstimator(context);
        executionTask = new ExecutionTask<>(N, N * 2);
        executionTask.setExecutionType(EExecutionType.SINGLE_THREAD_POOL);
        executionTask.submitRunnable(new RunEPScheduling());
    }

    private class RunEPScheduling implements Runnable {
        @Override
        public void run() {
            while (mProcessing) {
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
                        List<String> devices = iGatewayService.getListActiveDevices();
                        // search for known device listed in database
                        for (String device : devices) {
                            iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null, 0);
                        }
                        // do normal scanning only for half of normal scanning time
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME_HALF);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
                        iGatewayService.execScanningQueue();
                        mScanning = iGatewayService.getScanState();

                        connectWSM();
                    } else {
                        // do normal scanning
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.WAIT_THREAD, null, SCAN_TIME);
                        iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
                        iGatewayService.execScanningQueue();
                        mScanning = iGatewayService.getScanState();

                        connectSemaphore();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * =================================================================================================================================
     * Type of connections
     * =================================================================================================================================
     */

    private void connectSemaphore() {
        try {
            List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

            // do Connecting by using Semaphore
            for (final BluetoothDevice device : scanResults) {
                mConnecting = true;
                mDevice = device;
                broadcastServiceInterface("Start service interface");
                iGatewayService.updateDatabaseDeviceState(device, "inactive");

                powerUsage = 0;
                powerEstimator.start();
                schedulerPower = executionTask.scheduleWithThreadPoolExecutor(doMeasurePower(), 0, 100, MILLISECONDS);

                iGatewayService.doConnect(mDevice.getAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectWSM() {
        try {
            List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
            Map<BluetoothDevice, Double> mapRankedDevices;
            if(scanResults.size() != 0) {
                broadcastUpdate("Start ranking device with WSM algorithm...");
                mapRankedDevices = doRankDeviceWSM(scanResults);
                broadcastUpdate("Finish ranking device...");
            } else {
                broadcastUpdate("No nearby device(s) available...");
                return;
            }

            // do Connecting by using Semaphore
            for (Map.Entry entry : mapRankedDevices.entrySet()) {
                BluetoothDevice device = (BluetoothDevice)  entry.getKey();

                mConnecting = true;
                mDevice = device;
                broadcastServiceInterface("Start service interface");
                iGatewayService.updateDatabaseDeviceState(device, "inactive");

                powerUsage = 0;
                powerEstimator.start();
                schedulerPower = executionTask.scheduleWithThreadPoolExecutor(doMeasurePower(), 0, 100, MILLISECONDS);

                iGatewayService.doConnect(mDevice.getAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // implementation of Ranking Devices based on AHP
    private Map<BluetoothDevice, Double> doRankDeviceWSM(List<BluetoothDevice> devices) {
        Map<BluetoothDevice, Double> result = new ConcurrentHashMap<>();
        try {
            WSM wsm = new WSM(devices, iGatewayService, powerEstimator.getBatteryRemainingPercent());
            broadcastUpdate("Sorting devices by their priorities...");
            result = wsm.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * =================================================================================================================================
     * Broadcast Listener
     * =================================================================================================================================
     */

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(GatewayService.DISCONNECT_COMMAND)) {
                if(mConnecting) { stopPowerMeasure(); updateDeviceDatabasePowerMeasure(); mConnecting = false;}
            } else if (action.equals(GatewayService.FINISH_READ)) {
                if(mConnecting) { stopPowerMeasure(); updateDeviceDatabasePowerMeasure(); mConnecting = false;}
            }
        }

    };

    private boolean registerBroadcastListener() {
        context.registerReceiver(mReceiver, new IntentFilter(GatewayService.DISCONNECT_COMMAND));
        context.registerReceiver(mReceiver, new IntentFilter(GatewayService.FINISH_READ));
        return true;
    }

    private void unregisterBroadcastListener() {
        context.unregisterReceiver(mReceiver);
    }

    /**
     * =================================================================================================================================
     * Device Power Usage Measurement routines
     * =================================================================================================================================
     */

    private Runnable doMeasurePower() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    long currentNow = powerEstimator.getCurrentNow();
                    if (currentNow < 0) { currentNow = currentNow * -1; }
                    powerUsage = powerUsage + (currentNow * new Long(powerEstimator.getVoltageNow()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void stopPowerMeasure() {
        powerEstimator.stop();
        schedulerPower.shutdownNow();
    }

    private void updateDeviceDatabasePowerMeasure() {
        try {
            iGatewayService.updateDatabaseDevicePowerUsage(mDevice.getAddress(), powerUsage);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * =================================================================================================================================
     * Other routines
     * =================================================================================================================================
     */

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
