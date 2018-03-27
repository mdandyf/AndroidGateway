package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by mdand on 3/17/2018.
 */

public class BluetoothLeGattCallback extends BluetoothGattCallback {

    private static final String TAG = "BluetoothGatt Callback";

    private BluetoothGatt mBluetoothGatt;
    private int mBluetoothRssi;
    private String macAddress;
    private BluetoothDevice mDevice;
    private Context context;
    private BluetoothGattCallback mGattCallback;
    private Handler mHandlerMessage;

    public BluetoothLeGattCallback(Context context) {this.context = context;}

    public BluetoothLeGattCallback(Context context, BluetoothDevice device) {
        this.context = context;
        this.mDevice = device;
        this.mGattCallback = this;
        this.mBluetoothGatt = null;
    }

    public BluetoothLeGattCallback(BluetoothGatt gatt) {
        this.mBluetoothGatt = gatt;
    }

    public void connect() {
        mBluetoothGatt = mDevice.connectGatt(context, false, mGattCallback);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 0, 0, mBluetoothGatt));
        refreshDeviceCache(mBluetoothGatt);
    }

    public void connect(BluetoothAdapter mBluetoothAdapter, String macAddress) {
        mDevice = mBluetoothAdapter.getRemoteDevice(macAddress);
        mBluetoothGatt = mDevice.connectGatt(context, false, mGattCallback);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 0, 0, mBluetoothGatt));
        refreshDeviceCache(mBluetoothGatt);
    }

    public void disconnect() {
        if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
    }

    public BluetoothGatt getBluetoothGatt() {
        return this.mBluetoothGatt;
    }

    public void setHandlerMessage(Handler handler) {this.mHandlerMessage = handler;}

    public int getBluetoothRssi() {
        return this.mBluetoothRssi;
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

    public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] data) {
        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            Log.d(TAG, "Characteristic " + characteristicUUID.toString() + " not found");
        } else {
            mBluetoothGatt.writeCharacteristic(characteristic);
            Log.d(TAG, "Characteristic " + characteristic.getUuid().toString() + " has been written");
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
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
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
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
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
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 1, 0, mBluetoothGatt));
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected from GATT server.");
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 2, 0, mBluetoothGatt));
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        this.mBluetoothRssi = rssi;
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 3, 0, mBluetoothRssi));
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 4, 0, gatt));
        } else {
            Log.w(TAG, "onServiceDiscovered Receive: " + status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 5, 0, gatt));
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 6, 0, gatt));
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 7, 0, gatt));
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 8, 0, descriptor));
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 9, 0, descriptor));
    }

}
