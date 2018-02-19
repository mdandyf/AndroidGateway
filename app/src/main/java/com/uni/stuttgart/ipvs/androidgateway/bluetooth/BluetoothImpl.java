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

public class BluetoothImpl implements Bluetooth {
    private Context context;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBleAdvertiser;
    private Set<BluetoothDevice> scanDevices;
    private BluetoothLeScanner mBluetoothLe;



    private BluetoothGatt mBluetoothGatt;
    private HashSet<String> triedDevices;
    private float deviceCounter;

    public BluetoothImpl(){}

    @Override
    public void setContext(Context input) {
        this.context = input;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setBluetoothAdapter(BluetoothAdapter input) {
        this.mBluetoothAdapter = input;
    }

    @Override
    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    @Override
    public void setBle(BluetoothLeAdvertiser input) {
        this.mBleAdvertiser = input;
    }

    @Override
    public BluetoothLeAdvertiser getBle() {
        return mBleAdvertiser;
    }

    @Override
    public void setScannedDevice(Set<BluetoothDevice> input) {
        this.scanDevices = input;
    }

    @Override
    public Set<BluetoothDevice> getScannedDevice() {
        return scanDevices;
    }

    @Override
    public void setBleScanner(BluetoothLeScanner input) {
        this.mBluetoothLe = input;
    }

    @Override
    public BluetoothLeScanner getBleScanner() {
        return mBluetoothLe;
    }

    @Override
    public void setBluetoothGatt(BluetoothGatt input) {
        this.mBluetoothGatt = input;
    }

    @Override
    public BluetoothGatt getBluetoothGatt() {
        return mBluetoothGatt;
    }

    @Override
    public void setTriedDevices(HashSet<String> input) {
        this.triedDevices = input;
    }

    @Override
    public HashSet<String> getTriedDevices() {
        return triedDevices;
    }

    @Override
    public void setDeviceCounter(float input) {
        this.deviceCounter = input;
    }

    @Override
    public float getDeviceCounter() {
        return deviceCounter;
    }

}
