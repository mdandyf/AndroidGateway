package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeGatt;
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

    public static final String MESSAGE_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.MESSAGE_COMMAND";
    public static final String TERMINATE_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.TERMINATE_COMMAND";
    public static final String START_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.START_COMMAND";

    private Intent mIntent;
    private final IBinder mBinder = new LocalBinder();

    private Context context;
    private ConcurrentLinkedQueue queueScanning;
    private ConcurrentLinkedQueue queueConnecting;
    private ConcurrentLinkedQueue queueCharacteristic;
    private BluetoothLeDevice bleDevice;
    private BluetoothLeGatt bleGatt;
    private Object lock;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeGattCallback mBluetoothGattCallback;
    private List<BluetoothDevice> scanResults;
    private List<BluetoothGatt> listBluetoothGatt;
    private Map<BluetoothGatt, BluetoothLeGattCallback> mapGattCallback;
    private BluetoothGatt mBluetoothGatt;

    private HandlerThread mThread = new HandlerThread("mThreadCallback");
    private Handler mHandlerMessage;
    private boolean mProcessing;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);
    private ServicesDatabase bleServicesDatabase = new ServicesDatabase(this);
    private CharacteristicsDatabase bleCharacteristicDatabase = new CharacteristicsDatabase(this);

    private String status;

    @Override
    public void onCreate() {
        super.onCreate();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        queueScanning = new ConcurrentLinkedQueue();
        queueConnecting = new ConcurrentLinkedQueue();
        queueCharacteristic = new ConcurrentLinkedQueue();
        bleDevice = new BluetoothLeDevice();
        bleGatt = new BluetoothLeGatt();
        lock = new Object();

        listBluetoothGatt = new ArrayList<>();
        mapGattCallback = new HashMap<>();
        scanResults = new ArrayList<>();

        status = "Created";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mIntent = intent;
        context = this;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        mIntent = intent;
        context = this;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        return false;
    }

    /**
     * Class used for the client Binder.
     */
    public class LocalBinder extends Binder {
        GatewayService getService() {
            // Return this instance of Service so clients can call public methods
            return GatewayService.this;
        }
    }


    public String getCurrentStatus() {
        return status;
    }

    public void setProcessing(boolean mProcessing) {
        this.mProcessing = mProcessing;
    }

    ;

    public List<BluetoothDevice> getScanResults() {
        return this.scanResults;
    }

    public BluetoothGatt getCurrentGatt() {
        return this.mBluetoothGatt;
    }

    public void addQueueScanning(String macAddress, String name, int rssi, int typeCommand, UUID serviceUUID) {
        bleDevice = new BluetoothLeDevice(macAddress, name, rssi, typeCommand, serviceUUID);
        queueScanning.add(bleDevice);
    }

    public void execScanningQueue() {
        status = "Scanning";
        if (!queueScanning.isEmpty() && mProcessing) {
            for (bleDevice = (BluetoothLeDevice) queueScanning.poll(); bleDevice != null; bleDevice = (BluetoothLeDevice) queueScanning.poll()) {
                synchronized (bleDevice) {
                    int type = bleDevice.getType();
                    if (type == BluetoothLeDevice.SCANNING) {
                        //step scan new BLE devices
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                broadcastUpdate("Scanning bluetooth...");
                                Log.d(TAG, "Start scanning");
                                mBluetoothLeScanProcess.scanLeDevice(true);
                                mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                            }
                        }).start();
                    } else if (type == BluetoothLeDevice.FIND_LE_DEVICE) {
                        // step scan known BLE devices
                        if (bleDevice.getMacAddress() != null) {
                            BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(bleDevice.getMacAddress());
                        } else if (bleDevice.getServiceUUID() != null) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    broadcastUpdate("Scanning known BLE devices...");
                                    Log.d(TAG, "Start scanning for known BLE device ");
                                    UUID[] listBle = new UUID[1];
                                    listBle[0] = bleDevice.getServiceUUID();
                                    mBluetoothLeScanProcess.findLeDevice(listBle, true);
                                    mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                }
                            }).start();
                        }
                    } else if (type == BluetoothLeDevice.STOP_SCANNING) {
                        mBluetoothLeScanProcess.scanLeDevice(false);
                        Log.d(TAG, "Stop scanning...");
                        broadcastUpdate("Stop scanning bluetooth...");
                        broadcastUpdate("Found " + mBluetoothLeScanProcess.getScanResult().size() + " device(s)");
                    }
                }
            }
        }
    }

    public void addQueueConnecting(String macAddress, String name, int rssi, int typeCommand, BluetoothGatt bluetoothGatt) {
        bleDevice = new BluetoothLeDevice(macAddress, name, rssi, typeCommand, null);
        bleDevice.setBluetoothGatt(bluetoothGatt);
        queueConnecting.add(bleDevice);
    }

    public void execConnectingQueue() {
        status = "Connecting";
        if (!queueConnecting.isEmpty() && mProcessing) {
            for (bleDevice = (BluetoothLeDevice) queueConnecting.poll(); bleDevice != null; bleDevice = (BluetoothLeDevice) queueConnecting.poll()) {
                synchronized (bleDevice) {
                    int type = bleDevice.getType();
                    if (type == BluetoothLeDevice.CONNECTING) {
                        broadcastUpdate("\n");
                        final BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(bleDevice.getMacAddress());
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
                    } else if (type == BluetoothLeDevice.CONNECTED) {
                        synchronized (lock) {
                            final BluetoothGatt gatt = bleDevice.getBluetoothGatt();
                            broadcastUpdate("connected to " + bleDevice.getMacAddress());
                            broadcastUpdate("discovering services...");
                            status = "Discovering";
                            gatt.discoverServices();
                            lock.notifyAll();
                        }
                    } else if (type == BluetoothLeDevice.DISCONNECTED) {
                        synchronized (lock) {
                            BluetoothGatt gatt = bleDevice.getBluetoothGatt();
                            if(bleDevice.getMacAddress() == null) {
                                gatt.disconnect();
                                broadcastUpdate("Disconnected from " + gatt.getDevice().getAddress());
                            } else {
                                broadcastUpdate("Disconnected from " + bleDevice.getMacAddress());
                            }
                            lock.notifyAll();
                        }
                    }
                }
            }
        }
    }

    public void addQueueCharacteristic(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data, int typeCommand) {
        bleGatt = new BluetoothLeGatt(gatt, serviceUUID, characteristicUUID, data, typeCommand);
        queueCharacteristic.add(bleGatt);
    }

    public void execCharacteristicQueue() {
        status = "Reading";
        if (!queueCharacteristic.isEmpty() && mProcessing) {
            for (bleGatt = (BluetoothLeGatt) queueCharacteristic.poll(); bleGatt != null; bleGatt = (BluetoothLeGatt) queueCharacteristic.poll()) {
                synchronized (bleGatt) {
                    int type = bleGatt.getTypeCommand();
                    if (type == BluetoothLeGatt.READ) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Reading Characteristic " + GattLookUp.characteristicNameLookup(bleGatt.getCharacteristicUUID()));
                        mBluetoothGattCallback.readCharacteristic(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID());
                    } else if (type == BluetoothLeGatt.REGISTER_NOTIFY) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Registering Notify Characteristic " + bleGatt.getCharacteristicUUID().toString());
                        mBluetoothGattCallback.writeDescriptorNotify(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                    } else if (type == BluetoothLeGatt.REGISTER_INDICATE) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                        sleepThread(100);
                        broadcastUpdate("Registering Indicate Characteristic " + bleGatt.getCharacteristicUUID().toString());
                        mBluetoothGattCallback.writeDescriptorIndication(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                    } else if (type == BluetoothLeGatt.WRITE) {
                        mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                    }

                }
            }

        }
    }

    private synchronized void getServiceCharacteristic(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            addQueueCharacteristic(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.READ);
                        } else if (property.equals("Write")) {
                            addQueueCharacteristic(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.WRITE);
                        } else if (property.equals("Notify")) {
                            addQueueCharacteristic(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.REGISTER_NOTIFY);
                        } else if (property.equals("Indicate")) {
                            addQueueCharacteristic(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.REGISTER_INDICATE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Handling callback messages
     */
    Handler.Callback mHandlerCallback = new Handler.Callback() {

        GattDataJson dataJson;

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
                            if (!scanResults.contains(result.getDevice())) {
                                scanResults.add(result.getDevice());
                                insertDatabaseDevice(result.getDevice(), result.getRssi(), "inactive");
                            }
                        }
                    } else if (msg.arg1 == 4) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            List<ScanResult> results = ((List<ScanResult>) msg.obj);
                            for (ScanResult result : results) {
                                if (!scanResults.contains(result.getDevice())) {
                                    scanResults.add(result.getDevice());
                                    insertDatabaseDevice(result.getDevice(), result.getRssi(), "inactive");
                                }
                            }
                        }
                    } else if (msg.arg1 == 5) {
                        final Map<BluetoothDevice, GattDataJson> mapDevice = ((Map<BluetoothDevice, GattDataJson>) msg.obj);
                        for (Map.Entry entry : mapDevice.entrySet()) {
                            BluetoothDevice device = (BluetoothDevice) entry.getKey();
                            if (!scanResults.contains(device)) {
                                scanResults.add(device);
                                insertDatabaseDevice(device, (GattDataJson) entry.getValue(), "inactive");
                            }
                        }
                    }

                } else if (msg.what == 1) {

                    //getting results from connecting
                    if (msg.arg1 == 0) {
                        // read all bluetoothGatt servers
                        mBluetoothGatt = (BluetoothGatt) msg.obj;
                        listBluetoothGatt.add(mBluetoothGatt);
                    } else if (msg.arg1 == 1) {
                        // read all bluetoothGatt connected servers
                        BluetoothGatt connectedGatt = ((BluetoothGatt) msg.obj);
                        dataJson = new GattDataJson(connectedGatt.getDevice(), connectedGatt);
                        addQueueConnecting(connectedGatt.getDevice().getAddress(), connectedGatt.getDevice().getName(), 0, BluetoothLeDevice.CONNECTED, connectedGatt);
                        execConnectingQueue();
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        BluetoothGatt disconnectedGatt = ((BluetoothGatt) msg.obj);
                        addQueueConnecting(disconnectedGatt.getDevice().getAddress(), disconnectedGatt.getDevice().getName(), 0, BluetoothLeDevice.DISCONNECTED, disconnectedGatt);
                        execConnectingQueue();
                        disconnectedGatt.close();
                        dataJson = new GattDataJson(disconnectedGatt.getDevice(), disconnectedGatt);
                    } else if (msg.arg1 == 3) {
                        // read all bluetoothGatt rssi
                        dataJson.setRssi((int) msg.obj);
                    } else if (msg.arg1 == 4) {
                        //discovered services and read all characteristics
                        broadcastUpdate("\n");
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        if (mBluetoothGatt != null) {
                            getServiceCharacteristic(mBluetoothGatt);
                            dataJson.setGatt(mBluetoothGatt);
                            execCharacteristicQueue();
                        }
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        status = "Reading";
                        readData(msg);
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        readData(msg);
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        status = "Reading";
                        readData(msg);
                    }
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
            mBluetoothGatt = gatt;
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

            return json.getJsonData().toString();
        }
    };

    /**
     * some database routines section
     */

    private void insertDatabaseDevice(BluetoothDevice device, int rssi, String deviceState) {
        broadcastUpdate("Write device " + device.getAddress() + " to database");
        String deviceName = "unknown";
        if (device.getName() != null) {
            deviceName = device.getName();
        }
        bleDeviceDatabase.insertData(device.getAddress(), deviceName, rssi, deviceState);
    }

    private void insertDatabaseDevice(BluetoothDevice device, GattDataJson data, String deviceState) {
        broadcastUpdate("Write device " + device.getAddress() + " to database");
        String deviceName = "unknown";
        int deviceRssi = 0;
        try {
            deviceRssi = (Integer) data.getJsonAdvertising().get("rssi");
            if (device.getName() != null) {
                deviceName = device.getName();
            }
            bleDeviceDatabase.insertData(device.getAddress(), deviceName, deviceRssi, deviceState);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateDatabaseDevice(BluetoothDevice device, String deviceState) {

    }

    private boolean updateDatabaseService(String macAddress, String serviceUUID) {
        return bleServicesDatabase.insertData(macAddress, serviceUUID);
    }

    private boolean updateDatabaseCharacteristics(String macAddress, String serviceUUID, String characteristicUUID, String property, String value) {
        return bleCharacteristicDatabase.insertData(macAddress, serviceUUID, characteristicUUID, property, value);
    }

    /**
     * Some routines section
     */

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

    private void broadcastUpdate(String message) {
        final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
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
