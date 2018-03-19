package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
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
    private Map<BluetoothDevice, GattDataJson> mapScanResults;
    private Map<BluetoothGatt, BluetoothLeGattCallback> mapGattCallback;

    private HandlerThread mThread = new HandlerThread("mThreadCallback");
    private Handler mHandlerMessage;
    private Handler mHandlerScanning;

    @Override
    public void onCreate() {
        super.onCreate();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);
        mHandlerScanning = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = intent;
        context = this;
        listBluetoothGatt = new ArrayList<>();

        initQueue();
        execQueue();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Map.Entry<BluetoothGatt, BluetoothLeGattCallback> entry : mapGattCallback.entrySet()) {
            BluetoothLeGattCallback gattCallback = entry.getValue();
            BluetoothGatt gatt = entry.getKey();
            if (gatt != null) {
                gattCallback.disconnect();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void initQueue() {
        queueCommand(null, null, null, null, BluetoothLe.CHECK_BLUETOOTH_STATE);
        queueCommand(null, null, null, null, BluetoothLe.CHECK_PERMISSION);
        queueCommand(null, null, null, null, BluetoothLe.SCANNING);
        queueCommand(null, null, null, null, BluetoothLe.CONNECTING);
    }

    public boolean execQueue() {
        if (!queue.isEmpty()) {
            for (ble = (BluetoothLe) queue.poll(); ble != null; ble = (BluetoothLe) queue.poll()) {
                synchronized (ble) {
                    boolean status = commandCall(ble);
                    if (!status) {
                        stopSelf();
                    }
                }
            }
        }

        return true;
    }

    public void queueCommand(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data, int commandType) {
        ble = new BluetoothLe(gatt, serviceUUID, characteristicUUID, data, commandType);
        queue.add(ble);
    }

    private boolean commandCall(final BluetoothLe bluetoothLe) {

        int type = bluetoothLe.getTypeCommand();
        boolean processCommandQueue = false;

        if (type == BluetoothLe.CHECK_BLUETOOTH_STATE) {

            broadcastUpdate("Starting sequence commands...");
            broadcastUpdate("Checking bluetooth adapter...");
            if (!checkBluetoothState() || mBluetoothAdapter == null) {
                Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                return false;
            }
            broadcastUpdate("Checking bluetooth adapter done...");


        } else if (type == BluetoothLe.CHECK_PERMISSION) {

            //step checking bluetoothAdapter & permissions
            broadcastUpdate("Checking permissions...");
            if (!checkLocationState()) {
                Toast.makeText(this, "Please turn on location!", Toast.LENGTH_SHORT).show();
                return false;
            }
            broadcastUpdate("Checking permissions done...");


        } else if (type == BluetoothLe.SCANNING) {

            //step scan BLE
            scanResults = new ArrayList<>();
            mapScanResults = new HashMap<>();
            broadcastUpdate("Scanning bluetooth...");
            Log.d(TAG, "Start scanning for " + SCAN_PERIOD + " seconds");
            mBluetoothLeScanProcess.scanLeDevice(true);
            mHandlerScanning.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        mBluetoothLeScanProcess.scanLeDevice(false);
                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, mBluetoothLeScanProcess.getScanResult()));
                        mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, mBluetoothLeScanProcess.getScanProperties()));
                        scanResults = mBluetoothLeScanProcess.getScanResult();
                        mapScanResults = mBluetoothLeScanProcess.getScanProperties();
                        Log.d(TAG, "Stop scanning");
                        broadcastUpdate("Stop scanning bluetooth...");
                        broadcastUpdate("Found " + scanResults.size() + " device(s)");
                        lock.notifyAll();
                    }
                }
            }, SCAN_PERIOD * 1000);


        } else if (type == BluetoothLe.CONNECTING) {

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        try {
                            Log.d(TAG, "Waiting for scanning to be finished");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        mapGattCallback = new HashMap<>();

                        for (BluetoothDevice device : scanResults) {
                            broadcastUpdate("connecting to " + device.getAddress());
                            final BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (lock) {
                                        gattCallback.connect();
                                        mapGattCallback.put(gattCallback.getBluetoothGatt(), gattCallback);
                                        lock.notifyAll();
                                    }
                                }
                            }).start();
                        }

                        synchronized (lock) {
                            try {
                                Log.d(TAG, "Waiting for connecting to be finished");
                                lock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (!mapGattCallback.isEmpty()) {
                                for (Map.Entry<BluetoothGatt, BluetoothLeGattCallback> entry : mapGattCallback.entrySet()) {
                                    BluetoothGatt callbackGatt = entry.getValue().getBluetoothGatt();
                                    listBluetoothGatt.add(callbackGatt);
                                    mBluetoothGattCallback = entry.getValue();

                                    if (callbackGatt != null) {
                                        queueCommand(callbackGatt, null, null, null, BluetoothLe.CONNECTED);
                                    }
                                }
                                execQueue();
                            }
                        }

                    }
                }
            });

            thread.start();


        } else if (type == BluetoothLe.CONNECTED) {
            synchronized (lock) {
                BluetoothGatt gatt = bluetoothLe.getGatt();

                // wait for service to be discovered first
                while (gatt.getServices() == null) {
                    sleepThread(100);
                }
                broadcastUpdate("connected to " + gatt.getDevice().getAddress());
                broadcastUpdate("discovering services...");
                getServiceCharacteristic(gatt);
                lock.notifyAll();
            }
        } else if (type == BluetoothLe.DISCONNECTED) {
            broadcastUpdate("Disconnected from " + bluetoothLe.getGatt().getDevice().getAddress());
        } else if (type == BluetoothLe.READ) {
            broadcastUpdate("Reading Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mBluetoothGattCallback.readCharacteristic(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID());
        } else if (type == BluetoothLe.REGISTER_NOTIFY) {
            broadcastUpdate("Registering Notify Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mBluetoothGattCallback.writeDescriptorNotify(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.REGISTER_INDICATE) {
            broadcastUpdate("Registering Indicate Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mBluetoothGattCallback.writeDescriptorIndication(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.WRITE) {
            //mBluetoothGattCallback.writeCharacteristic()
        }

        return true;
    }

    Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            return false;
        }
    };

    private boolean checkBluetoothState() {

        /** force user to turn on bluetooth */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            turnOn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(turnOn);
        } else {
            // bluetooth is already turned on
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        return true;
    }

    private boolean checkLocationState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** force user to turn on location service */
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Location Access", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        return true;
    }

    private void getServiceCharacteristic(BluetoothGatt gatt) {
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

    private void sleepThread(long time) {
        try {
            Log.d(TAG, "Sleep for " + String.valueOf(time) + " ms");
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void broadcastUpdate(String message) {
        final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }
}
