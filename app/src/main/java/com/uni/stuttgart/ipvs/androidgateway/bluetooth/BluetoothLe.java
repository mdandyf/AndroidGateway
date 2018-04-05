package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.UUID;

/**
 * Created by mdand on 2/28/2018.
 */

public class BluetoothLe {

    public static int READ = 100;
    public static int REGISTER_NOTIFY = 101;
    public static int REGISTER_INDICATE = 102;
    public static int WRITE = 200;

    public static int START_SEQUENCE = 300;
    public static int SCANNING = 301;
    public static int FIND_LE_DEVICE = 302;
    public static int CONNECTING = 303;
    public static int CONNECTED = 304;
    public static int DISCONNECTED = 305;

    public static int STOP_SEQUENCE = 400;
    public static int UPDATE_UI_CONNECTED = 401;
    public static int UPDATE_UI_DISCONNECTED = 402;

    private BluetoothGatt gatt;
    private UUID serviceUUID;
    private UUID characteristicUUID;
    private byte[] data;
    private int typeCommand;
    private String jsonData;

    public BluetoothLe(){}

    public BluetoothLe(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data, int typeCommand) {
        this.gatt = gatt;
        this.serviceUUID = serviceUUID;
        this.characteristicUUID = characteristicUUID;
        this.data = data;
        this.typeCommand = typeCommand;
    }

    public BluetoothGatt getGatt() {return gatt;}

    public void setGatt(BluetoothGatt gatt) {this.gatt = gatt;}

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    public void setServiceUUID(UUID serviceUUID) {
        this.serviceUUID = serviceUUID;
    }

    public UUID getCharacteristicUUID() {
        return characteristicUUID;
    }

    public void setCharacteristicUUID(UUID characteristicUUID) {
        this.characteristicUUID = characteristicUUID;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getTypeCommand() {return typeCommand;}

    public void setTypeCommand(int typeCommand) {
        this.typeCommand = typeCommand;
    }

    public void setJsonData(String jsonData) {this.jsonData = jsonData;}

    public String getJsonData() {return this.jsonData;}

}
