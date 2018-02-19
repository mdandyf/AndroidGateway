package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.widget.Toast;

/**
 * Created by mdand on 2/19/2018.
 */

public class MyBluetoothGattCallback extends BluetoothConfigurationManager {

    protected MyBluetoothGattCallback() {}

    protected BluetoothGattCallback getGattCallback() {
        return new MyBluetoothCallback();
    }

    private class MyBluetoothCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (gatt == mBluetoothGatt) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, "Disconnected.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    finish();
                }
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            for (BluetoothGattService service : gatt.getServices()) {
            /*if (service.getUuid().equals(SERVICE_VIBROTACTILE)) {
                if(gatt.getDevice().getAddress().equals(MAC_ADDRESS_1)) {
                    containsVibrotactile = true;
                }
                if(gatt.getDevice().getAddress().equals(MAC_ADDRESS_2)) {
                    containsVibrotactile2 = true;
                }

            }*/

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

}
