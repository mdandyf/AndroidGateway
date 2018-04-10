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
import android.support.annotation.Nullable;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;

import java.util.List;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {

    private static final int PROCESSING_TIME = 60000;
    private static final int SCAN_TIME = 10000;

    private GatewayService mGatewayService;
    private boolean mBound = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;

    private int counter;

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
        unbindService(mConnection);
        broadcastUpdate("Unbind to GatewayService...");
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {return null;}

    /**
     * Gateway Controller Section
     */

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            GatewayService.LocalBinder binder = (GatewayService.LocalBinder) service;
            mGatewayService = binder.getService();
            mBound = true;
            broadcastUpdate("GatewayService & GatewayController have bound...");
            start();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mProcessing = false;
            mGatewayService.setProcessing(mProcessing);
        }
    };

    private void start() {
        counter = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mProcessing = true;
                counter++;
                while(mBound && mGatewayService != null && mProcessing) {
                    final String[] status = {mGatewayService.getCurrentStatus()};

                    if(counter == 1) {
                        mGatewayService.setProcessing(mProcessing);
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mProcessing = false;
                                mGatewayService.setProcessing(mProcessing);
                                start();
                            }
                        }, PROCESSING_TIME);
                    }

                    doGatewayController(status[0]);
                }
            }
        }).start();
    }

    private void doGatewayController(String status) {

        switch (status) {
            case "Created":
                boolean deviceExist = bleDeviceDatabase.isDeviceExist();
                if(deviceExist) {
                    List<String> macAddresses = bleDeviceDatabase.getListDevices();
                    for(String mac : macAddresses) {
                        mGatewayService.addQueueScanning(mac, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                    }
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                } else {
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                }

                mScanning = true;
            case "Scanning":
                if(mScanning) {
                    waitThread(SCAN_TIME);
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    mGatewayService.execScanningQueue();

                    waitThread(1000);
                    List<BluetoothDevice> scanResults = mGatewayService.getScanResults();
                    for(BluetoothDevice device : scanResults) {
                        mGatewayService.addQueueConnecting(device.getAddress(), device.getName(), 0, BluetoothLeDevice.CONNECTING, null);
                    }
                    mGatewayService.execConnectingQueue();
                    mScanning = false;
                }
            case "Connecting":
                // nothing to do
            case "Discovering":
                while(mGatewayService.getCurrentStatus() == "Discovering") {
                    waitThread(SCAN_TIME);
                    BluetoothGatt gatt = mGatewayService.getCurrentGatt();
                    gatt.disconnect();
                    break;
                }
            case "Reading":
                while(mGatewayService.getCurrentStatus() == "Reading") {
                    waitThread(5000);
                    BluetoothGatt gatt = mGatewayService.getCurrentGatt();
                    gatt.disconnect();
                    break;
                }
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
        final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }

}
