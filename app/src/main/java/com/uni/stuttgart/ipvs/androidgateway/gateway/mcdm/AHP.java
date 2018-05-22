package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import com.uni.stuttgart.ipvs.androidgateway.helper.matrix.IMatrixComputation;
import com.uni.stuttgart.ipvs.androidgateway.helper.matrix.MatrixComputation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AHP extends AsyncTask<Void, Void, Map<BluetoothDevice, Double>> {

    private Map<BluetoothDevice, Object[]> mapInput;
    private Map<BluetoothDevice, Double> mapOutput;
    private Map<BluetoothDevice, Long> mapPowerUsage;

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

        // set Matrix AHP Weight
        MatrixComputation matrixComputeAHP = new MatrixComputation(3,3);
        double[][] matrix = matrixComputeAHP.getMatrixIdentity();
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 0, 1, 0.33);
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 0, 2, 0.20);
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 1, 0, 3.00);
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 1, 2, 0.50);
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 2, 0, 5.00);
        matrix = matrixComputeAHP.changeMatrixValue(matrix, 2, 1, 2.00);
        this.matrix3x3 = matrix;
        this.matrix3x3Standardize = matrixComputeAHP.getMatrixStandardize(matrix3x3);
        this.matrix3x3Weight = matrixComputeAHP.getMatrixWeight(matrix3x3Standardize);
        this.matrixAHPWeight = this.matrix3x3Weight;

        // set Rssi Matrix Weight
        MatrixComputation matrixComputeRssi = new MatrixComputation(2,2);
        double[][] matrixRssi = matrixComputeRssi.getMatrixIdentity();
        matrixRssi = matrixComputeRssi.changeMatrixValue(matrixRssi, 0, 1, 2.00);
        matrixRssi = matrixComputeRssi.changeMatrixValue(matrixRssi, 1, 0, 0.50);
        this.matrix2x2 = matrixRssi;
        this.matrix2x2Standardize = matrixComputeRssi.getMatrixStandardize(matrix2x2);
        this.matrix2x2Weight = matrixComputeRssi.getMatrixWeight(matrix2x2Standardize);
        this.matrix2x2SubWeight = matrixComputeRssi.getMatrixSubWeight(matrix2x2Weight, matrixAHPWeight[0]);
        this.rssiMatrix = this.matrix2x2SubWeight;

        // set DeviceState Matrix Weight
        MatrixComputation matrixComputeDS = new MatrixComputation(2,2);
        double[][] matrixDS = matrixComputeDS.getMatrixIdentity();
        matrixDS = matrixComputeDS.changeMatrixValue(matrixDS, 0, 1, 3.00);
        matrixDS = matrixComputeDS.changeMatrixValue(matrixDS, 1, 0, 0.33);
        this.matrix2x2 = matrixDS;
        this.matrix2x2Standardize = matrixComputeDS.getMatrixStandardize(matrix2x2);
        this.matrix2x2Weight = matrixComputeDS.getMatrixWeight(matrix2x2Standardize);
        this.matrix2x2SubWeight = matrixComputeDS.getMatrixSubWeight(matrix2x2Weight, matrixAHPWeight[1]);
        this.deviceStateMatrix = this.matrix2x2SubWeight;

        //set PowerUsage Matrix Weight
        MatrixComputation matrixComputePU = new MatrixComputation(3,3);
        double[][] matrixPU = matrixComputePU.getMatrixIdentity();
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 0, 1, 0.33);
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 0, 2, 0.20);
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 1, 0, 3.00);
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 1, 2, 0.50);
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 2, 0, 5.00);
        matrixPU = matrixComputePU.changeMatrixValue(matrixPU, 2, 1, 2.00);
        this.matrix3x3 = matrixPU;
        this.matrix3x3Standardize = matrixComputePU.getMatrixStandardize(matrix3x3);
        this.matrix3x3Weight = matrixComputePU.getMatrixWeight(matrix3x3Standardize);
        this.matrix3x3SubWeight = matrixComputePU.getMatrixSubWeight(matrix3x3Weight, matrixAHPWeight[2]);
        this.powerUsageMatrix = this.matrix3x3SubWeight;
    }

    /**
     * =======================================================================================================================
     * Calculate Number of Percentage of a device to be connected
     * =======================================================================================================================
     */

    @Override
    protected Map<BluetoothDevice, Double> doInBackground(Void... voids) {
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
                if(powerUsage > (1 * 10^15)) {
                    devicePercentage = devicePercentage + powerUsageMatrix[0];
                } else if((powerUsage >= (1 * 10^14)) && (powerUsage < (1 * 10^15))) {
                    devicePercentage = devicePercentage + powerUsageMatrix[1];
                } else if(powerUsage < (1 * 10^14)) {
                    devicePercentage = devicePercentage + powerUsageMatrix[2];
                }

                mapOutput.put(device, devicePercentage);
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
