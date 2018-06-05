package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class WSM implements Callable<Map<BluetoothDevice, Double>> {

    private List<BluetoothDevice> listDevices;
    private IGatewayService iGatewayService;
    private long batteryRemaining;

    private Map<BluetoothDevice, Double> mapOutput;

    public WSM(List<BluetoothDevice> listDevices, IGatewayService iGatewayService, long batteryRemaining) {
        this.listDevices = listDevices;
        this.iGatewayService = iGatewayService;
        this.batteryRemaining = batteryRemaining;
    }

    @Override
    public Map<BluetoothDevice, Double> call() throws Exception {
        mapOutput = new ConcurrentHashMap<>();

        if(listDevices.size() == 1) {
            mapOutput.put(listDevices.get(0), 0.0);
            return mapOutput;
        }

        for(BluetoothDevice device : listDevices) {
            // get device parameters
            int rssi = iGatewayService.getDeviceRSSI(device.getAddress());
            String deviceState = iGatewayService.getDeviceState(device.getAddress());
            long powerUsage = iGatewayService.getDevicePowerUsage(device.getAddress());
            double batteryPercent = (double) batteryRemaining;
            double[] powerConstraints = iGatewayService.getPowerUsageConstraints(batteryPercent);

            // set criteria weights
            int rssiWeight = 3; int stateWeight = 2; int powerUsageWeight = 5;double deviceWeight = 0.0;

            // set alternative weights
            if(rssi <  -50) {
                deviceWeight = deviceWeight + ( rssiWeight * 5);
            } else if(rssi < -60) {
                deviceWeight = deviceWeight + ( rssiWeight * 4);
            } else if(rssi < -70) {
                deviceWeight = deviceWeight + ( rssiWeight * 3);
            } else if(rssi <= -80) {
                deviceWeight = deviceWeight + ( rssiWeight * 2);
            } else if(rssi > -80) {
                deviceWeight = deviceWeight + ( rssiWeight * 1);
            }

            if(deviceState.equals("active")) {
                deviceWeight = deviceWeight +( stateWeight * 5);
            } else {
                deviceWeight = deviceWeight +( stateWeight * 2);
            }

            if(batteryRemaining >= 60) { // when battery 60 - 100 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 5);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 3);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 1);
                }
            } else if(batteryRemaining >= 20) { // when battery 20 - 60 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 4);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 2);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 1);
                }
            } else { // when battery 0 - 20 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 3);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 2);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight + ( powerUsageWeight * 1);
                }
            }

            mapOutput.put(device, deviceWeight);

        }

        //sort data
        DataSorterHelper<BluetoothDevice> sortData = new DataSorterHelper<>();
        mapOutput = sortData.sortMapByComparatorDouble(mapOutput, false);

        return mapOutput;
    }
}
