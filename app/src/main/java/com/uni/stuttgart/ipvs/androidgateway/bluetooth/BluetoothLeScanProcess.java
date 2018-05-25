package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.ScanCallbackNew;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.ScanCallbackOld;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeScanProcess {

    private static final String TAG = "Bluetooth Scanner";

    private BluetoothAdapter mBluetoothAdapter;
    private android.bluetooth.le.BluetoothLeScanner mBleScanner;
    private Context context;
    private boolean mScanning = false;
    public ScanCallbackNew callback;
    public ScanCallbackOld callbackOld;

    public BluetoothLeScanProcess(Context context, BluetoothAdapter adapter) {
        this.context = context;
        this.mBluetoothAdapter = adapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            callback = new ScanCallbackNew(context, new ArrayList<BluetoothDevice>(), new HashMap<BluetoothDevice, GattDataJson>(), new HashMap<BluetoothDevice, byte[]>());
        } else {
            callbackOld = new ScanCallbackOld(new ArrayList<BluetoothDevice>(), new HashMap<BluetoothDevice, GattDataJson>(), new HashMap<BluetoothDevice, byte[]>());
        }
    }

    public Context getContext() {
        return this.context;
    }

    public boolean getScanState() {return this.mScanning;}

    public void setHandlerMessage(Handler mHandlerMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            callback.setMessageHandler(mHandlerMessage);
        } else {
            callbackOld.setHandlerMessage(mHandlerMessage);
        }
    }

    public List<BluetoothDevice> getScanResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return callback.getListDevices();
        } else {
            return callbackOld.getListDevices();
        }
    }

    public Map<BluetoothDevice, GattDataJson> getScanProperties() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return callback.getMapProperties();
        } else {
            return callbackOld.getMapProperties();
        }
    }

    public BluetoothDevice getRemoteDevice(String address) {return mBluetoothAdapter.getRemoteDevice(address);}

    /** method to start or stop scan for new devices */
    public void scanLeDevice(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            // directly scan or use bluetooth adapter
        }

        if(enable) {
            // start scan
            mScanning = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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

    // method to start scan only for spesific known LE Device
    public void findLeDevice(String macAddress, boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            // directly scan or use bluetooth adapter
        }

        if(enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                newScan(macAddress);
            } else {
                oldScan(macAddress);
            }
        } else {
            stopScan();
        }
    }

    /** method to start or stop scan for known LE Device services */
    public void findLeDevice(UUID[] servicesUUID, boolean enable) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            // directly scan or use bluetooth adapter
        }

        if(enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                newScan(servicesUUID);
            } else {
                oldScan(servicesUUID);
            }
        } else {
            stopScan();
        }


    }


    /**
     * scan using new Scan method
     */
    private void newScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            mScanning = true;
            mBleScanner.startScan(scanFilters, scanSettings, callback);
        }
    }

    /**
     * scan using new Scan method for known LE Device's services
     */
    private void newScan(UUID[] servicesUUID) {
        ScanFilter scanFilter = null;
        List<ScanFilter> scanFilters = new ArrayList<>();
        for(UUID serviceUUID : servicesUUID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                scanFilter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(serviceUUID.toString())).build();
                scanFilters.add(scanFilter);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            settingsBuilder.setReportDelay(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
                settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
                settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settingsBuilder.setLegacy(false);
                }
            }

            ScanSettings scanSettings = settingsBuilder.build();
            mScanning = true;
            mBleScanner.startScan(scanFilters, scanSettings, callback);
        }
    }

    /**
     * scan using new Scan method for known LE Device's address
     */
    private void newScan(String macAddress) {
        ScanFilter scanFilter = null;
        List<ScanFilter> scanFilters = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanFilter = new ScanFilter.Builder().setDeviceAddress(macAddress).build();
            scanFilters.add(scanFilter);
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            settingsBuilder.setReportDelay(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
                settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
                settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settingsBuilder.setLegacy(false);
                }
            }

            ScanSettings scanSettings = settingsBuilder.build();
            mScanning = true;
            mBleScanner.startScan(scanFilters, scanSettings, callback);
        }
    }

    /**
     * scan using old scan method
     */
    private void oldScan() {
        mScanning = true;
        mBluetoothAdapter.startLeScan(callbackOld);
    }


    /**
     * scan using old scan method for known LE Device's services
     */
    private void oldScan(UUID[] servicesUUID) {
        mScanning = true;
        mBluetoothAdapter.startLeScan(servicesUUID, callbackOld);
    }

    /**
     * scan using old scan method for known LE Device's address
     */
    private void oldScan(String macAddress) {
        mScanning = true;
        mBluetoothAdapter.startLeScan(callbackOld);
    }


    private void stopScan() {
        mScanning = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner.stopScan(callback);
        } else {
            mBluetoothAdapter.stopLeScan(callbackOld);
        }
    }
}
