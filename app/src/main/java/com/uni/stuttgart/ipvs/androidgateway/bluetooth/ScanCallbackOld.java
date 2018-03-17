package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */

public class ScanCallbackOld implements BluetoothAdapter.LeScanCallback {

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, GattDataJson> mapProperties;

    public ScanCallbackOld(List<BluetoothDevice> listDevices, Map<BluetoothDevice, GattDataJson> mapProperties) {
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, GattDataJson> getMapProperties(){return mapProperties;}


    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (!listDevices.contains(device)) {
            listDevices.add(device);
            GattDataJson json = new GattDataJson(device, rssi, scanRecord);
            mapProperties.put(device, json);
        }
    }
}
