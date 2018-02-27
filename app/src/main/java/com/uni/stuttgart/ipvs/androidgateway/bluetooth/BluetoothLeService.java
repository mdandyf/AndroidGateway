package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeService extends Service {

    private static final String TAG = "Bluetooth Service";

    public final static String ACTION_GATT_CONNECTED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_READ_RSSI =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_READ_RSSI";
    public final static String ACTION_DATA_AVAILABLE =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.EXTRA_DATA";
    public final static String ACTION_DESCRIPTOR_WRITE =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_DESCRIPTOR_WRITE";
    public final static String ACTION_DESCRIPTOR_READ =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_DESCRIPTOR_READ";


    private Intent mService;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private int mBluetoothRssi;
    private List<BluetoothGatt> listGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private Context context;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    /** Binder given to clients */
    private final IBinder mBinder = new LocalBinder();

    /** Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mService = intent;
        context = this;
        listGatt = new ArrayList<>();
        return mBinder;
    }

    public void connect() {
        mConnectionState = STATE_CONNECTING;
        Bundle bundle = mService.getBundleExtra("bluetoothDevice");
        mBluetoothDevice = (BluetoothDevice) bundle.getParcelable("bluetoothDevice");
        runThread();
    }

    public void connect(BluetoothDevice device) {
        mConnectionState = STATE_CONNECTING;
        Bundle bundle = mService.getBundleExtra("bluetoothDevice");
        mBluetoothDevice = device;
        runThread();
    }

    public void disconnect() {
        mConnectionState = STATE_DISCONNECTED;
        if(listGatt != null) {
            for(BluetoothGatt gatt : listGatt) {
                gatt.disconnect();
            }
        }
    }

    private void runThread() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt = mBluetoothDevice.connectGatt(context,false, mGattCallback);
                listGatt.add(mBluetoothGatt);
                refreshDeviceCache(mBluetoothGatt);
            }
        };
        AsyncTask.execute(runnable);
    }

    private void refreshDeviceCache(BluetoothGatt gatt) {
        try {
            Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
            if(localMethod != null) {
                boolean bool = ((boolean) localMethod.invoke(gatt, new Object[0]));
            }
        } catch(Exception e) {
            Log.w("Unhandled Exception", e);
        }
    }

    public final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BluetoothDevice device = gatt.getDevice();
                gatt.readRemoteRssi();
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());
                BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
                String json = bleJson.getJsonData().toString();
                broadcastUpdate(ACTION_GATT_CONNECTED, json);
                mConnectionState = STATE_CONNECTED;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BluetoothDevice device = gatt.getDevice();
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_GATT_DISCONNECTED, device.getAddress());
                mConnectionState = STATE_DISCONNECTED;
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            BluetoothDevice device = gatt.getDevice();
            mBluetoothRssi = rssi;
            BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
            String json = bleJson.getJsonData().toString();
            broadcastUpdate(ACTION_READ_RSSI, json);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothDevice device = gatt.getDevice();
                gatt.readRemoteRssi();
                BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
                String json = bleJson.getJsonData().toString();
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, json);
            } else {
                Log.w(TAG, "onServiceDiscovered Receive: " + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            BluetoothDevice device = gatt.getDevice();
            BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
            String json = bleJson.getJsonData().toString();
            broadcastUpdate(ACTION_DATA_AVAILABLE, json);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            BluetoothDevice device = gatt.getDevice();
            BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
            String json = bleJson.getJsonData().toString();
            broadcastUpdate(ACTION_DATA_AVAILABLE, json);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            BluetoothDevice device = gatt.getDevice();
            BluetoothJsonData bleJson = new BluetoothJsonData(device, gatt, mBluetoothRssi);
            String json = bleJson.getJsonData().toString();
            broadcastUpdate(EXTRA_DATA, json);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
/*
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DESCRIPTOR_READ);
            }
*/
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            //broadcastUpdate(ACTION_DESCRIPTOR_WRITE);

        }
    };

    protected void broadcastUpdate(final String action, String data) {
        final Intent intent = new Intent(action);
        intent.putExtra("bluetoothData", data);
        sendBroadcast(intent);
    }
}
