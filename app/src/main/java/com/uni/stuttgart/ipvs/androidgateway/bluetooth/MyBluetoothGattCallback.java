package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

/**
 * Created by mdand on 2/19/2018.
 */

public class MyBluetoothGattCallback extends BluetoothGattCallback {
    private Bluetooth bluetooth;

    public MyBluetoothGattCallback(Bluetooth data) {
        this.bluetooth = data;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            gatt.discoverServices();
        }
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        for (BluetoothGattService service : gatt.getServices()) {

        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        parseCharacteristic(characteristic);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        parseCharacteristic(characteristic);
    }

    public void parseCharacteristic(BluetoothGattCharacteristic characteristic) {
        /*if(characteristic.getUuid().equals(SERVICE_BATTERY_READING)) {

            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            final String batteryString = String.format(" Battery: %d", flags);
            Log.d("Battery:", ""+ batteryString);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textViewConnected1.append(batteryString);
                }
            });


        }*/

    }
}
