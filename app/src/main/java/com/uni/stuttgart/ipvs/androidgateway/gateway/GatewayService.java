package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeGatt;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.BluetoothLeGattCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.CharacteristicsDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.ServicesDatabase;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataLookUp;

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
    public static final String START_SERVICE_INTERFACE =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.START_SERVICE_INTERFACE";

    private Intent mIntent;

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
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

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
        return START_STICKY;
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
        setWakeLock();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        return false;
    }

    /**
     * method used for binding GatewayService to many processes outside this class
     */

    private final IGatewayService.Stub mBinder = new IGatewayService.Stub() {
        @Override
        public int getPid() throws RemoteException {
            return Process.myPid();
        }

        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException {
        }

        @Override
        public String getCurrentStatus() throws RemoteException {
            return status;
        }

        @Override
        public void setMessageHandler(PMessageHandler messageHandler) throws RemoteException {
            Handler handler = messageHandler.getHandlerMessage();
            if (handler != null) {
                mHandlerMessage = handler;
            }
        }

        @Override
        public PMessageHandler getMessageHandler() throws RemoteException {
            PMessageHandler parcelMessageHandler = new PMessageHandler();
            parcelMessageHandler.setHandlerMessage(mHandlerMessage);
            return parcelMessageHandler;
        }

        @Override
        public void setProcessing(boolean processing) throws RemoteException {
            mProcessing = processing;
        }

        @Override
        public List<BluetoothDevice> getScanResults() throws RemoteException {
            return scanResults;
        }

        @Override
        public PBluetoothGatt getCurrentGatt() throws RemoteException {
            PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
            parcelBluetoothGatt.setGatt(mBluetoothGatt);
            return parcelBluetoothGatt;
        }

        @Override
        public void addQueueScanning(String macAddress, String name, int rssi, int typeCommand, ParcelUuid serviceUUID) throws RemoteException {
            UUID uuidService = null;
            if (serviceUUID != null) {
                uuidService = serviceUUID.getUuid();
            }
            bleDevice = new BluetoothLeDevice(macAddress, name, rssi, typeCommand, uuidService);
            queueScanning.add(bleDevice);
        }

        @Override
        public void execScanningQueue() throws RemoteException {
            status = "Scanning";
            if (queueScanning != null && !queueScanning.isEmpty() && mProcessing) {
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
                            // step scan for known BLE devices
                            Log.d(TAG, "Start scanning for known BLE device ");
                            if (bleDevice.getMacAddress() != null) {
                                // scan using specific macAddress
                                broadcastUpdate("Searching device " + bleDevice.getMacAddress());
                                BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(bleDevice.getMacAddress());
                                if (device == null) {
                                    broadcastUpdate("Device " + bleDevice.getMacAddress() + "not found, try scanning...");
                                    mBluetoothLeScanProcess.findLeDevice(bleDevice.getMacAddress(), true);
                                    mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                    sleepThread(500);
                                    mBluetoothLeScanProcess.findLeDevice(bleDevice.getMacAddress(), false);
                                } else {
                                    mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 7, 0, device));
                                }
                            } else if (bleDevice.getServiceUUID() != null) {
                                // scan using specific service
                                UUID[] listBle = new UUID[1];
                                listBle[0] = bleDevice.getServiceUUID();
                                mBluetoothLeScanProcess.findLeDevice(listBle, true);
                                mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                sleepThread(1000);
                                mBluetoothLeScanProcess.findLeDevice(listBle, false);
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

        @Override
        public void doConnect(String macAddress) throws RemoteException {
            status = "Connecting";
            final BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(macAddress);
            synchronized (lock) {
                broadcastUpdate("\n");
                broadcastUpdate("connecting to " + device.getAddress());
                BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                gattCallback.setHandlerMessage(mHandlerMessage);
                mBluetoothGatt = gattCallback.connect();
                Log.d(TAG, "connect to " + mBluetoothGatt.getDevice().getAddress() + " on " + mBinder.getPid());
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void doConnecting(String macAddress) throws RemoteException {
            status = "Connecting";
            final BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(macAddress);
            synchronized (device) {
                BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                gattCallback.setHandlerMessage(mHandlerMessage);
                mBluetoothGatt = gattCallback.connect();
                Log.d(TAG, "connect to " + mBluetoothGatt.getDevice().getAddress() + " on " + mBinder.getPid());
            }
        }

        @Override
        public void doConnected(PBluetoothGatt gatt) {
            synchronized (lock) {
                status = "Connected";
                mBluetoothGatt = gatt.getGatt();
                broadcastUpdate("connected to " + mBluetoothGatt.getDevice().getAddress());
                broadcastUpdate("discovering services...");
                status = "Discovering";
                mBluetoothGatt.discoverServices();
                lock.notifyAll();
            }
        }

        @Override
        public void doDisconnected(PBluetoothGatt gatt, String type) throws RemoteException {
            mBluetoothGatt = gatt.getGatt();
            synchronized (lock) {
                if (type.equals("GatewayService")) {
                    status = "Disconnected";
                    broadcastUpdate("Disconnected from " + mBluetoothGatt.getDevice().getAddress());
                    lock.notifyAll();
                } else {
                    try {
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        @Override
        public void addQueueCharacteristic(PBluetoothGatt gatt, ParcelUuid serviceUUID, ParcelUuid characteristicUUID, ParcelUuid descriptorUUID, byte[] data, int typeCommand) throws RemoteException {
            mBluetoothGatt = gatt.getGatt();
            UUID uuidService = null;
            UUID uuidCharacteristic = null;
            UUID uuidDescriptor = null;
            if (serviceUUID != null) {
                uuidService = serviceUUID.getUuid();
            }
            if (characteristicUUID != null) {
                uuidCharacteristic = characteristicUUID.getUuid();
            }
            if (descriptorUUID != null) {
                uuidDescriptor = descriptorUUID.getUuid();
            }
            bleGatt = new BluetoothLeGatt(mBluetoothGatt, uuidService, uuidCharacteristic, uuidDescriptor, data, typeCommand);
            queueCharacteristic.add(bleGatt);
        }

        @Override
        public void execCharacteristicQueue() throws RemoteException {
            status = "Reading";
            broadcastUpdate("\n");
            if (queueCharacteristic != null && !queueCharacteristic.isEmpty() && mProcessing) {
                for (bleGatt = (BluetoothLeGatt) queueCharacteristic.poll(); bleGatt != null; bleGatt = (BluetoothLeGatt) queueCharacteristic.poll()) {
                    synchronized (bleGatt) {
                        int type = bleGatt.getTypeCommand();
                        if (type == BluetoothLeGatt.READ) {
                            mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                            broadcastUpdate("Reading Characteristic " + GattDataLookUp.characteristicNameLookup(bleGatt.getCharacteristicUUID()));
                            mBluetoothGattCallback.readCharacteristic(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID());
                        } else if (type == BluetoothLeGatt.REGISTER_NOTIFY) {
                            mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                            broadcastUpdate("Registering Notify Characteristic " + bleGatt.getCharacteristicUUID().toString());
                            mBluetoothGattCallback.writeDescriptorNotify(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID(), bleGatt.getDescriptorUUID());
                        } else if (type == BluetoothLeGatt.REGISTER_INDICATE) {
                            mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                            broadcastUpdate("Registering Indicate Characteristic " + bleGatt.getCharacteristicUUID().toString());
                            mBluetoothGattCallback.writeDescriptorIndication(bleGatt.getServiceUUID(), bleGatt.getCharacteristicUUID(), bleGatt.getDescriptorUUID());
                        } else if (type == BluetoothLeGatt.WRITE) {
                            mBluetoothGattCallback = new BluetoothLeGattCallback(bleGatt.getGatt());
                        }

                    }
                }

            }
        }

        @Override
        public void updateDatabaseDeviceState(BluetoothDevice device, String deviceState) throws RemoteException {
            try {
                bleDeviceDatabase.updateDeviceState(device.getAddress(), deviceState);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void updateDatabaseDeviceAdvRecord(BluetoothDevice device, byte[] scanRecord) throws RemoteException {
            try {
                bleDeviceDatabase.updateDeviceAdvData(device.getAddress(), scanRecord);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void updateAllDeviceStates(List<String> nearbyDevices) throws RemoteException {
            broadcastUpdate("\n");
            broadcastUpdate("Refresh all device states...");
            try {
                bleDeviceDatabase.updateAllDevicesState(nearbyDevices, "active");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean checkDevice(String macAddress) throws RemoteException {
            if (macAddress == null) {
                return bleDeviceDatabase.isDeviceExist();
            }
            return bleDeviceDatabase.isDeviceExist(macAddress);
        }

        @Override
        public List<String> getListDevices() throws RemoteException {
            return bleDeviceDatabase.getListDevices();
        }

        @Override
        public List<String> getListActiveDevices() throws RemoteException {
            return bleDeviceDatabase.getListActiveDevices();
        }

        @Override
        public int getDeviceRSSI(String macAddress) throws RemoteException {
            return bleDeviceDatabase.getDeviceRssi(macAddress);
        }

        @Override
        public byte[] getDeviceScanRecord(String macAddress) throws RemoteException {
            return bleDeviceDatabase.getDeviceScanRecord(macAddress);
        }

        @Override
        public List<ParcelUuid> getServiceUUIDs(String macAddress) throws RemoteException {
            return bleServicesDatabase.getServiceUUIDs(macAddress);
        }

        @Override
        public List<ParcelUuid> getCharacteristicUUIDs(String macAddress) throws RemoteException {
            return null;
        }

        @Override
        public void disconnectSpecificGatt(String macAddress) throws RemoteException {
            for (BluetoothGatt gatt : listBluetoothGatt) {
                if (gatt.getDevice().getAddress().equals(macAddress)) {
                    gatt.disconnect();
                    gatt.close();
                }
            }
        }
    };

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
                                insertDatabaseDevice(result.getDevice(), result.getRssi(), "active");
                            } else {
                                updateDatabaseDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                            }
                        }
                    } else if (msg.arg1 == 4) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            List<ScanResult> results = ((List<ScanResult>) msg.obj);
                            for (ScanResult result : results) {
                                if (!scanResults.contains(result.getDevice())) {
                                    scanResults.add(result.getDevice());
                                    insertDatabaseDevice(result.getDevice(), result.getRssi(), "active");
                                } else {
                                    updateDatabaseDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                                }
                            }
                        }
                    } else if (msg.arg1 == 5) {
                        final Map<BluetoothDevice, GattDataJson> mapDevice = ((Map<BluetoothDevice, GattDataJson>) msg.obj);
                        for (Map.Entry entry : mapDevice.entrySet()) {
                            BluetoothDevice device = (BluetoothDevice) entry.getKey();
                            if (!scanResults.contains(device)) {
                                scanResults.add(device);
                                insertDatabaseDevice(device, (GattDataJson) entry.getValue(), "active");
                            } else {
                                updateDatabaseDevice(device, (GattDataJson) entry.getValue(), null);
                            }
                        }
                    } else if (msg.arg1 == 7) {
                        final BluetoothDevice device = (BluetoothDevice) msg.obj;
                        if (!scanResults.contains(device)) {
                            scanResults.add(device);
                        }
                    } else if (msg.arg1 == 10) {
                        final Map<BluetoothDevice, byte[]> mapScanRecord = ((Map<BluetoothDevice, byte[]>) msg.obj);
                        for (Map.Entry entry : mapScanRecord.entrySet()) {
                            BluetoothDevice device = (BluetoothDevice) entry.getKey();
                            if (scanResults.contains(device)) {
                                mBinder.updateDatabaseDeviceAdvRecord(device, (byte[]) entry.getValue());
                            }
                        }
                    }

                } else if (msg.what == 1) {

                    //getting results from connecting
                    if (msg.arg1 == 0) {
                        // read all bluetoothGatt servers
                        mBluetoothGatt = (BluetoothGatt) msg.obj;
                        if (mBluetoothGatt != null && !listBluetoothGatt.contains(mBluetoothGatt)) {
                            listBluetoothGatt.add(mBluetoothGatt);
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
                            mBinder.execCharacteristicQueue();
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
            broadcastUpdate("\n");
            boolean databaseService = false;
            boolean databaseCharacteristic = false;
            GattDataJson json = null;
            Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
            for (Map.Entry entry : mapGatt.entrySet()) {
                mBluetoothGatt = (BluetoothGatt) entry.getKey();
                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                json = new GattDataJson(mBluetoothGatt.getDevice(), mBluetoothGatt);
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
                broadcastUpdate("Characteristic: " + GattDataLookUp.characteristicNameLookup(characteristic.getUuid()));
                broadcastUpdate("UUID: " + characteristic.getUuid().toString());
                broadcastUpdate("Property: " + properties);
                broadcastUpdate("Value: " + characteristicValue);
                databaseService = updateDatabaseService(mBluetoothGatt.getDevice().getAddress(), characteristic.getService().getUuid().toString());
                databaseCharacteristic = updateDatabaseCharacteristics(mBluetoothGatt.getDevice().getAddress(), characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), properties, characteristicValue);


                if (databaseService) {
                    broadcastUpdate("Services have been written to database");
                }
                if (databaseCharacteristic) {
                    broadcastUpdate("Characteristics have been written to database");
                }

                if (databaseService && databaseCharacteristic) {
                    try {
                        mBinder.updateDatabaseDeviceState(mBluetoothGatt.getDevice(), "active");
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }

            return json.getJsonData().toString();
        }
    };


    /*
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

    private void updateDatabaseDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
        String deviceName = "unknown";
        if (device.getName() != null) {
            deviceName = device.getName();
        }
        try {
            bleDeviceDatabase.updateData(device.getAddress(), deviceName, rssi, null, scanRecord);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void updateDatabaseDevice(BluetoothDevice device, GattDataJson data, byte[] scanRecord) {
        String deviceName = "unknown";
        int deviceRssi = 0;
        try {
            deviceRssi = (Integer) data.getJsonAdvertising().get("rssi");
            if (device.getName() != null) {
                deviceName = device.getName();
            }
            bleDeviceDatabase.updateData(device.getAddress(), deviceName, deviceRssi, null, scanRecord);
        } catch (JSONException e) {
            e.printStackTrace();
        }
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

    private void setWakeLock() {
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockService");
        if ((wakeLock != null) && (!wakeLock.isHeld())) {
            wakeLock.acquire();
        }
    }


    public void sleepThread(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void disconnect() {
        mProcessing = false;
        mHandlerMessage.removeCallbacksAndMessages(mHandlerCallback);
        disconnectGatt();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopService(mIntent);
        stopSelf();
    }

    private void disconnectGatt() {
        for (BluetoothGatt gatt : listBluetoothGatt) {
            gatt.disconnect();
            gatt.close();
        }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            sendBroadcast(intent);
        }
    }

}
