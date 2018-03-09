package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */

public class BLeScanOldCallback implements BluetoothAdapter.LeScanCallback {

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, BluetoothJsonDataProcess> mapProperties;

    public BLeScanOldCallback(List<BluetoothDevice> listDevices, Map<BluetoothDevice, BluetoothJsonDataProcess> mapProperties) {
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, BluetoothJsonDataProcess> getMapProperties(){return mapProperties;}


    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (!listDevices.contains(device)) {
            listDevices.add(device);
            BluetoothJsonDataProcess json = new BluetoothJsonDataProcess(device, rssi, scanRecord);
            mapProperties.put(device, json);
        }
    }
}
