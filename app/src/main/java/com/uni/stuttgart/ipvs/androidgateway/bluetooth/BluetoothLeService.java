package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeService extends Service {

    private static final String TAG = "BLE Service";

    public final static String ACTION_GATT_CONNECTED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.uni-stuttgart.ipvs.androidgateway.bluetoothle.ACTION_GATT_SERVICES_DISCOVERED";
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
    private List<BluetoothGatt> listGatt;
    private int mConnectionState = STATE_DISCONNECTED;

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
        listGatt = new ArrayList<>();
        return mBinder;
    }

    public void connect() {
        mConnectionState = STATE_CONNECTING;
        mBluetoothDevice = mService.getExtras().getParcelable("bluetoothDevice");
        mBluetoothGatt = mBluetoothDevice.connectGatt(this, false, mGattCallback);
        listGatt.add(mBluetoothGatt);
    }

    public void disconnect() {
        mConnectionState = STATE_DISCONNECTED;
        for(BluetoothGatt gatt : listGatt) {
            gatt.disconnect();
        }
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(ACTION_GATT_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                mConnectionState = STATE_CONNECTED;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
                mConnectionState = STATE_DISCONNECTED;
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            broadcastUpdate(ACTION_DATA_AVAILABLE);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            broadcastUpdate(ACTION_DATA_AVAILABLE);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            broadcastUpdate(EXTRA_DATA);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DESCRIPTOR_READ);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            broadcastUpdate(ACTION_DESCRIPTOR_WRITE);

        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

    }

}
