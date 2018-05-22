package com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral;

import android.bluetooth.BluetoothGatt;

import java.util.UUID;

/**
 * Created by mdand on 4/9/2018.
 */

public class BluetoothLeDevice {
    private String macAddress;
    private String name;
    private int rssi;
    private int type;
    private UUID serviceUUID;

    public static int SCANNING = 300;
    public static int STOP_SCANNING = 301;
    public static int FIND_LE_DEVICE = 302;
    public static int STOP_SCAN = 303;
    public static int CONNECTED = 304;
    public static int DISCONNECTED = 305;
    public static int STOP_SEQUENCE = 306;
    public static int UPDATE_UI_CONNECTED = 307;

    public BluetoothLeDevice() {}

    public BluetoothLeDevice(String macAddress, String name, int rssi, int type, UUID serviceUUID) {
        this.macAddress = macAddress;
        this.name = name;
        this.rssi = rssi;
        this.type = type;
        this.serviceUUID = serviceUUID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    public void setServiceUUID(UUID serviceUUID) {
        this.serviceUUID = serviceUUID;
    }

}
