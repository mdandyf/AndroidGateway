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
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLe;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeGattCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeService;
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
    private boolean isExecCommand = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);
        mHandlerScanning = new Handler();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        listBluetoothGatt = new ArrayList<>();
        mapGattCallback = new HashMap<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = intent;
        context = this;

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
        if (!queue.contains(ble)) {
            queue.add(ble);
        }
    }

    private boolean commandCall(final BluetoothLe bluetoothLe) {

        int type = bluetoothLe.getTypeCommand();

        if(type == BluetoothLe.CHECK_PERMISSION) {
            broadcastUpdate("Starting sequence commands...");
            broadcastUpdate("Checking permissions...");
            if(!checkPermissions()) {
                broadcastTerminate("Please turn on location!");
                return false;
            }
            broadcastUpdate("Checking permissions done...");
        } else if (type == BluetoothLe.SCANNING) {

            //step scan BLE
            broadcastUpdate("Scanning bluetooth...");
            Log.d(TAG, "Start scanning for " + SCAN_PERIOD + " seconds");
            mBluetoothLeScanProcess.scanLeDevice(true);

            mHandlerScanning.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanProcess.scanLeDevice(false);
                    Log.d(TAG, "Stop scanning...");
                    broadcastUpdate("Stop scanning bluetooth...");
                    broadcastUpdate("Found " + mBluetoothLeScanProcess.getScanResult().size() + " device(s)");
                    isExecCommand = true;
                    mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 0, 0, mBluetoothLeScanProcess.getScanResult()));
                    mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 1, 0, mBluetoothLeScanProcess.getScanProperties()));
                    mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 0, 2, 0, isExecCommand));
                }
            }, SCAN_PERIOD * 1000);

        } else if (type == BluetoothLe.CONNECTING) {
            broadcastUpdate("\n");
            final Map<BluetoothGatt, BluetoothLeGattCallback> mapGatt = new HashMap<>();
            for (BluetoothDevice device : scanResults) {
                broadcastUpdate("connecting to " + device.getAddress());
                final BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        gattCallback.setHandlerMessage(mHandlerMessage);
                        gattCallback.connect();
                        mapGatt.put(gattCallback.getBluetoothGatt(), gattCallback);
                    }
                }).start();
            }
            mHandlerMessage.sendMessage(Message.obtain(mHandlerMessage, 1, 9, 0, mapGatt));

        } else if (type == BluetoothLe.CONNECTED) {
            BluetoothGatt gatt = bluetoothLe.getGatt();
            broadcastUpdate("connected to " + gatt.getDevice().getAddress());
            broadcastUpdate("discovering services...");
            gatt.readRemoteRssi();
            gatt.discoverServices();
        } else if (type == BluetoothLe.DISCONNECTED) {
            broadcastUpdate("Disconnected from " + bluetoothLe.getGatt().getDevice().getAddress());
        } else if (type == BluetoothLe.READ) {
            mBluetoothGattCallback = new BluetoothLeGattCallback(bluetoothLe.getGatt());
            try {
                Thread.sleep(100);
                broadcastUpdate("Reading Characteristic " + GattLookUp.characteristicNameLookup(bluetoothLe.getCharacteristicUUID()));
                mBluetoothGattCallback.readCharacteristic(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (type == BluetoothLe.REGISTER_NOTIFY) {
            mBluetoothGattCallback = new BluetoothLeGattCallback(bluetoothLe.getGatt());
            broadcastUpdate("Registering Notify Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mBluetoothGattCallback.writeDescriptorNotify(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.REGISTER_INDICATE) {
            mBluetoothGattCallback = new BluetoothLeGattCallback(bluetoothLe.getGatt());
            broadcastUpdate("Registering Indicate Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mBluetoothGattCallback.writeDescriptorIndication(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.WRITE) {
            mBluetoothGattCallback = new BluetoothLeGattCallback(bluetoothLe.getGatt());
            //broadcastUpdate("\n");
            //broadcastUpdate("writing characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            //mBluetoothGattCallback.writeCharacteristic()
        }

        return true;
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
                        dataJson = new GattDataJson(connectedGatt.getDevice(), connectedGatt);
                        queueCommand(connectedGatt, null, null, null, BluetoothLe.CONNECTED);
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        isExecCommand = true;
                        BluetoothGatt disconnectedGatt = ((BluetoothGatt) msg.obj);
                        dataJson = new GattDataJson(disconnectedGatt.getDevice(), disconnectedGatt);
                        queueCommand(disconnectedGatt, null, null, null, BluetoothLe.DISCONNECTED);
                    } else if (msg.arg1 == 3) {
                        // read all bluetoothGatt rssi
                        isExecCommand = false;
                        dataJson.setRssi((int) msg.obj);
                    } else if (msg.arg1 == 4) {
                        //discovered services and read all characteristics
                        broadcastUpdate("\n");
                        isExecCommand = true;
                        BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                        getServiceCharacteristic(gatt);
                        dataJson.setGatt(gatt);
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        isExecCommand = false;
                        readData(msg);
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        isExecCommand = false;
                        readData(msg);
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        isExecCommand = false;
                        readData(msg);
                    } else if (msg.arg1 == 9) {
                        mapGattCallback.putAll(((Map<BluetoothGatt, BluetoothLeGattCallback>) msg.obj));
                    }
                }

                if (isExecCommand) {
                    execQueue();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private String readData(Message msg) {
            String result = null;
            Map<BluetoothGatt, BluetoothGattCharacteristic> map = new HashMap<>();
            map = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
            for (Map.Entry<BluetoothGatt, BluetoothGattCharacteristic> entry : map.entrySet()) {
                dataJson.setGatt(entry.getKey());
                dataJson.setAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
                result = dataJson.getJsonData().toString();
                String characteristicValue = GattDataHelper.decodeCharacteristicValue(entry.getValue(), entry.getKey());
                broadcastUpdate("Characteristic : " + GattLookUp.characteristicNameLookup(entry.getValue().getUuid()));
                broadcastUpdate("Characteristic read value: " + characteristicValue);
            }
            return result;
        }

        private String shortUUID(String uuid) {
            int begin = 4;
            return uuid.substring(begin, begin + 4);
        }
    };

    private boolean checkPermissions() {
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

    private void broadcastTerminate(String message) {
        final Intent intent = new Intent(GatewayService.TERMINATE_COMMAND);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }
}
