// IGatewayService.aidl
package com.uni.stuttgart.ipvs.androidgateway.gateway;

// Declare any non-default types here with import statements

import BluetoothDevice;
import List;
import ParcelUuid;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PMessageHandler;
//import Runnable;

interface IGatewayService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

    int getPid();

    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    String getCurrentStatus();

    void setMessageHandler(in PMessageHandler messageHandler);

    PMessageHandler getMessageHandler();

    void setProcessing(boolean mProcessing);

    List<BluetoothDevice> getScanResults();

    PBluetoothGatt getCurrentGatt();

    void addQueueScanning(in String macAddress, in String name, in int rssi, in int typeCommand, in ParcelUuid serviceUUID);

    void execScanningQueue();

    void doConnect(in String macAddress);

    void doConnecting(in String macAddress);

    void doConnected(in PBluetoothGatt gatt);

    void doDisconnected(in PBluetoothGatt gatt, in String type);

    void addQueueCharacteristic(in PBluetoothGatt gatt, in ParcelUuid serviceUUID, in ParcelUuid characteristicUUID, in ParcelUuid descriptorUUID, in byte[] data, in int typeCommand);

    void execCharacteristicQueue();

    void updateDatabaseDeviceState(in BluetoothDevice device, in String deviceState);

    void updateDatabaseDeviceAdvRecord(in BluetoothDevice device, in byte[] scanRecord);

    void updateAllDeviceStates(in List<String> nearbyDevices);

    boolean checkDevice(in String macAddress);

    List<String> getListDevices() ;

    List<String> getListActiveDevices();

    int getDeviceRSSI(in String macAddress);

    byte[] getDeviceScanRecord(in String macAddress);

    List<ParcelUuid> getServiceUUIDs(String macAddress);

    List<ParcelUuid> getCharacteristicUUIDs(String macAddress);

    void disconnectSpecificGatt(in String macAddress);

}
