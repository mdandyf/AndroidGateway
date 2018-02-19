package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.BluetoothActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by mdand on 2/17/2018.
 */

public class BluetoothConfigurationManager extends BluetoothActivity {

    protected Set<BluetoothDevice> scanDevices;
    protected BluetoothLeScanner mBluetoothLe;
    protected BluetoothGatt mBluetoothGatt;
    protected HashSet<String> triedDevices;
    protected float deviceCounter;

    public BluetoothConfigurationManager(){}

    public BluetoothAdapter getBluetoothAdapter() {
        if(!checkBluetoothState()) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            Log.d("Ble", "Bluetooth is already on");
        } else {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            Log.d("Ble", "Turning Bluetooth on");
        }
        return mBluetoothAdapter;
    }

    public void getBleDevice() {
        mBluetoothLe = mBluetoothAdapter.getBluetoothLeScanner();
        scanBluetooth();
    }

    private boolean checkBluetoothState() {
        boolean status = true;
        if ((mBluetoothAdapter == null) || (!mBluetoothAdapter.isEnabled())) {
            status = false;
        }
        return status;
    }

    private void scanBluetooth() {
        deviceCounter = 0;
        triedDevices = new HashSet<>();

        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        settingsBuilder.setReportDelay(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        }
        ScanSettings scanSettings = settingsBuilder.build();

        Log.d("Ble", "start scanning");

        mBluetoothLe.startScan(scanFilters, scanSettings, scanCallback);

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
            Toast.makeText(mContext, String.format("Scanning failed (%d)", errorCode), Toast.LENGTH_SHORT).show();
            finish();
        }

        private void connect(ScanResult result) {
            Log.d("Ble", result.getDevice().getAddress());
            Log.d("Ble", String.valueOf(result.getRssi()));
            //BLE.stopScan(scanCallback);
            if (mBluetoothGatt == null) {
                synchronized (triedDevices) {
                    if (!triedDevices.contains(result.getDevice().getAddress())) {
                        triedDevices.add(result.getDevice().getAddress());
                        MyBluetoothGattCallback mBleCallback = new MyBluetoothGattCallback();
                        result.getDevice().connectGatt(mContext, false, mBleCallback.getGattCallback());

                    }
                }
            }
        }
    };
}
