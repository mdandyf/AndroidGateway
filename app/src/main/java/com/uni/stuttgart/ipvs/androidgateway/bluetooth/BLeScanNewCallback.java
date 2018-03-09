package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BLeScanNewCallback extends ScanCallback {

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, BluetoothJsonDataProcess> mapProperties;
    private Context context;

    public BLeScanNewCallback(Context context, List<BluetoothDevice> listDevices, Map<BluetoothDevice, BluetoothJsonDataProcess> mapProperties) {
        this.context = context;
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, BluetoothJsonDataProcess> getMapProperties(){return mapProperties;}

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
        Log.w("Bluetooth ScanCallback", "Scanning failed with errorCode " + errorCode);
        Toast.makeText(context, String.format("Scanning failed (%d)", errorCode), Toast.LENGTH_SHORT).show();
    }

    private void addBluetoothDevice(ScanResult result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!listDevices.contains(result.getDevice())) {
                listDevices.add(result.getDevice());
                BluetoothJsonDataProcess json = new BluetoothJsonDataProcess(result.getDevice(), result.getRssi(), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    json = new BluetoothJsonDataProcess(result.getDevice(), result.getRssi(), result.getTxPower());
                }
                mapProperties.put(result.getDevice(), json);
            }
        }
    }

}
