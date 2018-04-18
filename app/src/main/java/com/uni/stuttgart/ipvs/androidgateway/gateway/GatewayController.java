package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.thread.ProcessPriority;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {
    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds
    private int maxConnectTime = 0;

    private ScheduledThreadPoolExecutor scheduler;

    private Intent mIntent;
    private GatewayService mGatewayService;
    private boolean mBound = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;
    private ProcessPriority process;
    private ProcessPriority processConnecting;

    private Runnable runnablePeriodic = null;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mIntent = intent;
        bindService(new Intent(this, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to GatewayService...");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (processConnecting != null) {
            processConnecting.interruptThread();
        }
        if (process != null) {
            process.interruptThread();
        }
        unbindService(mConnection);
        broadcastUpdate("Unbind GatewayController to GatewayService...");
        mProcessing = false;
        stopService(mIntent);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Gateway Controller Section
     */

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            GatewayService.LocalBinder binder = (GatewayService.LocalBinder) service;
            mGatewayService = binder.getService();
            mBound = true;
            mProcessing = true;
            broadcastUpdate("GatewayController & GatewayService have bound...");
            broadcastUpdate("\n");
            doScheduleSemaphore();
            //doScheduleRR();
            //doScheduleEP();
            //doScheduleFEP();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mGatewayService.setProcessing(mProcessing);
        }
    };

    // Scheduling based on waiting for callback connection (Semaphore Scheduling)
    private void doScheduleSemaphore() {
        broadcastUpdate("Start Semaphore Scheduling...");
        process = new ProcessPriority(10);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerSemaphore();
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of Semaphore Scheduling Gateway Controller
    private void doGatewayControllerSemaphore() {

        mProcessing = true;

        while (mProcessing) {
            broadcastUpdate("\n");
            broadcastUpdate("Start new Cycle");
            mGatewayService.disconnectGatt();
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = true;

            waitThread(SCAN_TIME);

            // do Normal Scanning Method
            broadcastUpdate("\n");
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = false;

            waitThread(100);
            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // do Semaphore for Connecting method
            for (final BluetoothDevice device : scanResults) {
                mGatewayService.doConnect(device.getAddress());
                if (!mProcessing) {
                    return;
                }
            }

        }
    }

    // scheduling using Round Robin Method
    private void doScheduleRR() {
        broadcastUpdate("Start Round Robin Scheduling...");
        process = new ProcessPriority(10);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerRR();
            }
        };

        process.newThread(runnablePeriodic).start();
    }


    // Implementation of Round Robin Scheduling Gateway Controller
    private void doGatewayControllerRR() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        scheduler.scheduleAtFixedRate(new RRStartScanning(), 0, PROCESSING_TIME + 10, MILLISECONDS);
        scheduler.scheduleAtFixedRate(new RRStopScanning(), SCAN_TIME, PROCESSING_TIME + SCAN_TIME, TimeUnit.MILLISECONDS);
    }

    private class RRStartScanning implements Runnable {
        @Override
        public void run() {
            broadcastUpdate("\n");
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = true;
        }
    }

    private class RRStopScanning implements Runnable {
        @Override
        public void run() {
            // do normal scanning
            broadcastUpdate("\n");
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = false;

            waitThread(100);
            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // calculate timer for connection (to obtain Round Robin Scheduling)
            if (scanResults.size() != 0) {
                int remainingTime = PROCESSING_TIME - SCAN_TIME;
                maxConnectTime = remainingTime / scanResults.size();
                broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
            }

            // do connecting by Round Robin
            for (final BluetoothDevice device : scanResults) {
                Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                processConnecting = new ProcessPriority(10);
                processConnecting.newThread(runnable).start();
                // set timer to xx seconds
                waitThread(maxConnectTime);
                if (!mProcessing) {
                    return;
                }
                broadcastUpdate("Wait time finished, disconnected...");
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                waitThread(100);
                processConnecting.interruptThread();
            }
        }
    }

    //scheduling using Exhaustive Polling (EP)
    private void doScheduleEP() {
        broadcastUpdate("Start Fair Exhaustive Polling Scheduling...");
        process = new ProcessPriority(10);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerEP();
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of Scheduling using Exhaustive Polling
    private void doGatewayControllerEP() {
        mProcessing = true;

        while (mProcessing) {
            // do Exhaustive Polling Part
            broadcastUpdate("\n");
            mGatewayService.disconnectGatt();
            boolean isDataExist = bleDeviceDatabase.isDeviceExist();
            if (isDataExist) {
                List<String> devices = bleDeviceDatabase.getListActiveDevices();
                for (String device : devices) {
                    mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                }
                mGatewayService.execScanningQueue();
                mScanning = false;
                waitThread(1000);
                // do normal scanning only for half of normal scanning time
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME - 5000);
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            } else {
                // do normal scanning
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME);
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }

            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // do Connecting by using Semaphore
            for (final BluetoothDevice device : scanResults) {
                mGatewayService.doConnect(device.getAddress());
                if (!mProcessing) {
                    return;
                }
            }

            if(!mProcessing) {return;}

        }
    }

    // scheduling using Fair Exhaustive Polling (FEP)
    private void doScheduleFEP() {
        broadcastUpdate("Start Fair Exhaustive Polling Scheduling...");
        process = new ProcessPriority(10);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerFEP();
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation Fair Exhaustive Polling (FEP) Scheduling Gateway Controller
    private void doGatewayControllerFEP() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        scheduler.scheduleAtFixedRate(new FEPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        scheduler.scheduleAtFixedRate(new FEPStopScanning(), SCAN_TIME, PROCESSING_TIME + SCAN_TIME, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(new FEPDeviceDbRefresh(), 10 * PROCESSING_TIME, 10 * PROCESSING_TIME, MILLISECONDS); // refresh db state after 10 minutes
    }

    private class FEPStartScanning implements Runnable {
        @Override
        public void run() {
            broadcastUpdate("\n");
            // do polling slaves part
            boolean isDataExist = bleDeviceDatabase.isDeviceExist();
            if (isDataExist) {
                List<String> devices = bleDeviceDatabase.getListActiveDevices();
                for (String device : devices) {
                    mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                }
                mGatewayService.execScanningQueue();
                mScanning = false;
                waitThread(1000);
                // do normal scanning only for half of normal scanning time
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(5000); // if timer fails, force to stop
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            } else {
                // do normal scanning
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
            }
        }
    }

    private class FEPStopScanning implements Runnable {
        @Override
        public void run() {
            broadcastUpdate("\n");
            if(mScanning) {
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }

            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();
            List<String> devices = bleDeviceDatabase.getListActiveDevices();

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : scanResults) {
                bleDeviceDatabase.updateDeviceState(device.getAddress(), "inactive");
                if (devices.contains(device.getAddress())) {
                    Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(runnable).start();
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        return;
                    }
                    broadcastUpdate("Wait time finished, disconnected...");
                    mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);
                    processConnecting.interruptThread();
                }
            }
        }
    }

    private class FEPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            broadcastUpdate("\n");
            broadcastUpdate("Refresh all device states...");
            bleDeviceDatabase.updateAllDevicesState(null, "active");
        }
    }

    private void doSmartGatewayController() {

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
            sendBroadcast(intent);
        }
    }

}
