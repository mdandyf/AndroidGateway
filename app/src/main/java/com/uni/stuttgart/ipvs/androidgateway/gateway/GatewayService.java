package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;
import android.util.Xml;

import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.uni.stuttgart.ipvs.androidgateway.MainActivity;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.ScannerCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeGatt;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.BluetoothLeGattCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.CharacteristicsDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.ServicesDatabase;
import com.uni.stuttgart.ipvs.androidgateway.gateway.callback.GatewayCallback;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataLookUp;
import com.uni.stuttgart.ipvs.androidgateway.thread.EExecutionType;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;

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
    public static final String MFG_CHOICE_SERVICE =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.MFG_CHOICE_SERVICE";
    public static final String DISCONNECT_COMMAND =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.DISCONNECT_COMMAND";
    public static final String FINISH_READ =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.FINISH_READ";
    public static final String START_NEW_CYCLE =
            "com.uni-stuttgart.ipvs.androidgateway.gateway.START_NEW_CYCLE";

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
    private List<BluetoothDevice> scanResultsNV;
    private List<BluetoothGatt> listBluetoothGatt;
    private Map<BluetoothGatt, BluetoothLeGattCallback> mapGattCallback;
    private BluetoothGatt mBluetoothGatt;

    private HandlerThread mThread;
    private Handler mHandlerMessage;
    private ExecutionTask<PBluetoothGatt> executionTask;

    private boolean mProcessing;
    private boolean mScanning;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private PowerEstimator powerEstimator;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);
    private ServicesDatabase bleServicesDatabase = new ServicesDatabase(this);
    private CharacteristicsDatabase bleCharacteristicDatabase = new CharacteristicsDatabase(this);

    private String status;
    private Document xmlDocument;
    private XmlPullParser mfrParser;
    private List<PManufacturer> manufacturers;

    @Override
    public void onCreate() {
        super.onCreate();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        queueScanning = new ConcurrentLinkedQueue();
        queueConnecting = new ConcurrentLinkedQueue();
        queueCharacteristic = new ConcurrentLinkedQueue();
        bleDevice = new BluetoothLeDevice();
        bleGatt = new BluetoothLeGatt();
        lock = new Object();
        powerEstimator = new PowerEstimator(this);

        listBluetoothGatt = new ArrayList<>();
        mapGattCallback = new HashMap<>();
        scanResults = new ArrayList<>();
        scanResultsNV = new ArrayList<>();

        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N * 2);
        executionTask.setExecutionType(EExecutionType.MULTI_THREAD_POOL);

        try {
            xmlDocument = GattDataHelper.parseXML(new InputSource(getAssets().open("Settings.xml")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Get Manufacturers List from XML File
        try {
            mfrParser = GattDataHelper.parseXML(getAssets().open("data.xml"));
            manufacturers = GattDataHelper.processParsing(mfrParser);
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


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

        private int cycleCounter = 0;
        private Map<String, double[]> powerConstraint = new HashMap<>();
        private Map<String, Integer> timerSettings = new HashMap<>();
        private String timeUnit = null;

        @Override
        public int getPid() throws RemoteException {
            return Process.myPid();
        }

        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException {
        }

        @Override
        public int getNumberRunningTasks() throws RemoteException {
            return executionTask.getNumberOfTasks();
        }

        @Override
        public int getNumberOfThreads() throws RemoteException {
            return executionTask.getNumberOfThreads();
        }

        @Override
        public int getNumberOfProcessor() throws RemoteException {
            return executionTask.getAvailableProcessor();
        }

        @Override
        public void setCycleCounter(int cycleCounter) throws RemoteException {
            this.cycleCounter = cycleCounter;
        }

        @Override
        public int getCycleCounter() throws RemoteException {
            return this.cycleCounter;
        }

        @Override
        public String getCurrentStatus() throws RemoteException {
            return status;
        }

        @Override
        public void setHandler(PMessageHandler messageHandler, String threadName, String type) throws RemoteException {

           /* if (mHandlerMessage == null) {
                // no quit section
            } else {
                mThread.interrupt();
                mThread.quit();
            }*/
            mThread = new HandlerThread(threadName);
            mThread.start();

            if (type.equals("Gateway")) {
                GatewayCallback gatewayCallback = new GatewayCallback(context, mProcessing, mBinder);
                mHandlerMessage = new Handler(mThread.getLooper(), gatewayCallback);
                gatewayCallback.setmHandlerMessage(mHandlerMessage);
            } else {
                ScannerCallback scannerCallback = new ScannerCallback(context, mProcessing, mBinder);
                mHandlerMessage = new Handler(mThread.getLooper(), scannerCallback);
            }

            mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
            if (mBluetoothGattCallback != null) {
                mBluetoothGattCallback.setHandlerMessage(mHandlerMessage);
            }
        }

        @Override
        public PMessageHandler getMessageHandler() throws RemoteException {
            PMessageHandler parcelMessageHandler = new PMessageHandler();
            parcelMessageHandler.setHandlerMessage(mHandlerMessage);
            return parcelMessageHandler;
        }

        @Override
        public PHandlerThread getHandlerThread() throws RemoteException {
            PHandlerThread handlerThread = new PHandlerThread();
            handlerThread.setHandlerThread(mThread);
            return handlerThread;
        }

        @Override
        public void setProcessing(boolean processing) throws RemoteException {
            mProcessing = processing;
        }

        @Override
        public boolean getScanState() throws RemoteException {
            return mScanning;
        }

        @Override
        public void setScanResult(List<BluetoothDevice> scanResult) throws RemoteException {
            scanResults = scanResult;
        }

        @Override
        public void setScanResultNonVolatile(List<BluetoothDevice> scanResult) throws RemoteException {
                scanResultsNV = scanResult;
        }

        @Override
        public List<BluetoothDevice> getScanResults() throws RemoteException {
            // only known devices that will be processed
            if (scanResults.size() > 0) {
                for (BluetoothDevice device : new ArrayList<BluetoothDevice>(scanResults)) {
                    if (!isDeviceManufacturerKnown(device.getAddress())) {
                        updateDatabaseDeviceState(device, "inactive");
                        scanResults.remove(device);
                    }
                }
            }

            return scanResults;
        }

        @Override
        public List<BluetoothDevice> getScanResultsNonVolatile() throws RemoteException {
            return scanResultsNV;
        }

        @Override
        public void setCurrentGatt(PBluetoothGatt gatt) throws RemoteException {
            mBluetoothGatt = gatt.getGatt();
        }

        @Override
        public PBluetoothGatt getCurrentGatt() throws RemoteException {
            PBluetoothGatt pBluetoothGatt = new PBluetoothGatt();
            pBluetoothGatt.setGatt(mBluetoothGatt);
            return pBluetoothGatt;
        }

        @Override
        public void setListGatt(List<PBluetoothGatt> listGatt) throws RemoteException {
            if (listGatt.size() > 0) {
                listBluetoothGatt = new ArrayList<>();
                for (PBluetoothGatt parcelBluetoothGatt : listGatt) {
                    listBluetoothGatt.add(parcelBluetoothGatt.getGatt());
                }
            }
        }

        /*@Override
        public void addQueueScanning(String macAddress, String name, int rssi, int typeCommand, ParcelUuid serviceUUID, long waitTime) throws RemoteException {
            synchronized (queueScanning) {

                UUID uuidService = null;
                if (serviceUUID != null) {
                    uuidService = serviceUUID.getUuid();
                }
                bleDevice = new BluetoothLeDevice(macAddress, name, rssi, typeCommand, uuidService, waitTime);
                queueScanning.add(bleDevice);
            }

        }*/

        /*@Override
        public void execScanningQueue() throws RemoteException {
            status = "Scanning";
            if (queueScanning != null && !queueScanning.isEmpty() && mProcessing) {
                for (bleDevice = (BluetoothLeDevice) queueScanning.poll(); bleDevice != null; bleDevice = (BluetoothLeDevice) queueScanning.poll()) {
                    synchronized (queueScanning) {
                        if(bleDevice != null) {
                            int type = bleDevice.getType();
                            if (type == BluetoothLeDevice.SCANNING) {
                                if(!mScanning) {
                                    //step scan new BLE devices
                                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing scanning method");
                                    mScanning = true;
                                    broadcastUpdate("Scanning bluetooth...");
                                    Log.d(TAG, "Start scanning");
                                    mBluetoothLeScanProcess.scanLeDevice(true);
                                    mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                }
                            } else if (type == BluetoothLeDevice.FIND_LE_DEVICE) {
                                // step scan for known BLE devices
                                Log.d(TAG, "Start scanning for known BLE device ");
                                Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing find LE device method");
                                if (bleDevice.getMacAddress() != null) {
                                    // find specific macAddress
                                    mScanning = false;
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
                                    if(!mScanning) {
                                        mScanning = true;
                                        UUID[] listBle = new UUID[1];
                                        listBle[0] = bleDevice.getServiceUUID();
                                        mBluetoothLeScanProcess.findLeDevice(listBle, true);
                                        mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                                        sleepThread(1000);
                                        mBluetoothLeScanProcess.findLeDevice(listBle, false);
                                    }
                                }
                            } else if (type == BluetoothLeDevice.STOP_SCANNING) {
                                if(mScanning) {
                                    mScanning = false;
                                    mBluetoothLeScanProcess.scanLeDevice(false);
                                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing stop scanning method");
                                    broadcastUpdate("Stop scanning bluetooth...");
                                    broadcastUpdate("Found " + getScanResults().size() + " matched device(s)");
                                }
                            } else if (type == BluetoothLeDevice.STOP_SCAN) {
                                if (mScanning) {
                                    mScanning = false;
                                    mBluetoothLeScanProcess.scanLeDevice(false);
                                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing stop scan method");
                                    broadcastUpdate("Stop scanning...");
                                }
                            } else if (type == BluetoothLeDevice.WAIT_THREAD) {
                                sleepThread(bleDevice.getWaitTime());
                            }

                        }
                    }
                }
            }
        }*/

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
        public PBluetoothGatt doConnecting(final String macAddress) throws RemoteException {
            status = "Connecting";
            final BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(macAddress);

            broadcastUpdate("\n");
            broadcastUpdate("connecting to " + device.getAddress());

            Log.d("Fragment", "fragment thread = " + Thread.currentThread().getName());

            PBluetoothGatt pBluetoothGatt = new PBluetoothGatt();

            Callable<PBluetoothGatt> callable = new Callable<PBluetoothGatt>() {
                @Override
                public PBluetoothGatt call() throws Exception {
                    BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                    gattCallback.setHandlerMessage(mHandlerMessage);
                    mBluetoothGatt = gattCallback.connect();

                    PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                    parcelBluetoothGatt.setGatt(mBluetoothGatt);

                    Log.d(TAG, "connect to " + mBluetoothGatt.getDevice().getAddress());
                    return parcelBluetoothGatt;
                }
            };

            try {
                pBluetoothGatt = executionTask.submitCallable(callable).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            return pBluetoothGatt;
        }

        @Override
        public void doConnected(PBluetoothGatt gatt) {
            mBluetoothGatt = gatt.getGatt();
            status = "Connected";
            synchronized (lock) {
                synchronized (mBluetoothGatt) {
                    try {
                        broadcastUpdate("connected to " + mBluetoothGatt.getDevice().getAddress());
                        broadcastUpdate("discovering services...");
                        status = "Discovering";
                        mBluetoothGatt.discoverServices();
                        lock.notifyAll();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void doDisconnected(PBluetoothGatt gatt, String type) throws RemoteException {
            mBluetoothGatt = gatt.getGatt();
            status = "Disconnected";
            synchronized (lock) {
                synchronized (mBluetoothGatt) {
                    if ((type.equals("GatewayService")) || (type.equals("ScannerCallback"))) {
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
        public BluetoothDevice getDevice(String macAddress) throws RemoteException {
            if (scanResults != null && scanResults.size() > 0) {
                List<BluetoothDevice> devices = null;
                try {
                    devices = mBinder.getScanResults();
                    for (BluetoothDevice device : devices) {
                        if (device.getAddress().equals(macAddress)) {
                            return device;
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        public void initializeDatabase() throws RemoteException {
            bleDeviceDatabase.deleteAllData();
            bleServicesDatabase.deleteAllData();
            bleCharacteristicDatabase.deleteAllData();
        }

        @Override
        public void insertDatabaseDevice(BluetoothDevice device, int rssi, String deviceState) throws RemoteException {
            broadcastUpdate("Write device " + device.getAddress() + " to database");
            String deviceName = "unknown";
            if (device.getName() != null) {
                deviceName = device.getName();
            }
            bleDeviceDatabase.insertData(device.getAddress(), deviceName, rssi, deviceState);
        }

        @Override
        public void updateDatabaseDevice(BluetoothDevice device, int rssi, byte[] scanRecord) throws RemoteException {
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

        @Override
        public boolean updateDatabaseService(String macAddress, String serviceUUID) throws RemoteException {
            return bleServicesDatabase.insertData(macAddress, serviceUUID);
        }

        @Override
        public boolean updateDatabaseCharacteristics(String macAddress, String serviceUUID, String characteristicUUID, String property, String value) throws RemoteException {
            return bleCharacteristicDatabase.insertData(macAddress, serviceUUID, characteristicUUID, property, value);
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
        public void updateDatabaseDevicePowerUsage(String macAddress, long powerUsage) throws RemoteException {
            try {
                bleDeviceDatabase.updateDevicePowerUsage(macAddress, powerUsage);
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
        public boolean isDeviceManufacturerKnown(String macAddress) throws RemoteException {

            boolean deviceKnown;

            //Get Device Advertisement
            byte[] scanRecord = bleDeviceDatabase.getDeviceScanRecord(macAddress);
            List<ADStructure> structures = AdRecordHelper.decodeAdvertisement(scanRecord);

            if (structures != null && structures.size() > 0) {
                Map<String, Object> mapListAdvertisement = AdRecordHelper.parseAdvertisement(structures);

                //Check for mfrID
                if (mapListAdvertisement.containsKey("CompanyId")) {

                    int compId = (int) mapListAdvertisement.get("CompanyId");
                    String mfrId = GattDataHelper.decToHex(compId);
                    mfrId = mfrId.substring(4, 8);
                    mfrId = "0x" + mfrId;
                    Log.d(TAG, "Company Id: " + mfrId);

                    deviceKnown = checkManufacturer(mfrId);

                    //Device mfrId is Known, then check for serviceUUID
                    if (deviceKnown) {

                        //Check Service if known
                        if (mapListAdvertisement.containsKey("DeviceUUID")) {

                            UUID[] arrayUUIDs = (UUID[]) mapListAdvertisement.get("DeviceUUID");

                            for (UUID uuid : arrayUUIDs) {
                                Log.d(TAG, "Device UUID: " + uuid.toString());
                                deviceKnown = checkManufacturerService(mfrId, uuid.toString());
                                Log.d(TAG, "DeviceKnown: " + deviceKnown);
                            }
                        } else {
                            // service is not known
                            return false;
                        }
                    } else {
                        //device not known
                        return false;
                    }
                } else {
                    // if device has no manufacturer id
                    return false;
                }
            } else {
                // if device has no scan record
                return false;
            }

            return deviceKnown;
        }

        @Override
        public String getDeviceName(String macAddress) throws RemoteException {
            return bleDeviceDatabase.getDeviceName(macAddress);
        }

        @Override
        public String getDeviceState(String macAddress) throws RemoteException {
            return bleDeviceDatabase.getDeviceState(macAddress);
        }

        @Override
        public long getDevicePowerUsage(String macAddress) throws RemoteException {
            return bleDeviceDatabase.getDevicePowerUsage(macAddress);
        }

        @Override
        public boolean checkManufacturer(String mfr_id) throws RemoteException {

            for (int i = 0; i < manufacturers.size(); i++) {
                if (mfr_id.equalsIgnoreCase(manufacturers.get(i).id)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean checkManufacturerService(String mfr_id, String serviceUUID) throws RemoteException {

            for (int i = 0; i < manufacturers.size(); i++) {
                if (mfr_id.equalsIgnoreCase(manufacturers.get(i).id)) {
                    if (serviceUUID.equalsIgnoreCase(manufacturers.get(i).service)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public List<PManufacturer> getListManufacturers() throws RemoteException {
            return manufacturers;
        }

        @Override
        public void setPowerUsageConstraints(String dataName, double[] data) throws RemoteException {
            if (powerConstraint.size() > 3) {
                powerConstraint = new HashMap<>();
            } else {
                powerConstraint.put(dataName, data);
            }
        }

        @Override
        public double[] getPowerUsageConstraints(double batteryLevel) throws RemoteException {
            int currentLevel = (int) batteryLevel;
            double[] powerUsageConstraints = new double[3];

            for (Map.Entry entry : powerConstraint.entrySet()) {
                String key = (String) entry.getKey();
                double[] data = (double[]) entry.getValue();

                String batLevelString = key.substring(0, key.indexOf(","));
                String batLevelUpString = key.substring(key.indexOf(",") + 1);

                int batLevel = Integer.valueOf(batLevelString);
                int batLevelUp = Integer.valueOf(batLevelUpString);

                if ((currentLevel > batLevel) && (currentLevel <= batLevelUp)) {
                    powerUsageConstraints = data;
                    break;
                }
            }

            return powerUsageConstraints;
        }

        @Override
        public void setTimeSettings(String dataName, int data) throws RemoteException {
            if (timerSettings.size() > 4) {
                timerSettings = new HashMap<>();
            } else {
                timerSettings.put(dataName, data);
            }
        }

        @Override
        public int getTimeSettings(String type) throws RemoteException {
            return timerSettings.get(type);
        }

        @Override
        public void setTimeUnit(String unit) throws RemoteException {
            timeUnit = unit;
        }

        @Override
        public String getTimeUnit() throws RemoteException {
            return timeUnit;
        }

        @Override
        public List<ParcelUuid> getServiceUUIDs(String macAddress) throws RemoteException {
            return bleServicesDatabase.getServiceUUIDs(macAddress);
        }

        @Override
        public List<ParcelUuid> getCharacteristicUUIDs(String macAddress, String serviceUUID) throws RemoteException {
            return bleCharacteristicDatabase.getCharacteristicUUIDs(macAddress, serviceUUID);
        }

        @Override
        public String getCharacteristicProperty(String macAddress, String serviceUUID, String CharacteristicUUID) throws RemoteException {
            return bleCharacteristicDatabase.getCharacteristicProperty(macAddress, serviceUUID, CharacteristicUUID);
        }

        @Override
        public String getCharacteristicValue(String macAddress, String serviceUUID, String characteristicUUID) throws RemoteException {
            return bleCharacteristicDatabase.getCharacteristicValue(macAddress, serviceUUID, characteristicUUID);
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

        @Override
        public void broadcastUpdate(String message) throws RemoteException {
            if (mProcessing) {
                final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
                intent.putExtra("command", message);
                sendBroadcast(intent);
            }
        }

        @Override
        public void broadcastCommand(String message, String action) throws RemoteException {
            if (mProcessing) {
                final Intent intent = new Intent(action);
                intent.putExtra("command", message);
                sendBroadcast(intent);
            }
        }

        @Override
        public void broadcastServiceInterface(String message) throws RemoteException {
            if (mProcessing) {
                final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
                intent.putExtra("message", message);
                context.sendBroadcast(intent);
            }
        }

        @Override
        public void broadcastClearScreen(String message) throws RemoteException {
            if (mProcessing) {
                final Intent intent = new Intent(GatewayService.START_NEW_CYCLE);
                context.sendBroadcast(intent);
            }
        }

        @Override
        public void startPowerEstimator() throws RemoteException {
            powerEstimator.start();
        }

        @Override
        public void stopPowerEstimator() throws RemoteException {
            powerEstimator.stop();
        }

        @Override
        public long getPowerEstimatorData(String type) throws RemoteException {
            long result = 0;
            switch (type) {
                case "currentAvg":
                    result = powerEstimator.getCurrentAvg();
                    break;
                case "currentNow":
                    result = powerEstimator.getCurrentNow();
                    break;
                case "batteryPercent":
                    result = powerEstimator.getBatteryPercentage();
                    break;
                case "batteryRemaining":
                    result = powerEstimator.getBatteryRemaining();
                    break;
                case "batteryRemainingEnergy":
                    result = powerEstimator.getBatteryRemainingEnergy();
                    break;
                case "batteryRemainingPercent":
                    result = powerEstimator.getBatteryRemainingPercent();
                case "voltageAvg":
                    result = powerEstimator.getVoltageAvg();
                    break;
                case "voltageNow":
                    result = powerEstimator.getVoltageNow();
                    break;
            }
            return result;
        }

        @Override
        public void startScan(long time) throws RemoteException {

            synchronized (this){
                if(!mScanning) {
                    //step scan new BLE devices
                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing scanning method");
                    mScanning = true;
                    broadcastUpdate("Scanning bluetooth...");
                    Log.d(TAG, "Start scanning");
                    mBluetoothLeScanProcess.scanLeDevice(true);
                    mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                    try {
                        Thread.sleep(time);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void stopScanning() throws RemoteException {
            synchronized (this) {
                if (mScanning) {
                    mScanning = false;
                    mBluetoothLeScanProcess.scanLeDevice(false);
                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing stop scanning method");
                    broadcastUpdate("Stop scanning bluetooth...");
                    broadcastUpdate("Found " + getScanResults().size() + " matched device(s)");
                }
            }
        }

        @Override
        public void stopScan() throws RemoteException {
            synchronized (this) {
                if (mScanning) {
                    mScanning = false;
                    mBluetoothLeScanProcess.scanLeDevice(false);
                    Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing stop scan method");
                    broadcastUpdate("Stop scanning...");
                }
            }
        }

        @Override
        public void startScanKnownDevices(String macAddress) throws RemoteException {
            synchronized (this) {
                // step scan for known BLE devices
                Log.d(TAG, "Start scanning for known BLE device ");
                Log.d(TAG, "Thread " + Thread.currentThread().getId() + " firing find LE device method");
                if (macAddress != null) {
                    // find specific macAddress
                    mScanning = false;
                    broadcastUpdate("Searching device " + macAddress);
                    BluetoothDevice device = mBluetoothLeScanProcess.getRemoteDevice(macAddress);
                    if (device == null) {
                        broadcastUpdate("Device " + macAddress + "not found, try scanning...");
                        mBluetoothLeScanProcess.findLeDevice(macAddress, true);
                        mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
                        sleepThread(500);
                        mBluetoothLeScanProcess.findLeDevice(macAddress, false);
                    } else {
                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 7, 0, device));
                    }

                }
            }
        }
    };

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
        mScanning = false;
        disconnectGatt();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        mThread.quit();
        stopService(mIntent);
        stopSelf();
    }

    private void disconnectGatt() {
        for (BluetoothGatt gatt : listBluetoothGatt) {
            gatt.disconnect();
            gatt.close();
        }
    }

}
