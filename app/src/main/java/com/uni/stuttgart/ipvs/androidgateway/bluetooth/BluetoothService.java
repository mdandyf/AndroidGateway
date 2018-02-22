package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static android.app.Activity.DEFAULT_KEYS_SEARCH_GLOBAL;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

/**
 * Created by mdand on 2/19/2018.
 */

public class BluetoothService extends Service {

    private static final int BLUETOOTH_TIMER = 10; // in seconds
    private static final String TAG = "Bluetooth Service";

    public final static String SCAN_START = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.SCAN_START";
    public final static String SCAN_STOP = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.SCAN_STOP";

    public final static String ACTION_GATT_CONNECTING = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_GATT_CONNECTING";
    public final static String ACTION_GATT_CONNECTED = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICE_DISCOVERED = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_GATT_SERVICE_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITTEN = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.ACTION_DATA_WRITTEN";
    public final static String EXTRA_DATA = "com.uni.stuttgart.ipvs.androidgateway.blueooth.BluetootService.EXTRA_DATA";

    private Bluetooth bluetooth;
    private boolean scanState = false;
    private boolean gattState = false;


    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        setBluetoothServiceStart();
        Toast.makeText(this, "service start", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "start bluetooth service");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        disconnect();
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "stop bluetooth service");
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }


    /** to parse parameter into this class */
    public void setBluetooth(Bluetooth input) {
        this.bluetooth = input;
        if(input == null) {
            setScanState(false);
        }
    }

    /** starting again the service after bluetooth device is turned off */
    public void setBluetoothServiceStart() {
        bluetooth = new BluetoothImpl();
        checkPermissionBle();
        if(getBluetoothState() && getBleDevice() != null) {
            scanBluetooth();
        }
    }

    /**
     * to parse parameter out this class
     */
    public Bluetooth getBluetooth() {
        return bluetooth;
    }

    /**
     * to check whether the scanning is still run or not
     */
    protected boolean getScanState() {
        return scanState;
    }

    protected void setScanState(boolean input) {
        scanState = input;
        if(scanState) {
            broadcastUpdate(SCAN_START);
        } else {
            broadcastUpdate(SCAN_STOP);
        }
    }

    protected  boolean getGattState() {return  gattState;}

    protected boolean getBluetoothState() {
        if ((bluetooth.getBluetoothAdapter() == null) || (!bluetooth.getBluetoothAdapter().isEnabled())) {
            return false;
        }
        return true;
    }

    /**
     * force user to turn on bluetooth
     */
    @SuppressLint("NewApi")
    public void checkPermissionBle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Location Access", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (bluetooth.getBluetoothAdapter() == null || !bluetooth.getBluetoothAdapter().isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            turnOn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            bluetooth.setContext(this);
            bluetooth.getContext().startActivity(turnOn);
            bluetooth.setBluetoothAdapter(BluetoothAdapter.getDefaultAdapter());
        } else {
            bluetooth.setBluetoothAdapter(BluetoothAdapter.getDefaultAdapter());
        }
    }

    public BluetoothLeScanner getBleDevice() {
        bluetooth.setBleScanner(bluetooth.getBluetoothAdapter().getBluetoothLeScanner());
        return bluetooth.getBleScanner();
    }

    public void scanBluetooth() {
        bluetooth.setDeviceCounter(0);
        bluetooth.setTriedDevices(new HashSet<String>());

        List<ScanFilter> scanFilters = new ArrayList<>();


        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        settingsBuilder.setReportDelay(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        }
        ScanSettings scanSettings = settingsBuilder.build();

        /** stop scanning after xx seconds */
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(bluetooth != null) {
                    bluetooth.getBleScanner().stopScan(scanCallback);
                    Log.d(TAG, "stop scanning after "+ BLUETOOTH_TIMER +" seconds");
                    setScanState(false);
                }
            }
        }, (BLUETOOTH_TIMER * 1000));


        Log.d(TAG, "start scanning for " + BLUETOOTH_TIMER + " seconds");
        setScanState(true);
        bluetooth.getBleScanner().startScan(scanFilters, scanSettings, scanCallback);
    }

    public void disconnect() {

        if (scanState && bluetooth.getBleScanner() != null) {
            bluetooth.getBleScanner().stopScan(scanCallback);
            setScanState(false);
        } else {
            // do nothing
        }

        if (gattState & bluetooth.getBluetoothGatt() != null) {
            bluetooth.getBluetoothGatt().disconnect();
        } else {
            // do nothing
        }

        bluetooth = null;
        Log.d(TAG, "disconnect bluetooth");
    }

    private ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            connect(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                connect(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(TAG, "Scanning failed with errorCode " + errorCode);
            Toast.makeText(getApplicationContext(), String.format("Scanning failed (%d)", errorCode), Toast.LENGTH_SHORT).show();
        }

        private void connect(ScanResult result) {
            Log.d(TAG, result.getDevice().getAddress());
            Log.d(TAG, String.valueOf(result.getRssi()));
            if(bluetooth != null) {
                if (bluetooth.getBluetoothGatt() == null) {
                    synchronized (bluetooth.getTriedDevices()) {
                        if (!bluetooth.getTriedDevices().contains(result.getDevice().getAddress())) {
                            bluetooth.getTriedDevices().add(result.getDevice().getAddress());
                            bluetooth.setBluetoothDevice(result);
                            broadcastUpdate(ACTION_GATT_CONNECTING);
                            runThread(result);
                        }
                    }
                }
            }
        }
    };

    private void runThread(final ScanResult result) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                result.getDevice().connectGatt(bluetooth.getContext(), false, new MyBluetoothGattCallback());
            }
        };
        AsyncTask.execute(runnable);
    }

    public class MyBluetoothGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if(bluetooth != null) {
                    bluetooth.setBluetoothGatt(gatt);
                    broadcastUpdate(ACTION_GATT_CONNECTED);
                    gattState = true;
                    gatt.discoverServices();
                }
            } else if(newState == BluetoothGatt.STATE_DISCONNECTED) {

            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bluetooth.setBluetoothService(gatt, gatt.getServices());
                broadcastUpdate(ACTION_GATT_SERVICE_DISCOVERED);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if(bluetooth != null) {
                bluetooth.setBluetoothCharacteristic(gatt, characteristic);
                broadcastUpdate(ACTION_DATA_AVAILABLE);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(bluetooth != null) {
                bluetooth.getBluetoothCharacteristic();
                broadcastUpdate(ACTION_DATA_WRITTEN);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(bluetooth != null) {
                bluetooth.setBluetoothCharacteristic(gatt, characteristic);
                broadcastUpdate(ACTION_DATA_AVAILABLE);
            }
        }

    }


    /** Broadcast an intent with a string representing an action*/
    protected void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action); //Create new intent to broadcast the action
        sendBroadcast(intent); //Broadcast the intent
    }


}
