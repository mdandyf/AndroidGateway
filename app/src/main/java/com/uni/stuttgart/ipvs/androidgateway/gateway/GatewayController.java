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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {
    private static final int DURATION = 60 * 60 * 24; // set to 24 hours
    private static final int PROCESSING_TIME = 60000; // set one cycle to 60 seconds
    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private GatewayService mGatewayService;
    private boolean mBound = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;

    private Handler handlerPeriodic = null;
    private Thread threadPeriodic = null;
    private static Runnable runnablePeriodic = null;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        bindService(new Intent(this, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind to GatewayService...");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(runnablePeriodic != null) {
            handlerPeriodic.removeCallbacks(runnablePeriodic);
        }
        unbindService(mConnection);
        broadcastUpdate("Unbind to GatewayService...");
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
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            GatewayService.LocalBinder binder = (GatewayService.LocalBinder) service;
            mGatewayService = binder.getService();
            mBound = true;
            broadcastUpdate("GatewayService & GatewayController have bound...");
            broadcastUpdate("\n");
            startProcessing();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mProcessing = false;
            mGatewayService.setProcessing(mProcessing);
        }
    };

    private void startProcessing() {
        doScheduleRR();
    }

    private void doScheduleRR() {
        final ScheduledFuture<?> timerSchedule =
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        mProcessing = true;
                        while (mBound && mGatewayService != null) {
                            final String[] status = {mGatewayService.getCurrentStatus()};
                            mGatewayService.setProcessing(mProcessing);
                            doGatewayController(status[0]);
                        }
                    }
                }, 10, PROCESSING_TIME, MILLISECONDS);
        scheduler.schedule(new Runnable() {
            public void run() { timerSchedule.cancel(true); }
        }, 60, SECONDS);

    }

    /*private void doScheduleRR() {
        handlerPeriodic = new Handler();
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try {
                    if(threadPeriodic != null) {
                        mProcessing = false;
                        mGatewayService.setProcessing(mProcessing);
                        broadcastUpdate("Start new cycle...");
                        threadPeriodic.interrupt();
                        threadPeriodic = null;
                        handlerPeriodic.postDelayed(runnablePeriodic, 1000);
                        return;
                    } else {
                        mProcessing = true;
                        handlerPeriodic.postDelayed(runnablePeriodic, PROCESSING_TIME);
                    }

                    threadPeriodic = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (mBound && mGatewayService != null) {
                                final String[] status = {mGatewayService.getCurrentStatus()};
                                mGatewayService.setProcessing(mProcessing);
                                doGatewayController(status[0]);
                            }
                        }
                    });
                    threadPeriodic.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        handlerPeriodic.postDelayed(runnablePeriodic, 1000);
    }*/

    // using scheduler for Round Robin Method
    private void doGatewayController(String status) {

        switch (status) {
            case "Created":
                boolean deviceExist = bleDeviceDatabase.isDeviceExist();
                if (deviceExist) {
                    List<String> macAddresses = bleDeviceDatabase.getListDevices();
                    for (String mac : macAddresses) {
                        mGatewayService.addQueueScanning(mac, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                    }
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                } else {
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                }

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
