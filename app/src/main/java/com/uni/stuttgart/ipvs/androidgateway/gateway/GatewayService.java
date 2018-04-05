package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.aware.Characteristics;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLe;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeGattCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.CharacteristicsDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.ServicesDatabase;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattLookUp;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by mdand on 3/17/2018.
 */

public class GatewayService extends Service {
    private static final String TAG = "GatewayService";
    private static final int SCAN_PERIOD = 10; // in second
    public static final String MESSAGE_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.MESSAGE_COMMAND";
    public static final String TERMINATE_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.TERMINATE_COMMAND";
    public static final String START_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.START_COMMAND";
    public static final String STOP_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.STOP_COMMAND";

    private Intent mService;
    private Context context;
    private ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
    private BluetoothLe ble = new BluetoothLe();
    private Object lock = new Object();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeGattCallback mBluetoothGattCallback;
    private List<BluetoothDevice> scanResults;
    private List<BluetoothGatt> listBluetoothGatt;
    private List<BluetoothGatt> listConnectedGatt;
    private List<BluetoothGatt> listDisconnectedGatt;
    private Map<BluetoothDevice, GattDataJson> mapScanResults;
    private Map<BluetoothGatt, BluetoothLeGattCallback> mapGattCallback;

    private HandlerThread mThread = new HandlerThread("mThreadCallback");
    private Handler mHandlerMessage;
    private Handler mHandlerScanning;
    private boolean isExecCommand = false;
    private boolean mProcessing = false;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);
    private ServicesDatabase bleServicesDatabase = new ServicesDatabase(this);
    private CharacteristicsDatabase bleCharacteristicDatabase = new CharacteristicsDatabase(this);

    @Override
    public void onCreate() {
        super.onCreate();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);
        mHandlerScanning = new Handler();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        clearAllLists();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = intent;
        context = this;

        mProcessing = true;
        initQueue();
        execQueue();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void initQueue() {
        queueCommand(null, null, null, null, BluetoothLe.START_SEQUENCE);
    }

    public boolean execQueue() {
        if (!queue.isEmpty()) {
            for (ble = (BluetoothLe) queue.poll(); ble != null; ble = (BluetoothLe) queue.poll()) {
                synchronized (ble) {
                    int type = ble.getTypeCommand();

                    if(type == BluetoothLe.START_SEQUENCE) {
                        broadcastUpdate("Starting sequence command... ");
                        if(listConnectedGatt.size() != 0) {
                           clearAllLists();
                           //read dbase
                            //scan known device
                            // scan unknown device
                            broadcastUpdate("Start sequence sticky... ");
                            queueCommand(null, null, null, null, BluetoothLe.SCANNING);
                            execQueue();
                        } else {
                            // scan all possible devices
                            queueCommand(null, null, null, null, BluetoothLe.SCANNING);
                            execQueue();
                        }
                    } else if (type == BluetoothLe.SCANNING) {
                        //step scan new BLE devices
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                broadcastUpdate("Scanning bluetooth...");
                                Log.d(TAG, "Start scanning for " + SCAN_PERIOD + " seconds");
                                mBluetoothLeScanProcess.scanLeDevice(true);
                                mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                mHandlerScanning.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        mBluetoothLeScanProcess.scanLeDevice(false);
                                        Log.d(TAG, "Stop scanning...");
                                        broadcastUpdate("Stop scanning bluetooth...");
                                        broadcastUpdate("Found " + mBluetoothLeScanProcess.getScanResult().size() + " device(s)");
                                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 0, 0, mBluetoothLeScanProcess.getScanResult()));
                                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 1, 0, mBluetoothLeScanProcess.getScanProperties()));
                                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 2, 0, true));
                                        updateDatabaseDevice(mBluetoothLeScanProcess.getScanResult(), mBluetoothLeScanProcess.getScanProperties());
                                    }
                                }, SCAN_PERIOD * 1000);
                            }
                        }).start();

                    } else if(type == BluetoothLe.FIND_LE_DEVICE) {
                        // step scan known BLE devices
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                broadcastUpdate("Scanning known BLE devices...");
                                Log.d(TAG, "Start scanning for known BLE device ");
                                UUID[] listBle = new UUID[1];
                                listBle[0] = ble.getServiceUUID();
                                mBluetoothLeScanProcess.findLeDevice(listBle, true);
                                mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                            }
                        }).start();
                    } else if (type == BluetoothLe.CONNECTING) {
                        broadcastUpdate("\n");
                        for (final BluetoothDevice device : scanResults) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (lock) {
                                        broadcastUpdate("connecting to " + device.getAddress());
                                        BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                                        gattCallback.setHandlerMessage(mHandlerMessage);
                                        gattCallback.connect();
                                        try {
                                            lock.wait();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }).start();
                        }
                        broadcastUpdate("\n");
                    } else if (type == BluetoothLe.CONNECTED) {
                        synchronized (lock) {
                            final BluetoothGatt gatt = ble.getGatt();
                            broadcastUpdate("connected to " + gatt.getDevice().getAddress());
                            broadcastUpdate("discovering services...");
                            gatt.readRemoteRssi();
                            gatt.discoverServices();
                            lock.notifyAll();
                        }
                    } else if (type == BluetoothLe.DISCONNECTED) {
                        synchronized (lock) {
                            BluetoothGatt gatt = ble.getGatt();
                            gatt.disconnect();
                            broadcastUpdate("Disconnected from " + ble.getGatt().getDevice().getAddress());
                            lock.notifyAll();
                        }
                    } else if (type == BluetoothLe.READ) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(ble.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Reading Characteristic " + GattLookUp.characteristicNameLookup(ble.getCharacteristicUUID()));
                        mBluetoothGattCallback.readCharacteristic(ble.getServiceUUID(), ble.getCharacteristicUUID());
                    } else if (type == BluetoothLe.REGISTER_NOTIFY) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(ble.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Registering Notify Characteristic " + ble.getCharacteristicUUID().toString());
                        mBluetoothGattCallback.writeDescriptorNotify(ble.getServiceUUID(), ble.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                    } else if (type == BluetoothLe.REGISTER_INDICATE) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(ble.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Registering Indicate Characteristic " + ble.getCharacteristicUUID().toString());
                        mBluetoothGattCallback.writeDescriptorIndication(ble.getServiceUUID(), ble.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                    } else if (type == BluetoothLe.WRITE) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(ble.getGatt());
                    } else if (type == BluetoothLe.STOP_SEQUENCE) {
                        broadcastUpdate("Stoping sequence command... ");
                        initQueue();
                        execQueue();
                    }

                }
            }

        }

        return true;
    }

    public synchronized void queueCommand(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data, int commandType) {
        ble = new BluetoothLe(gatt, serviceUUID, characteristicUUID, data, commandType);
        if (!queue.contains(ble)) {
            queue.add(ble);
        }
    }

    public void disconnect() {
        for (Map.Entry<BluetoothGatt, BluetoothLeGattCallback> entry : mapGattCallback.entrySet()) {
            BluetoothLeGattCallback gattCallback = entry.getValue();
            BluetoothGatt gatt = entry.getKey();
            if (gatt != null) {
                gattCallback.disconnect();
            }
        }

        mProcessing = false;
        stopSelf();
    }

    /**
     * Handling callback messages
     */
    Handler.Callback mHandlerCallback = new Handler.Callback() {

        GattDataJson dataJson;
        BluetoothGatt mBluetoothGatt;
        int callbackCounter = 0;
        int readCounter = 0;


        @Override
        public boolean handleMessage(Message msg) {
            try {

                if (msg.what == 0) {
                    // getting results from scanning
                    if (msg.arg1 == 0) {
                        isExecCommand = false;
                        scanResults = new ArrayList<>();
                        scanResults = (List<BluetoothDevice>) msg.obj;
                    } else if (msg.arg1 == 1) {
                        mapScanResults = new HashMap<>();
                        mapScanResults = (Map<BluetoothDevice, GattDataJson>) msg.obj;
                    } else if (msg.arg1 == 2) {
                        // Next, do connecting
                        queueCommand(null, null, null, null, BluetoothLe.CONNECTING);
                        isExecCommand = (boolean) msg.obj;
                    }

                } else if (msg.what == 1) {

                    //getting results from connecting
                    if (msg.arg1 == 0) {
                        // read all bluetoothGatt servers
                        isExecCommand = false;
                        listBluetoothGatt.add((BluetoothGatt) msg.obj);
                    } else if (msg.arg1 == 1) {
                        // read all bluetoothGatt connected servers
                        isExecCommand = true;
                        BluetoothGatt connectedGatt = ((BluetoothGatt) msg.obj);
                        listConnectedGatt.add(connectedGatt);
                        dataJson = new GattDataJson(connectedGatt.getDevice(), connectedGatt);
                        queueCommand(connectedGatt, null, null, null, BluetoothLe.CONNECTED);
                        callbackCounter++;
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        isExecCommand = true;
                        BluetoothGatt disconnectedGatt = ((BluetoothGatt) msg.obj);
                        listDisconnectedGatt.add(disconnectedGatt);
                        disconnectedGatt.close();
                        dataJson = new GattDataJson(disconnectedGatt.getDevice(), disconnectedGatt);
                        queueCommand(disconnectedGatt, null, null, null, BluetoothLe.DISCONNECTED);
                        if(listConnectedGatt.contains(disconnectedGatt)) {callbackCounter--;}

                        // if all devices have been disconnected
                        if (listBluetoothGatt.size() == listDisconnectedGatt.size()) {
                            queueCommand(null, null, null, null, BluetoothLe.STOP_SEQUENCE);
                        }

                    } else if (msg.arg1 == 3) {
                        // read all bluetoothGatt rssi
                        isExecCommand = false;
                        dataJson.setRssi((int) msg.obj);
                    } else if (msg.arg1 == 4) {
                        //discovered services and read all characteristics
                        broadcastUpdate("\n");
                        isExecCommand = true;
                        mProcessing = true;
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        if (mBluetoothGatt != null) {
                            getServiceCharacteristic(mBluetoothGatt);
                            dataJson.setGatt(mBluetoothGatt);
                        }
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        isExecCommand = false;
                        readData(msg);
                        readCounter++;
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        isExecCommand = false;
                        readData(msg);
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        isExecCommand = false;
                        readData(msg);
                        readCounter++;
                    } else if(msg.arg1 == 10) {
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                    }
                }

                if (isExecCommand && mProcessing) {
                    execQueue();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private synchronized String readData(Message msg) {
            boolean databaseService = false;
            boolean databaseCharacteristic = false;
            BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
            GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    String characteristicValue = GattDataHelper.decodeCharacteristicValue(characteristic, gatt);
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
                    broadcastUpdate("Characteristic : " + GattLookUp.characteristicNameLookup(characteristic.getUuid()));
                    broadcastUpdate("Characteristic property: " + properties);
                    broadcastUpdate("Characteristic read value: " + characteristicValue);
                    databaseService = updateDatabaseService(gatt.getDevice().getAddress(), service.getUuid().toString());
                    databaseCharacteristic = updateDatabaseCharacteristics(gatt.getDevice().getAddress(), service.getUuid().toString(), characteristic.getUuid().toString(), properties, characteristicValue);
                }
            }

            if (databaseService) {
                broadcastUpdate("Services have been written to database");
            }
            if (databaseCharacteristic) {
                broadcastUpdate("Characteristics have been written to database");
            }

            if (databaseService && databaseCharacteristic) {
                gatt.disconnect();
            }
            return json.getJsonData().toString();
        }
    };

    private void clearAllLists() {
        listBluetoothGatt = new ArrayList<>();
        listConnectedGatt = new ArrayList<>();
        listDisconnectedGatt = new ArrayList<>();
        mapGattCallback = new HashMap<>();
    }

    private synchronized void getServiceCharacteristic(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            queueCommand(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.READ);
                        } else if (property.equals("Write")) {
                            queueCommand(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.WRITE);
                        } else if (property.equals("Notify")) {
                            queueCommand(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_NOTIFY);
                        } else if (property.equals("Indicate")) {
                            queueCommand(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_INDICATE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private void broadcastUpdate(String message) {
        final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }

    private void broadcastStop(String message) {
        final Intent intent = new Intent(GatewayService.STOP_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }

    private void updateDatabaseDevice(List<BluetoothDevice> listDevices, Map<BluetoothDevice, GattDataJson> data) {
        broadcastUpdate("Write device to database");
        for (BluetoothDevice device : listDevices) {
            String deviceName = "unknown";
            int deviceRssi = 0;
            try {
                deviceRssi = (Integer) data.get(device).getJsonAdvertising().get("rssi");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (device.getName() != null) {
                deviceName = device.getName();
            }
            bleDeviceDatabase.insertData(device.getAddress(), deviceName, deviceRssi);
        }
    }

    private boolean updateDatabaseService(String macAddress, String serviceUUID) {
        return bleServicesDatabase.insertData(macAddress, serviceUUID);
    }

    private boolean updateDatabaseCharacteristics(String macAddress, String serviceUUID, String characteristicUUID, String property, String value) {
        return bleCharacteristicDatabase.insertData(macAddress, serviceUUID, characteristicUUID, property, value);
    }

    private void sleepThread(long time) {
        try {
            Log.d(TAG, "Sleep for " + String.valueOf(time) + " ms");
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
