package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import com.uni.stuttgart.ipvs.androidgateway.helper.matrix.MatrixComputation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ANP extends AsyncTask<Void, Void, Map<BluetoothDevice, Double>> {

    private static final int GOAL_SIZE = 1;
    private static final int CRITERIA_SIZE = 3;

    private Map<BluetoothDevice, Object[]> mapInput;
    private Map<BluetoothDevice, Double> mapOutput;
    private Map<BluetoothDevice, Object[]> mapDeviceSubWeight;

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

    private double[][] wrtRssi;
    private double[][] wrtPU;
    private double[][] wrtDS;
    private double[][] superMatrix;
    private double[][] superMatrixWeighted;
    private double[][] LimitMatrix;



    /**
     * =======================================================================================================================
     * Preparation Section
     * =======================================================================================================================
     */

    public ANP(Map<BluetoothDevice, Object[]> mapInput) {
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

        MatrixComputation matrixCompute = new MatrixComputation((GOAL_SIZE + CRITERIA_SIZE + mapInput.size()),(GOAL_SIZE + CRITERIA_SIZE + mapInput.size()));
        this.superMatrix = matrixCompute.getMatrixZero();
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 1, 0, this.matrixAHPWeight[0]);
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 2, 0, this.matrixAHPWeight[1]);
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 3, 0, this.matrixAHPWeight[2]);
    }

    /**
     * =======================================================================================================================
     * Calculate Number of Percentage of a device to be connected
     * =======================================================================================================================
     */

    @Override
    protected Map<BluetoothDevice, Double> doInBackground(Void... voids) {
        // Looping to all available devices
        Object[] subWeight = new Object[4];
        if(mapInput != null && mapInput.size() > 0) {
            for(Map.Entry entry : mapInput.entrySet()) {
                BluetoothDevice device = (BluetoothDevice) entry.getKey();
                Object[] values = (Object[]) entry.getValue();
                int rssi = 0; String state = ""; String userChoice="";long powerUsage = 0;int counter = 0;double[] powerConstraints = new double[3];
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
                    } else if(counter == 5) {
                        powerConstraints = (double[]) value;
                    }
                }

                double devicePercentage = 0;

                // calculate percentage RSSI
                if(rssi != 0 && rssi > -80) {
                    devicePercentage = rssiMatrix[0];
                    subWeight[0] = rssiMatrix[0];
                } else {
                    devicePercentage = rssiMatrix[1];
                    subWeight[0] = rssiMatrix[1];
                }

                // calculate percentage DeviceState
                if("active".equals(state)) {
                    devicePercentage = devicePercentage + deviceStateMatrix[0];
                    subWeight[1] = deviceStateMatrix[0];
                } else {
                    devicePercentage = devicePercentage + deviceStateMatrix[1];
                    subWeight[1] = deviceStateMatrix[1];
                }

                //calculate percentage PowerUsage
                if(powerUsage > powerConstraints[0]) {
                    devicePercentage = devicePercentage + powerUsageMatrix[0];
                    subWeight[2] = powerUsageMatrix[0];
                } else if((powerUsage >= powerConstraints[1]) && (powerUsage < powerConstraints[0])) {
                    devicePercentage = devicePercentage + powerUsageMatrix[1];
                    subWeight[2] = powerUsageMatrix[1];
                } else if(powerUsage < powerConstraints[2]) {
                    devicePercentage = devicePercentage + powerUsageMatrix[2];
                    subWeight[2] = powerUsageMatrix[2];
                }

                mapOutput.put(device, devicePercentage);
                mapDeviceSubWeight.put(device, subWeight);
            }
        }

        MatrixComputation computeWrtRssi = new MatrixComputation(mapInput.size(), mapInput.size());
        this.wrtRssi = computeWrtRssi.getMatrixIdentity();

        int i = 0;
        int j = 0;
        this.wrtRssi = new double[mapDeviceSubWeight.size()][mapDeviceSubWeight.size()];
        for(Map.Entry entry : mapDeviceSubWeight.entrySet()){
            for(Map.Entry entry2 : mapDeviceSubWeight.entrySet()){
                Object[] value = (Object[]) mapDeviceSubWeight.get((BluetoothDevice) entry.getKey());
                Object[] value2 = (Object[]) mapDeviceSubWeight.get((BluetoothDevice) entry2.getKey());
                this.wrtRssi[i][j] = (double) value[0] / (double) value2[0];
                j++;
            }
            i++;
        }

        return mapOutput;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mapOutput  = new ConcurrentHashMap<>();
        mapDeviceSubWeight = new ConcurrentHashMap<>();
    }
}
