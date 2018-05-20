package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AHP extends AsyncTask<Void, Void, Map<BluetoothDevice, String>> {

    private Map<BluetoothDevice, Object[]> mapInput;
    private Map<BluetoothDevice, String> mapOutput;
    private Map<BluetoothDevice, Long> mapPowerUsage;

    private MatrixOperation matrixOperation = new MatrixOperation();

    private double[][] matrix3x3;
    private double[][] matrix3x3Standardize;
    private double[] matrix3x3Weight;
    private double[] matrix3x3SubWeight;

    private double[][] matrix2x2;
    private double[][] matrix2x2Standardize;
    private double[] matrix2x2Weight;
    private double[] matrix2x2SubWeight;

    private double[] matrixAHPWeight;
    private double[] rssiMatrix;
    private double[] userChoiceMatrix;
    private double[] deviceStateMatrix;
    private double[] powerUsageMatrix;

    /**
     * =======================================================================================================================
     * Preparation Section
     * =======================================================================================================================
     */

    public AHP(Map<BluetoothDevice, Object[]> mapInput) {
        this.mapInput = mapInput;
        this.matrix3x3 = matrixOperation.setMatrix3x3(1.00, 0.33, 0.20,
                                                        3.00, 1.00, 0.50,
                                                            5.00, 2.00, 1.00);
        this.matrix3x3Standardize = matrixOperation.setMatrix3x3Standardize();
        this.matrix3x3Weight = matrixOperation.setMatrix3x3Weight();
        this.matrixAHPWeight = matrixOperation.matrix3x3Weight;

        // set Rssi Matrix
        this.matrix2x2 = matrixOperation.setMatrix2x2(1.00, 3,
                                                       0.33, 1.00);
        this.matrix2x2Standardize = matrixOperation.setMatrix2x2Standardize();
        this.matrix2x2Weight = matrixOperation.setMatrix2x2Weight();
        this.matrix2x2SubWeight = matrixOperation.setMatrix2x2SubWeight(matrix2x2Weight, matrixAHPWeight[0]);
        this.rssiMatrix = matrix2x2SubWeight;

        // set DeviceState Matrix
        this.matrix2x2 = matrixOperation.setMatrix2x2(1.00, 3.00,
                                                      0.33, 1.00);
        this.matrix2x2Standardize = matrixOperation.setMatrix2x2Standardize();
        this.matrix2x2Weight = matrixOperation.setMatrix2x2Weight();
        this.matrix2x2SubWeight = matrixOperation.setMatrix2x2SubWeight(matrix2x2Weight, matrixAHPWeight[1]);
        this.deviceStateMatrix = matrix2x2SubWeight;

        //set PowerUsage Matrix
        this.matrix3x3 = matrixOperation.setMatrix3x3(1.00, 0.33, 0.2,
                                                      3.00, 1.00, 0.50,
                                                      5.00, 2.00, 1.00);
        this.matrix3x3Standardize = matrixOperation.setMatrix3x3Standardize();
        this.matrix3x3Weight = matrixOperation.setMatrix3x3Weight();
        this.matrix3x3SubWeight = matrixOperation.setMatrix3x3SubWeight(matrix3x3Weight, matrixAHPWeight[2]);
        this.powerUsageMatrix = matrix3x3SubWeight;
    }

    /**
     * =======================================================================================================================
     * Calculate Number of Percentage of a device to be connected
     * =======================================================================================================================
     */

    @Override
    protected Map<BluetoothDevice, String> doInBackground(Void... voids) {
        // Looping to all available devices
        if(mapInput != null && mapInput.size() > 0) {
            for(Map.Entry entry : mapInput.entrySet()) {
                BluetoothDevice device = (BluetoothDevice) entry.getKey();
                Object[] values = (Object[]) entry.getValue();
                int rssi = 0; String state = ""; String userChoice="";long powerUsage = 0;int counter = 0;
                for(Object value : values) {
                    counter++;
                    if(counter == 1) {
                        rssi = (Integer) value;
                    } else if(counter == 2) {
                        state = (String) value;
                    } else if(counter == 3) {
                        userChoice = (String) value;
                    } else if(counter == 4) {
                        powerUsage = (Long) value;
                    }
                }

                double devicePercentage = 0;

                // calculate percentage RSSI
                if(rssi != 0 && rssi > -80) {
                    devicePercentage = rssiMatrix[0];
                } else {
                    devicePercentage = rssiMatrix[1];
                }

                // calculate percentage DeviceState
                if("active".equals(state)) {
                    devicePercentage = devicePercentage + deviceStateMatrix[0];
                } else {
                    devicePercentage = devicePercentage + deviceStateMatrix[1];
                }

                //calculate percentage PowerUsage
                if(powerUsage > (5 * 10^16)) {
                    devicePercentage = devicePercentage + powerUsageMatrix[0];
                } else if(powerUsage < (5 * 10^16)) {
                    devicePercentage = devicePercentage + powerUsageMatrix[1];
                } else if(powerUsage < (1 * 10^16)) {
                    devicePercentage = devicePercentage + powerUsageMatrix[2];
                }

                // if sum of all percentages is more than 40%, then connect the device
                if(devicePercentage > 0.4) {
                    mapOutput.put(device, "Yes");
                } else {
                    mapOutput.put(device, "No");
                }
            }
        }
        return mapOutput;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mapOutput  = new ConcurrentHashMap<>();
        mapPowerUsage = new ConcurrentHashMap<>();
    }
}
