package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 3/9/2018.
 */


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScanCallbackNew extends ScanCallback {

    private List<BluetoothDevice> listDevices;
    private Map<BluetoothDevice, GattDataJson> mapProperties;
    private Context context;

    public ScanCallbackNew(Context context, List<BluetoothDevice> listDevices, Map<BluetoothDevice, GattDataJson> mapProperties) {
        this.context = context;
        this.listDevices = listDevices;
        this.mapProperties = mapProperties;
    }
    public List<BluetoothDevice> getListDevices() {return listDevices;}
    public Map<BluetoothDevice, GattDataJson> getMapProperties(){return mapProperties;}

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
                GattDataJson json = new GattDataJson(result.getDevice(), result.getRssi(), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    json = new GattDataJson(result.getDevice(), result.getRssi(), result.getTxPower());
                }
                mapProperties.put(result.getDevice(), json);
            }
        }
    }

}
