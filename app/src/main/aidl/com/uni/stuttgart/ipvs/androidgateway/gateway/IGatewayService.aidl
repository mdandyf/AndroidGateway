// IGatewayService.aidl
package com.uni.stuttgart.ipvs.androidgateway.gateway;

// Declare any non-default types here with import statements

import BluetoothDevice;
import List;
import ParcelUuid;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PMessageHandler;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PHandlerThread;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PManufacturer;

interface IGatewayService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

    int getPid();

    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    int getNumberRunningTasks();

    int getNumberOfThreads();

    int getNumberOfProcessor();

    void setCycleCounter(in int cycleCounter);

    int getCycleCounter();

    String getCurrentStatus();

    void setHandler(in PMessageHandler messageHandler, in String threadName, in String type);

    PMessageHandler getMessageHandler();

    PHandlerThread getHandlerThread();

    void setProcessing(boolean mProcessing);

    boolean getScanState();

    void setScanResult(in List<BluetoothDevice> scanResult);

    void setScanResultNonVolatile(in List<BluetoothDevice> scanResult);

    List<BluetoothDevice> getScanResults();

    List<BluetoothDevice> getScanResultsNonVolatile();

    void setCurrentGatt(in PBluetoothGatt gatt);

    PBluetoothGatt getCurrentGatt();

    void setListGatt(in List<PBluetoothGatt> listGatt);

    //void addQueueScanning(in String macAddress, in String name, in int rssi, in int typeCommand, in ParcelUuid serviceUUID, in long waitTime);

    //void execScanningQueue();

    void doConnect(in String macAddress);

    PBluetoothGatt doConnecting(in String macAddress);

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

    void updateDatabaseDevicePowerUsage(in String macAddress, in long powerUsage);

    void updateAllDeviceStates(in List<String> nearbyDevices);

    boolean checkDevice(in String macAddress);

    List<String> getListDevices() ;

    List<String> getListActiveDevices();

    int getDeviceRSSI(in String macAddress);

    byte[] getDeviceScanRecord(in String macAddress);

    boolean isDeviceManufacturerKnown(in String macAddress);

    String getDeviceName(in String macAddress);

    String getDeviceState(in String macAddress);

    long getDevicePowerUsage(in String macAddress);

    boolean checkManufacturer(in String mfr_id);

    boolean checkManufacturerService(in String mfr_id, in String serviceUUID);

    List<PManufacturer> getListManufacturers();

    void setPowerUsageConstraints(in String dataName, in double[] data);

    double[] getPowerUsageConstraints(in double batteryLevel);

    void setTimeSettings(in String dataName, in int data);

    int getTimeSettings(in String type);

    void setTimeUnit(in String unit);

    String getTimeUnit();

    List<ParcelUuid> getServiceUUIDs(in String macAddress);

    List<ParcelUuid> getCharacteristicUUIDs(in String macAddress, in String serviceUUID);

    String getCharacteristicProperty(in String macAddress, in String serviceUUID, in String CharacteristicUUID);

    String getCharacteristicValue(in String macAddress, in String serviceUUID, in String CharacteristicUUID);

    void disconnectSpecificGatt(in String macAddress);

    void broadcastUpdate(in String message);

    void broadcastCommand(in String message, in String action);

    void broadcastServiceInterface(in String message);

    void broadcastClearScreen(in String message);

     void startPowerEstimator();

     void stopPowerEstimator();

     long getPowerEstimatorData(in String type);

     void startScan(in long time);

     void stopScanning();

     void stopScan();

     void startScanKnownDevices(String macAddress);

     void saveCloudData(in String macAddress);

     void uploadDataCloud();

     void insertDatabaseUpload(in String macAddress, in String data, in String uploadState);

     void updateDatabaseUpload(in String macAddress, in String uploadState);
}