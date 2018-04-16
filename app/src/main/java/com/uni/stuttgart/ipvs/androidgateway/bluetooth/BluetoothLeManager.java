package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;

import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BluetoothLeManager {

    private boolean mConnected = false;
    private Context context;

    private Activity mParent = null;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothSelectedService = null;
    private BluetoothGattCharacteristic mBluetoothSelectedCharacteristic = null;

    private List<BluetoothDevice> listDevices = new ArrayList<>();
    private Map<BluetoothDevice, GattDataJson> mapDevices = new HashMap<>();
    private List<BluetoothGattService> mBluetoothGattServices = new ArrayList<>();

    private ScanCallbackNew scanCallback;
    private ScanCallbackOld scanCallbackOld;
    private BluetoothLeGattCallback leGattCallback;

    /**
     *  scan section
     */

    public BluetoothLeManager(BluetoothAdapter mBluetoothAdapter) {
        this.mBluetoothAdapter = mBluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanCallback = getScanCallback();
        } else {
            scanCallbackOld = getScanCallbackOld();
        }
        leGattCallback = getLeGattCallback();
    }

    public void setMessageHandler(Handler mHandlerMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanCallback.setMessageHandler(mHandlerMessage);
        } else {
            scanCallbackOld.setHandlerMessage(mHandlerMessage);
        }
        leGattCallback.setHandlerMessage(mHandlerMessage);
    }

    public BluetoothAdapter getAdapter() {
        return mBluetoothAdapter;
    }

    public BluetoothDevice getDevice() {
        return mBluetoothDevice;
    }

    public BluetoothGatt getGatt() {
        return mBluetoothGatt;
    }

    public BluetoothGattService getCachedService() {
        return mBluetoothSelectedService;
    }

    public List<BluetoothGattService> getCachedServices() {
        return mBluetoothGattServices;
    }

    public boolean isConnected() {
        return mConnected;
    }

    /* run test and check if this device has BT and BLE hardware available */
    public boolean checkBleHardwareAvailable() {
        // First check general Bluetooth Hardware:
        // get BluetoothManager...
        final BluetoothManager manager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return false;
        }
        // .. and then get adapter from manager
        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) {
            return false;
        }

        // and then check if BT LE is also available
        return mParent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public boolean isBtEnabled() {
        final BluetoothManager manager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return false;
        }

        final BluetoothAdapter adapter = manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /* start scanning for BT LE devices around */
    public void startScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothAdapter.getBluetoothLeScanner().startScan(null, getScanSettings().build(), scanCallback);
        } else {
            mBluetoothAdapter.startLeScan(scanCallbackOld);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startScanning(String macAddress) {
        mBluetoothAdapter.getBluetoothLeScanner().startScan(getScanFilters(macAddress), getScanSettings().build(), scanCallback);
    }


    public void startScanning(UUID[] listServices) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothAdapter.getBluetoothLeScanner().startScan(getScanFilters(listServices), getScanSettings().build(), scanCallback);
        } else {
            mBluetoothAdapter.startLeScan(listServices, scanCallbackOld);
        }
    }

    /* stops current scanning */
    public void stopScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(scanCallbackOld);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallbackNew getScanCallback() {
        return new ScanCallbackNew(context, listDevices, mapDevices);
    }

    private ScanCallbackOld getScanCallbackOld() {
        return new ScanCallbackOld(listDevices, mapDevices);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ScanSettings.Builder getScanSettings() {
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
        return settingsBuilder;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private List<ScanFilter> getScanFilters(String macAddress) {
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter scanFilter = new ScanFilter.Builder().setDeviceAddress(macAddress).build();
        scanFilters.add(scanFilter);
        return scanFilters;
    }

    private List<ScanFilter> getScanFilters(UUID[] listServices) {
        ScanFilter scanFilter = null;
        List<ScanFilter> scanFilters = new ArrayList<>();
        for(UUID serviceUUID : listServices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                scanFilter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(serviceUUID.toString())).build();
                scanFilters.add(scanFilter);
            }
        }
        return scanFilters;
    }

    /**
     *  connnect section
     */

    public BluetoothLeGattCallback getLeGattCallback() {
        return new BluetoothLeGattCallback(context);
    }

    public void connectLe(String macAddress) {
        leGattCallback.connect(getAdapter(), macAddress);
    }

    public void disconnectLe(String macAddress) {
        leGattCallback.disconnect();
    }
}

