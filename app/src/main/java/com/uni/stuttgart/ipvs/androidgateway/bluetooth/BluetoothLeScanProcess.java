package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeScanProcess implements BluetoothAdapter.LeScanCallback {

    private static final String TAG = "BLE Scanner";

    private BluetoothAdapter mBluetoothAdapter;
    private android.bluetooth.le.BluetoothLeScanner mBleScanner;
    private Handler mHandler;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private ScanResult scanResult;
    private Context context;
    private boolean mScanning = false;

    private List<BluetoothDevice> listDevices = new ArrayList<>();
    private Map<BluetoothDevice, List<String>> mapProperties = new HashMap<>();

    private static final long SCAN_PERIOD = 10000;

    public void scanLeDevice(boolean enable) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                newScan();
            } else {
                oldScan();
            }
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return this.context;
    }

    public void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {this.mBluetoothAdapter = bluetoothAdapter;}

    public boolean getScanState(){return this.mScanning;}

    public  List<BluetoothDevice> getScanResult() {return this.listDevices;}

    public Map<BluetoothDevice, List<String>> getScanProperties() {return this.mapProperties;}

    /**
     * scan using new Scan method
     */
    private void newScan() {
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
        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBleScanner.stopScan(scanCallback);
                Log.d(TAG, "stop scanning after " + SCAN_PERIOD + " seconds");
            }
        }, (SCAN_PERIOD * 1000));


        Log.d(TAG, "start scanning for " + SCAN_PERIOD + " seconds");
        mScanning = true;
        mBleScanner.startScan(scanFilters, scanSettings, scanCallback);
    }

    /**
     * scan using old scan method
     */
    private void oldScan() {
        // Stops scanning after a pre-defined scan period.
        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }, SCAN_PERIOD);

        mScanning = true;
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    @Override
    public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] bytes) {
        List<String> properties = new ArrayList<>();
       if (!listDevices.contains(bluetoothDevice)) {
            listDevices.add(bluetoothDevice);
        }

        properties.add("rssi : " + String.valueOf(rssi));

        mapProperties.put(bluetoothDevice, properties);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            addBluetoothDevice(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                addBluetoothDevice(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(TAG, "Scanning failed with errorCode " + errorCode);
            Toast.makeText(getContext(), String.format("Scanning failed (%d)", errorCode), Toast.LENGTH_SHORT).show();
        }

        private void addBluetoothDevice(ScanResult result) {
            List<String> properties = new ArrayList<>();
            if (!listDevices.contains(result)) {
                listDevices.add(result.getDevice());
            }

            properties.add("rssi : " + String.valueOf(result.getRssi()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                properties.add("Tx power : " + String.valueOf(result.getTxPower()));
            }

            mapProperties.put(result.getDevice(), properties);
        }
    };

}
