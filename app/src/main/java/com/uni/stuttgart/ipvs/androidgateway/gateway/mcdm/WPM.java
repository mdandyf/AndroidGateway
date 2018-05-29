package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class WPM implements Callable<Map<BluetoothDevice, Double>> {

    private List<BluetoothDevice> listDevices;
    private IGatewayService iGatewayService;
    private long batteryRemaining;

    private Map<BluetoothDevice, Object[]> mapProcessing;
    private Map<BluetoothDevice, BluetoothDevice> mapDevices;
    private Map<Map<BluetoothDevice, BluetoothDevice>, Double> mapProbability;
    private Map<BluetoothDevice, Double> mapOutput;

    public WPM(List<BluetoothDevice> listDevices, IGatewayService iGatewayService, long batteryRemaining) {
        this.listDevices = listDevices;
        this.iGatewayService = iGatewayService;
        this.batteryRemaining = batteryRemaining;
    }

    @Override
    public Map<BluetoothDevice, Double> call() throws Exception {
        mapOutput = new ConcurrentHashMap<>();
        mapProcessing = new ConcurrentHashMap<>();
        mapDevices = new ConcurrentHashMap<>();
        mapProbability = new ConcurrentHashMap<>();

        if(listDevices.size() == 1) {
            mapOutput.put(listDevices.get(0), 0.0);
            return mapOutput;
        }

        // populate all parameters from all devices
        for(BluetoothDevice device : listDevices) {
            // get device parameters
            int rssi = iGatewayService.getDeviceRSSI(device.getAddress());
            String deviceState = iGatewayService.getDeviceState(device.getAddress());
            long powerUsage = iGatewayService.getDevicePowerUsage(device.getAddress());
            double batteryPercent = (double) batteryRemaining;
            double[] powerConstraints = iGatewayService.getPowerUsageConstraints(batteryPercent);

            int deviceRssiScore = 0; int deviceStateScore = 0; int devicePowerUsageScore = 0;

            // set alternative weights
            if(rssi <  -50) {
                deviceRssiScore = 50;
            } else if(rssi < -60) {
                deviceRssiScore = 40;
            } else if(rssi < -70) {
                deviceRssiScore = 30;
            } else if(rssi <= -80) {
                deviceRssiScore = 20;
            } else if(rssi > -80) {
                deviceRssiScore = 10;
            }

            if(deviceState.equals("active")) {
                deviceStateScore = 50;
            } else {
                deviceStateScore = 20;
            }

            if(batteryRemaining >= 60) { // when battery 60 - 100 %
                if(powerUsage > powerConstraints[0]) {
                    devicePowerUsageScore = 50;
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    devicePowerUsageScore = 30;
                } else if(powerUsage < powerConstraints[2]) {
                    devicePowerUsageScore = 10;
                }
            } else if(batteryRemaining >= 20) { // when battery 20 - 60 %
                if(powerUsage > powerConstraints[0]) {
                    devicePowerUsageScore = 40;
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    devicePowerUsageScore = 20;
                } else if(powerUsage < powerConstraints[2]) {
                    devicePowerUsageScore = 10;
                }
            } else { // when battery 0 - 20 %
                if(powerUsage > powerConstraints[0]) {
                    devicePowerUsageScore = 30;
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    devicePowerUsageScore = 20;
                } else if(powerUsage < powerConstraints[2]) {
                    devicePowerUsageScore = 10;
                }
            }
            Object[] objects = new Object[3];
            objects[0] = deviceRssiScore;
            objects[1] = deviceStateScore;
            objects[2] = devicePowerUsageScore;
            mapProcessing.put(device, objects);
        }

        // calculate comparison of 2 devices to get probability P(A1 / A2)
        for(BluetoothDevice device : listDevices) {
            Object[] param = mapProcessing.get(device);
            for(Map.Entry entry : mapProcessing.entrySet()) {
                BluetoothDevice device2 = (BluetoothDevice) entry.getKey();
                Object[] param2 = (Object[]) entry.getValue();
                if(!device.equals(device2)) {
                    double deviceProbability = 0.0;
                    double rssiWeight = 0.3; double stateWeight = 0.2; double powerUsageWeight = 0.5;
                    double resRssi = (Math.pow(((Double) param[0] / (Double) param2[0]), rssiWeight));
                    double resState = (Math.pow(((Double) param[1] / (Double) param2[1]), stateWeight));
                    double resPU = (Math.pow(((Double) param[2] / (Double) param2[2]), powerUsageWeight));
                    deviceProbability = resRssi + resState + resPU;
                    mapDevices.put(device, device2);
                    mapProbability.put(mapDevices, deviceProbability);
                }
            }
        }

        // populate probability
        for(Map.Entry entry : mapProbability.entrySet()) {
            Map<BluetoothDevice, BluetoothDevice> map1 = (Map<BluetoothDevice, BluetoothDevice>) entry.getKey();
            for(Map.Entry entry1 : map1.entrySet()) {
                BluetoothDevice device = (BluetoothDevice) entry1.getKey();
                Double prob = (Double) entry.getValue();
                if(!mapOutput.containsKey(device)) {
                    mapOutput.put(device, prob);
                } else {
                    Double prob2 = mapOutput.get(device);
                    if(prob > prob2) {
                        mapOutput.remove(device);
                        mapOutput.put(device, prob);
                    }
                }
            }
        }

        // sort device priority based on probability
        DataSorterHelper<BluetoothDevice> sortData = new DataSorterHelper<>();
        mapOutput = sortData.sortMapByComparatorDouble(mapOutput, false);

        return mapOutput;
    }
}
