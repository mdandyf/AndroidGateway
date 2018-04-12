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

    private GatewayService mGatewayService;
    private boolean mBound = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;

    private Runnable runnablePeriodic = null;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        broadcastUpdate("Start new program cycle...");
        bindService(new Intent(this, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to GatewayService...");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
        broadcastUpdate("Unbind GatewayController to GatewayService...");
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
            broadcastUpdate("GatewayController & GatewayService have bound...");
            broadcastUpdate("\n");
            //doScheduleNormal();
            //doScheduleRR();
            doScheduleFEP();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mProcessing = false;
            mGatewayService.setProcessing(mProcessing);
        }
    };

    // not using any scheduling method (based on waiting for callback connection) (Normal Scheduling)
    private void doScheduleNormal() {
        broadcastUpdate("Start Normal Scheduling...");
        ProcessPriority processPriority = new ProcessPriority(8);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mProcessing = true;
                while (mBound && mGatewayService != null && mProcessing) {
                    final String[] status = {mGatewayService.getCurrentStatus()};
                    mGatewayService.setProcessing(mProcessing);
                    if (status[0] != null) {
                        doGatewayControllerNormal(status[0]);
                    }
                    if (!mProcessing) {
                        return;
                    }
                }
            }
        };

        processPriority.newThread(runnablePeriodic).start();
    }

    // implementation of Normal Scheduling Gateway Controller
    private void doGatewayControllerNormal(String status) {
        switch (status) {
            case "Created":
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                break;
            case "Scanning":
                if (mScanning) {
                    waitThread(SCAN_TIME);
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    mGatewayService.execScanningQueue();
                    mScanning = false;

                    waitThread(100);
                    List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

                    for (final BluetoothDevice device : scanResults) {
                        mGatewayService.doConnecting(device.getAddress());
                    }
                }
                break;
        }
    }

    // scheduling using Round Robin Method
    private void doScheduleRR() {
        broadcastUpdate("Start Round Robin Scheduling...");
        ProcessPriority processPriority = new ProcessPriority(8);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mProcessing = true;
                while (mBound && mGatewayService != null && mProcessing) {
                    final String[] status = {mGatewayService.getCurrentStatus()};
                    mGatewayService.setProcessing(mProcessing);
                    if (status[0] != null) {
                        doGatewayControllerRR(status[0]);
                    }
                    if (!mProcessing) {
                        return;
                    }
                }
            }
        };

        processPriority.newThread(runnablePeriodic).start();
    }


    // Implementation of Round Robin Scheduling Gateway Controller
    private void doGatewayControllerRR(String status) {
        switch (status) {
            case "Created":
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                break;
            case "Scanning":
                if (mScanning) {
                    waitThread(SCAN_TIME);
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

                    for (final BluetoothDevice device : scanResults) {
                        Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                        ProcessPriority processPriority = new ProcessPriority(10);
                        processPriority.newThread(runnable).start();
                        // set timer to xx seconds
                        waitThread(maxConnectTime);
                        broadcastUpdate("Wait time finished, disconnected...");
                        mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                        waitThread(100);
                        processPriority.interruptThread();
                    }
                }
                break;
        }
    }

    // scheduling using Fair Exhaustive Polling (FEP)
    private void doScheduleFEP() {
        broadcastUpdate("Start Fair Exhaustive Polling Scheduling...");
        ProcessPriority processPriority = new ProcessPriority(8);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mProcessing = true;
                while (mBound && mGatewayService != null && mProcessing) {
                    final String[] status = {mGatewayService.getCurrentStatus()};
                    mGatewayService.setProcessing(mProcessing);
                    if (status[0] != null) {
                        doGatewayControllerFEP(status[0]);
                    }
                    if (!mProcessing) {
                        return;
                    }
                }
            }
        };

        processPriority.newThread(runnablePeriodic).start();
    }

    // implementation Fair Exhaustive Polling (FEP) Scheduling Gateway Controller
    private void doGatewayControllerFEP(String status) {
        boolean isDataExist = false;
        switch (status) {
            case "Created":
                isDataExist = bleDeviceDatabase.isDeviceExist();
                if (isDataExist) {
                    List<String> devices = bleDeviceDatabase.getListDevices();
                    for (String device : devices) {
                        mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                    }
                    mGatewayService.execScanningQueue();
                    mScanning = false;
                    // do normal scanning only for half of normal scanning time
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                    mScanning = true;
                } else {
                    // do normal scanning
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                    mScanning = true;
                }
                break;
            case "Scanning":
                if (mScanning) {
                    if (isDataExist) {
                        waitThread(SCAN_TIME / 2);
                    } else {
                        waitThread(SCAN_TIME);
                    }
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    mGatewayService.execScanningQueue();
                    mScanning = false;
                    waitThread(1000);

                    List<BluetoothDevice> scanResults = mGatewayService.getScanResults();
                    // calculate timer for connection (to obtain Round Robin Scheduling)
                    if (scanResults.size() != 0) {
                        int remainingTime = PROCESSING_TIME - SCAN_TIME;
                        maxConnectTime = remainingTime / scanResults.size();
                        broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
                    }

                    // connect to only known active devices
                    if (isDataExist) {
                        List<String> devices = bleDeviceDatabase.getListActiveDevices();
                        for (final BluetoothDevice device : scanResults) {
                            if (devices.contains(device)) {
                                Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                                ProcessPriority processPriority = new ProcessPriority(10);
                                processPriority.newThread(runnable).start();
                                // set timer to xx seconds
                                waitThread(maxConnectTime);
                                broadcastUpdate("Wait time finished, disconnected...");
                                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                                waitThread(100);
                                processPriority.interruptThread();
                            }
                        }
                    } else {
                        for (final BluetoothDevice device : scanResults) {
                            Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                            ProcessPriority processPriority = new ProcessPriority(10);
                            processPriority.newThread(runnable).start();
                            // set timer to xx seconds
                            waitThread(maxConnectTime);
                            broadcastUpdate("Wait time finished, disconnected...");
                            mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                            waitThread(100);
                            processPriority.interruptThread();
                        }
                    }


                }
                break;
            case "Connecting":
                // nothing to do
                waitThread(maxConnectTime);
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                break;
            case "Connected":
                waitThread(5000);
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                break;
            case "Reading":
                waitThread(5000);
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                break;
        }
    }

    private void doSmartGatewayController(String status) {

    }

    private void waitThread(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcastUpdate(String message) {
        final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }

}
