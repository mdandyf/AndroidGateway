package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;

import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */

public class ScanCallbackOld implements BluetoothAdapter.LeScanCallback {

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, GattDataJson> mapProperties;
    private Handler mHandlerMessage;

    public ScanCallbackOld(List<BluetoothDevice> listDevices, Map<BluetoothDevice, GattDataJson> mapProperties) {
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, GattDataJson> getMapProperties(){return mapProperties;}
    public void setHandlerMessage(Handler mHandlerMessage) {this.mHandlerMessage = mHandlerMessage;}


    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (!listDevices.contains(device)) {
            listDevices.add(device);
            GattDataJson json = new GattDataJson(device, rssi, scanRecord);
            mapProperties.put(device, json);
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 5, 0, mapProperties));
        }
    }
}
