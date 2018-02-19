package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.BluetoothActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by mdand on 2/17/2018.
 */

public class BluetoothConfigurationManager extends BluetoothActivity {
    private Bluetooth bluetooth;


    public BluetoothConfigurationManager(){
        bluetooth = new BluetoothImpl();
    }

    public void setBluetooth(Bluetooth input) {
        this.bluetooth = input;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        if(!checkBluetoothState()) {
            Log.d("Ble", "Bluetooth is already on");
            return BluetoothAdapter.getDefaultAdapter();
        }
        return null;
    }

    public BluetoothLeScanner getBleDevice() {
        return getBluetoothAdapter().getBluetoothLeScanner();
    }

    private boolean checkBluetoothState() {
        boolean status = true;
        if ((bluetooth.getBluetoothAdapter() == null) || (!bluetooth.getBluetoothAdapter().isEnabled())) {
            status = false;
        }
        return status;
    }

    public void scanBluetooth() {
        bluetooth.setDeviceCounter(0);
        bluetooth.setTriedDevices(new HashSet<String>());

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
        Toast.makeText(bluetooth.getContext(), "Start Scanning", Toast.LENGTH_SHORT).show();
        bluetooth.getBleScanner().startScan(scanFilters, scanSettings, scanCallback);
    }

    private void disconnect() {
        Log.d("Ble", "disconnect bluetooth");

        if (bluetooth.getBleScanner() != null) {
            bluetooth.getBleScanner().stopScan(scanCallback);
        }

        if (bluetooth.getBluetoothGatt() != null) {
            bluetooth.getBluetoothGatt().disconnect();
        }

        bluetooth.setBleScanner(null);
        bluetooth.setBluetoothGatt(null);

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
            Toast.makeText(bluetooth.getContext(), String.format("Scanning failed (%d)", errorCode), Toast.LENGTH_SHORT).show();
            finish();
        }

        private void connect(ScanResult result) {
            Log.d("Ble", result.getDevice().getAddress());
            Log.d("Ble", String.valueOf(result.getRssi()));
            //BLE.stopScan(scanCallback);
            if (bluetooth.getBluetoothGatt() == null) {
                synchronized (bluetooth.getTriedDevices()) {
                    if (!bluetooth.getTriedDevices().contains(result.getDevice().getAddress())) {
                        bluetooth.getTriedDevices().add(result.getDevice().getAddress());
                        result.getDevice().connectGatt(bluetooth.getContext(), false, new MyBluetoothGattCallback(bluetooth));

                    }
                }
            }
        }
    };
}
