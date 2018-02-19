package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;

import java.util.HashSet;
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
}
