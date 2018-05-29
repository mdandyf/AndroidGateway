// IGatewayService.aidl
package com.uni.stuttgart.ipvs.androidgateway.gateway;

// Declare any non-default types here with import statements

import BluetoothDevice;
import List;
import ParcelUuid;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PMessageHandler;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PHandlerThread;
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

    PHandlerThread getHandlerThread();

    void setProcessing(boolean mProcessing);

    boolean getScanState();

    void setScanResult(in List<BluetoothDevice> scanResult);

    List<BluetoothDevice> getScanResults();

    void setCurrentGatt(in PBluetoothGatt gatt);

    PBluetoothGatt getCurrentGatt();

    void setListGatt(in List<PBluetoothGatt> listGatt);

    void addQueueScanning(in String macAddress, in String name, in int rssi, in int typeCommand, in ParcelUuid serviceUUID);

    void execScanningQueue();

    void doConnect(in String macAddress);

    void doConnecting(in String macAddress);

    void doConnected(in PBluetoothGatt gatt);

    void doDisconnected(in PBluetoothGatt gatt, in String type);

    void addQueueCharacteristic(in PBluetoothGatt gatt, in ParcelUuid serviceUUID, in ParcelUuid characteristicUUID, in ParcelUuid descriptorUUID, in byte[] data, in int typeCommand);

    void execCharacteristicQueue();

    BluetoothDevice getDevice(String macAddress);

    void initializeDatabase();

    void insertDatabaseDevice(in BluetoothDevice device, in int rssi, in String deviceState);

    void updateDatabaseDevice(in BluetoothDevice device, in int rssi, in byte[] scanRecord);

    boolean updateDatabaseService(in String macAddress, in String serviceUUID);

    boolean updateDatabaseCharacteristics(in String macAddress, in String serviceUUID, in String characteristicUUID, in String property, in String value);

    void updateDatabaseDeviceState(in BluetoothDevice device, in String deviceState);

    void updateDatabaseDeviceAdvRecord(in BluetoothDevice device, in byte[] scanRecord);

    void updateDatabaseDeviceUsrChoice(in String macAddress, in String userChoice);

    void updateDatabaseDevicePowerUsage(in String macAddress, in long powerUsage);

    void updateAllDeviceStates(in List<String> nearbyDevices);

    boolean checkDevice(in String macAddress);

    List<String> getListDevices() ;

    List<String> getListActiveDevices();

    int getDeviceRSSI(in String macAddress);

    byte[] getDeviceScanRecord(in String macAddress);

    String getDeviceUsrChoice(in String macAddress);

    String getDeviceState(in String macAddress);

    long getDevicePowerUsage(in String macAddress);

    void insertDatabasePowerUsage(in String idCase, in double batteryLevel, in double batteryLevelUpper, in double powerUsage1, in double powerUsage2, in double powerUsage3);

    double[] getPowerUsageConstraints(in double batteryLevel);

    List<ParcelUuid> getServiceUUIDs(String macAddress);

    List<ParcelUuid> getCharacteristicUUIDs(String macAddress, String serviceUUID);

    String getCharacteristicValue(String macAddress, String serviceUUID, String CharacteristicUUID);

    void disconnectSpecificGatt(in String macAddress);

    void broadcastUpdate(in String message);

    void broadcastCommand(in String message, in String action);

}
