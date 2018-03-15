package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeScanProcess {

    private static final String TAG = "Bluetooth Scanner";

    private BluetoothAdapter mBluetoothAdapter;
    private android.bluetooth.le.BluetoothLeScanner mBleScanner;
    private Handler mHandler;
    private ScanResult scanResult;
    private Context context;
    private boolean mScanning = false;

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, BluetoothJsonDataProcess> mapProperties;

    private static final long SCAN_PERIOD = 10;

    public BLeScanNewCallback callback;

    public BLeScanOldCallback callbackOld;

    public Context getContext() {
        return this.context;
    }

    public boolean getScanState() {return this.mScanning;}

    public List<BluetoothDevice> getScanResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return callback.getListDevices();
        } else {
            return callbackOld.getListDevices();
        }
    }

    public Map<BluetoothDevice, BluetoothJsonDataProcess> getScanProperties() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return callback.getMapProperties();
        } else {
            return callbackOld.getMapProperties();
        }
    }

    public BluetoothLeScanProcess(Context context, BluetoothAdapter adapter) {
        this.context = context;
        this.mBluetoothAdapter = adapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            callback = new BLeScanNewCallback(context, new ArrayList<BluetoothDevice>(), new HashMap<BluetoothDevice, BluetoothJsonDataProcess>());
        } else {
            callbackOld = new BLeScanOldCallback(new ArrayList<BluetoothDevice>(), new HashMap<BluetoothDevice, BluetoothJsonDataProcess>());
        }
    }

    public void scanLeDevice(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            // directly scan
        }

        if(enable) {
            // start scan
            mScanning = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                newScan();
            } else {
                oldScan();
            }
        } else {
            // stop scan
            mScanning = false;
            stopScan();
        }


    }

    /**
     * scan using new Scan method
     */
    private void newScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            Log.d(TAG, "start scanning for " + SCAN_PERIOD + " seconds");
            mScanning = true;
            mBleScanner.startScan(scanFilters, scanSettings, callback);
        }
    }

    /**
     * scan using old scan method
     */
    private void oldScan() {
        // Stops scanning after a pre-defined scan period.
        mHandler = new Handler();
        Log.d(TAG, "start scanning for " + SCAN_PERIOD + " seconds");
        mScanning = true;
        mBluetoothAdapter.startLeScan(callbackOld);
    }

    private void stopScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner.stopScan(callback);
        } else {
            mBluetoothAdapter.stopLeScan(callbackOld);
        }
    }
}
