package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mdand on 2/19/2018.
 */

public interface Bluetooth {
    public void setContext(Context input);
    public Context getContext();
    public void setBluetoothAdapter(BluetoothAdapter input);
    public BluetoothAdapter getBluetoothAdapter();
    public void setBle(BluetoothLeAdvertiser input);
    public BluetoothLeAdvertiser getBle();
    public void setScannedDevice(Set<BluetoothDevice> input);
    public Set<BluetoothDevice> getScannedDevice();
    public void setBleScanner(BluetoothLeScanner input);
    public BluetoothLeScanner getBleScanner();
    public void setBluetoothGatt(BluetoothGatt input);
    public BluetoothGatt getBluetoothGatt();
    public void setTriedDevices(HashSet<String> input);
    public HashSet<String> getTriedDevices();
    public void setDeviceCounter(float input);
    public float getDeviceCounter();
    public void setBluetoothService(BluetoothGatt gatt, List<BluetoothGattService> input);
    public Map<BluetoothGatt, List<BluetoothGattService>> getBluetoothService();
    public void setBluetoothCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic input);
    public Map<BluetoothGatt, BluetoothGattCharacteristic> getBluetoothCharacteristic();
    public void setBluetoothDevice(ScanResult input);
    public ScanResult getBluetoothDevice();
    public void setJsonData(JSONArray json);
    public JSONArray getJsonData();
}
