package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Created by mdand on 3/17/2018.
 */

public class BluetoothLeGattCallback extends BluetoothGattCallback {

    private static final String TAG = "Bluetooth Gatt Callback";

    private BluetoothGatt mBluetoothGatt;
    private int mBluetoothRssi;
    private BluetoothDevice mDevice;
    private Context context;
    private BluetoothGattCallback mGattCallback;
    private List<BluetoothGattService> mGattServices;
    private BluetoothGattCharacteristic mCharacteristic;

    public BluetoothLeGattCallback(Context context, BluetoothDevice device) {
        this.context = context;
        this.mDevice = device;
        this.mGattCallback = this;
    }

    public void connect() {
        mBluetoothGatt = mDevice.connectGatt(context, false, mGattCallback);
        refreshDeviceCache(mBluetoothGatt);
    }

    public void disconnect() {
        if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        mBluetoothGatt.close();
    }

    public BluetoothGatt getBluetoothGatt() {
        return this.mBluetoothGatt;
    }

    public int getBluetoothRssi() {
        return this.mBluetoothRssi;
    }

    public List<BluetoothGattService> getGattServices() {
        return this.mGattServices;
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        return mCharacteristic;
    }

    public void readCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            Log.d(TAG, "Characteristic " + characteristicUUID.toString() + " not found");
        } else {
            mBluetoothGatt.readCharacteristic(characteristic);
            Log.d(TAG, "Characteristic " + characteristic.getUuid().toString() + " read");
        }
    }

    public void writeDescriptorNotify(UUID serviceUUID, UUID characteristicUuid, UUID descriptorUUID) {
        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.d(TAG, "Characteristic " + characteristicUuid.toString() + " not found");
        } else {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
            if (descriptor != null) {
                try {
                    mBluetoothGatt.readDescriptor(descriptor);
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);
                    Log.d(TAG, "descriptor notify " + descriptorUUID.toString() + " has been written");
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void writeDescriptorIndication(UUID serviceUUID, UUID characteristicUuid, UUID descriptorUUID) {
        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.d(TAG, "Characteristic " + characteristicUuid.toString() + " not found");
        } else {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
            if (descriptor != null) {
                mBluetoothGatt.readDescriptor(descriptor);
                try {
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);
                    Log.d(TAG, "descriptor indicate " + descriptorUUID.toString() + " has been written");
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void refreshDeviceCache(BluetoothGatt gatt) {
        try {
            Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                localMethod.invoke(gatt, new Object[0]);
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "Connected to GATT server.");
            Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());
            gatt.readRemoteRssi();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected from GATT server.");
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        this.mBluetoothRssi = rssi;
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mGattServices = gatt.getServices();
        } else {
            Log.w(TAG, "onServiceDiscovered Receive: " + status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        mCharacteristic = characteristic;
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);

    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

    }

}
