package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class WSM implements Callable<Map<BluetoothDevice, Double>> {
    private Map<BluetoothDevice, Object[]> mapInput;
    private Map<BluetoothDevice, Double> mapOutput;

    public WSM(Map<BluetoothDevice, Object[]> mapInput) {
        this.mapInput = mapInput;
    }

    @Override
    public Map<BluetoothDevice, Double> call() throws Exception {
        mapOutput = new ConcurrentHashMap<>();
        for(Map.Entry entry : mapInput.entrySet()) {
            BluetoothDevice device = (BluetoothDevice) entry.getKey();
            Object[] values = (Object[]) entry.getValue();

            int rssi = 0; String state = ""; String userChoice="";long powerUsage = 0;int counter = 0;double[] powerConstraints = new double[3];long batteryRemaining = 0;
            for(Object value : values) {
                counter++;
                if(counter == 1) {
                    rssi = (Integer) value;
                } else if(counter == 2) {
                    state = (String) value;
                } else if(counter == 3) {
                    powerUsage = (Long) value;
                } else if(counter == 4) {
                    powerConstraints = (double[]) value;
                } else if(counter == 5) {
                    batteryRemaining = (Long) value;
                }
            }

            // set criteria weights
            int rssiWeight = 3; int stateWeight = 2; int powerUsageWeight = 5;double deviceWeight = 0.0;

            // set alternative weights
            if(rssi <  -50) {
                deviceWeight = deviceWeight +( rssiWeight * 5);
            } else if(rssi < -60) {
                deviceWeight = deviceWeight +( rssiWeight * 4);
            } else if(rssi < -70) {
                deviceWeight = deviceWeight +( rssiWeight * 3);
            } else if(rssi <= -80) {
                deviceWeight = deviceWeight +( rssiWeight * 2);
            } else if(rssi > -80) {
                deviceWeight = deviceWeight +( rssiWeight * 1);
            }

            if(state.equals("active")) {
                deviceWeight = deviceWeight +( stateWeight * 5);
            } else {
                deviceWeight = deviceWeight +( stateWeight * 2);
            }

            if(batteryRemaining > 60) { // when battery 61 - 100 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 5);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 3);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 1);
                }
            } else if(batteryRemaining > 20) { // when battery 21 - 60 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 4);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 2);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 1);
                }
            } else { // when battery 0 - 20 %
                if(powerUsage > powerConstraints[0]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 3);
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 2);
                } else if(powerUsage < powerConstraints[2]) {
                    deviceWeight = deviceWeight +( powerUsageWeight * 1);
                }
            }

            mapOutput.put(device, deviceWeight);

        }
        return mapOutput;
    }
}
