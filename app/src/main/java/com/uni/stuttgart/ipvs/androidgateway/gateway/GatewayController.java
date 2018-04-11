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

    private GatewayService mGatewayService;
    private boolean mBound = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;

    private Runnable runnablePeriodic = null;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
            doScheduleRR();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mProcessing = false;
            mGatewayService.setProcessing(mProcessing);
        }
    };

    private void doScheduleRR() {
        ProcessPriority processPriority = new ProcessPriority(8);
         runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                mProcessing = true;
                while (mBound && mGatewayService != null && mProcessing) {
                    final String[] status = {mGatewayService.getCurrentStatus()};
                    mGatewayService.setProcessing(mProcessing);
                    doGatewayController(status[0]);
                    if(!mProcessing) {return;}
                }
            }
        };

         processPriority.newThread(runnablePeriodic).start();
    }


    // using scheduler for Round Robin Method
    private void doGatewayController(String status) {
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

                    waitThread(1000);

                    List<BluetoothDevice> scanResults = mGatewayService.getScanResults();
                    for (BluetoothDevice device : scanResults) {
                        mGatewayService.addQueueConnecting(device.getAddress(), device.getName(), 0, BluetoothLeDevice.CONNECTING, null);
                        mGatewayService.execConnectingQueue();
                    }
                    mScanning = false;
                }
                break;
            case "Connecting":
                // nothing to do
            case "Discovering":
                waitThread(SCAN_TIME);
                mGatewayService.addQueueConnecting(null, null, 0, BluetoothLeDevice.DISCONNECTED, mGatewayService.getCurrentGatt());
                mGatewayService.execConnectingQueue();
                break;
            case "Reading":
                waitThread(5000);
                mGatewayService.addQueueConnecting(null, null, 0, BluetoothLeDevice.DISCONNECTED, mGatewayService.getCurrentGatt());
                mGatewayService.execConnectingQueue();
                break;
        }
    }

    private void doScheduleFEP() {

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
