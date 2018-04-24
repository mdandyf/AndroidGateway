package com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */

public class ScanCallbackOld implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "Bluetooth ScanCallback";
    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, GattDataJson> mapProperties;
    private Handler mHandlerMessage;
    private String macAddress;

    public ScanCallbackOld(List<BluetoothDevice> listDevices, Map<BluetoothDevice, GattDataJson> mapProperties) {
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, GattDataJson> getMapProperties(){return mapProperties;}
    public void setHandlerMessage(Handler mHandlerMessage) {this.mHandlerMessage = mHandlerMessage;}
    public void setFilter(String macAddress) {this.macAddress = macAddress;}


    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.d(TAG, device.getAddress());
        Log.d(TAG, Arrays.toString(scanRecord));

        if (!listDevices.contains(device)) {
            listDevices.add(device);
            GattDataJson json = new GattDataJson(device, rssi, scanRecord);
            mapProperties.put(device, json);
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 5, 0, mapProperties));
        }

        if(macAddress != null && device.getAddress().equals(macAddress)) {
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 7, 0, device));
        }
    }
}
