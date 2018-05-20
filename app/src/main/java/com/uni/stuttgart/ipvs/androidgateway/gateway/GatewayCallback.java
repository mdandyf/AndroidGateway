package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeGatt;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataLookUp;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GatewayCallback implements Handler.Callback {

    private boolean mProcessing;
    private IGatewayService.Stub mBinder;
    private GattDataJson dataJson;

    private Handler mHandlerMessage;
    private Context context;

    private List<PBluetoothGatt> listBluetoothGatt = new ArrayList<>();

    public GatewayCallback(Context context, boolean mProcessing, IGatewayService.Stub mBinder) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.mBinder = mBinder;
    }

    public void setmHandlerMessage(Handler mHandlerMessage) {
        this.mHandlerMessage = mHandlerMessage;
    }

    @Override
    public boolean handleMessage(Message msg) {
        try {

            if (msg.what == 0) {
                // getting results from scanning
                if (msg.arg1 == 0) {

                } else if (msg.arg1 == 1) {

                } else if (msg.arg1 == 2) {

                } else if (msg.arg1 == 3) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ScanResult result = ((ScanResult) msg.obj);
                        List<BluetoothDevice> scanResults = mBinder.getScanResults();
                        if (!scanResults.contains(result.getDevice())) {
                            scanResults.add(result.getDevice());
                            mBinder.setScanResult(scanResults);
                            mBinder.insertDatabaseDevice(result.getDevice(), result.getRssi(), "active");
                        } else {
                            mBinder.updateDatabaseDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                        }
                    }
                } else if (msg.arg1 == 4) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        List<ScanResult> results = ((List<ScanResult>) msg.obj);
                        List<BluetoothDevice> scanResults = mBinder.getScanResults();
                        for (ScanResult result : results) {
                            if (!scanResults.contains(result.getDevice())) {
                                scanResults.add(result.getDevice());
                                mBinder.setScanResult(scanResults);
                                mBinder.insertDatabaseDevice(result.getDevice(), result.getRssi(), "active");
                            } else {
                                mBinder.updateDatabaseDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                            }
                        }
                    }
                } else if (msg.arg1 == 5) {
                    final Map<BluetoothDevice, GattDataJson> mapDevice = ((Map<BluetoothDevice, GattDataJson>) msg.obj);
                    for (Map.Entry entry : mapDevice.entrySet()) {
                        BluetoothDevice device = (BluetoothDevice) entry.getKey();
                        List<BluetoothDevice> scanResults = mBinder.getScanResults();
                        if (!scanResults.contains(device)) {
                            scanResults.add(device);
                            mBinder.setScanResult(scanResults);
                            GattDataJson data = (GattDataJson) entry.getValue();
                            int deviceRssi = 0;
                            try { deviceRssi = (Integer) data.getJsonAdvertising().get("rssi"); } catch (JSONException e) { e.printStackTrace(); }
                            mBinder.insertDatabaseDevice(device, deviceRssi, "active");
                        } else {
                            GattDataJson data = (GattDataJson) entry.getValue();
                            int deviceRssi = 0;
                            try { deviceRssi = (Integer) data.getJsonAdvertising().get("rssi"); } catch (JSONException e) { e.printStackTrace(); }
                            mBinder.updateDatabaseDevice(device, deviceRssi, null);
                        }
                    }
                } else if (msg.arg1 == 7) {
                    final BluetoothDevice device = (BluetoothDevice) msg.obj;
                    List<BluetoothDevice> scanResults = mBinder.getScanResults();
                    if (!scanResults.contains(device)) {
                        scanResults.add(device);
                        mBinder.setScanResult(scanResults);
                    }
                } else if (msg.arg1 == 10) {
                    final Map<BluetoothDevice, byte[]> mapScanRecord = ((Map<BluetoothDevice, byte[]>) msg.obj);
                    if(mapScanRecord.size() > 0) {
                        for (Map.Entry entry : mapScanRecord.entrySet()) {
                            BluetoothDevice device = (BluetoothDevice) entry.getKey();
                            List<BluetoothDevice> scanResults = mBinder.getScanResults();
                            if (!scanResults.contains(device)) {
                                mBinder.updateDatabaseDeviceAdvRecord(device, (byte[]) entry.getValue());
                            }
                        }
                    }
                }

            } else if (msg.what == 1) {

                //getting results from connecting
                if (msg.arg1 == 0) {
                    // read all bluetoothGatt servers
                    BluetoothGatt gatt = (BluetoothGatt) msg.obj;
                    PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                    parcelBluetoothGatt.setGatt(gatt);
                    if (gatt != null && !listBluetoothGatt.contains(parcelBluetoothGatt)) {
                        listBluetoothGatt.add(parcelBluetoothGatt);
                        mBinder.setListGatt(listBluetoothGatt);
                    }
                } else if (msg.arg1 == 1) {
                    // read all bluetoothGatt connected servers
                    BluetoothGatt connectedGatt = ((BluetoothGatt) msg.obj);
                    dataJson = new GattDataJson(connectedGatt.getDevice(), connectedGatt);
                    PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                    parcelBluetoothGatt.setGatt(connectedGatt);
                    mBinder.doConnected(parcelBluetoothGatt);
                } else if (msg.arg1 == 2) {
                    // read all bluetoothGatt disconnected servers
                    BluetoothGatt disconnectedGatt = ((BluetoothGatt) msg.obj);
                    PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                    parcelBluetoothGatt.setGatt(disconnectedGatt);
                    dataJson = new GattDataJson(disconnectedGatt.getDevice(), disconnectedGatt);
                    mBinder.doDisconnected(parcelBluetoothGatt, "GatewayService");
                    disconnectedGatt.close();
                    mBinder.broadcastCommand("Command disconnected Gatt", GatewayService.DISCONNECT_COMMAND);
                } else if (msg.arg1 == 3) {
                    // read all bluetoothGatt rssi
                    dataJson.setRssi((int) msg.obj);
                } else if (msg.arg1 == 4) {
                    //discovered services and read all characteristics
                    mBinder.broadcastUpdate("\n");
                    BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                    PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                    parcelBluetoothGatt.setGatt(gatt);
                    mBinder.setCurrentGatt(parcelBluetoothGatt);
                    if (gatt != null) {
                        getServiceCharacteristic(gatt);
                        dataJson.setGatt(gatt);
                        mBinder.execCharacteristicQueue();
                    }
                } else if (msg.arg1 == 5) {
                    // onReadCharacteristic
                    readData(msg);
                } else if (msg.arg1 == 6) {
                    // onWriteCharacteristic
                    readData(msg);
                } else if (msg.arg1 == 7) {
                    //onCharacteristicChanged
                    readData(msg);
                } else if (msg.arg1 == 12) {
                    //Finish Reading
                    mBinder.broadcastCommand("Finish reading data", GatewayService.FINISH_READ);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private synchronized String readData(Message msg) {
        GattDataJson json = null;
        try {
            mBinder.broadcastUpdate("\n");
            boolean databaseService = false;
            boolean databaseCharacteristic = false;
            Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
            for (Map.Entry entry : mapGatt.entrySet()) {
                BluetoothGatt gatt = (BluetoothGatt) entry.getKey();
                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                json = new GattDataJson(gatt.getDevice(), gatt);
                json.updateJsonData(json.getJsonData(), (BluetoothGattCharacteristic) entry.getValue());
                String characteristicValue = GattDataHelper.decodeCharacteristicValue(characteristic);
                JSONArray characteristicProperty = GattDataHelper.decodeProperties(characteristic);
                String properties = null;
                for (int i = 0; i < characteristicProperty.length(); i++) {
                    try {
                        if (characteristicProperty.get(i) != null && properties != null) {
                            properties = properties + ", " + (String) characteristicProperty.get(i);
                        } else if (characteristicProperty.get(i) != null && properties == null) {
                            properties = (String) characteristicProperty.get(i);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                mBinder.broadcastUpdate("Characteristic: " + GattDataLookUp.characteristicNameLookup(characteristic.getUuid()));
                mBinder.broadcastUpdate("UUID: " + characteristic.getUuid().toString());
                mBinder.broadcastUpdate("Property: " + properties);
                mBinder.broadcastUpdate("Value: " + characteristicValue);
                try {
                    databaseService = mBinder.updateDatabaseService(gatt.getDevice().getAddress(), characteristic.getService().getUuid().toString());
                    databaseCharacteristic = mBinder.updateDatabaseCharacteristics(gatt.getDevice().getAddress(), characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), properties, characteristicValue);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if (databaseService) {
                    mBinder.broadcastUpdate("Services have been written to database");
                }
                if (databaseCharacteristic) {
                    mBinder.broadcastUpdate("Characteristics have been written to database");
                }

                if (databaseService && databaseCharacteristic) {
                    try {
                        mBinder.updateDatabaseDeviceState(gatt.getDevice(), "active");
                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 12, 0));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return json.getJsonData().toString();
    }

    private synchronized void getServiceCharacteristic(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        ParcelUuid uuidService = new ParcelUuid(service.getUuid());
                        ParcelUuid uuidCharacteristic = new ParcelUuid(characteristic.getUuid());
                        PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                        parcelBluetoothGatt.setGatt(gatt);
                        if (property.equals("Read")) {
                            mBinder.addQueueCharacteristic(parcelBluetoothGatt, uuidService, uuidCharacteristic, null, null, BluetoothLeGatt.READ);
                        } else if (property.equals("Write")) {
                            mBinder.addQueueCharacteristic(parcelBluetoothGatt, uuidService, uuidCharacteristic, null, null, BluetoothLeGatt.WRITE);
                        } else if (property.equals("Notify")) {
                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                ParcelUuid uuidDescriptor = new ParcelUuid(descriptor.getUuid());
                                mBinder.addQueueCharacteristic(parcelBluetoothGatt, uuidService, uuidCharacteristic, uuidDescriptor, null, BluetoothLeGatt.REGISTER_NOTIFY);
                            }
                        } else if (property.equals("Indicate")) {
                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                ParcelUuid uuidDescriptor = new ParcelUuid(descriptor.getUuid());
                                mBinder.addQueueCharacteristic(parcelBluetoothGatt, uuidService, uuidCharacteristic, uuidDescriptor, null, BluetoothLeGatt.REGISTER_INDICATE);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
