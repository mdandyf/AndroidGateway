package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;

import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.matrix.MatrixComputation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class ANP implements Callable<Map<BluetoothDevice, Double>> {
    private List<BluetoothDevice> listDevices;
    private IGatewayService iGatewayService;
    private long batteryRemaining;

    private static final int GOAL_SIZE = 1;
    private static final int CRITERIA_SIZE = 3;

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
    private Map<BluetoothDevice, Double> mapOutput;
    private Map<BluetoothDevice, Object[]> mapDeviceSubWeight;

    public ANP(List<BluetoothDevice> listDevices, IGatewayService iGatewayService, long batteryRemaining) {
        this.listDevices = listDevices;
        this.iGatewayService = iGatewayService;
        this.batteryRemaining = batteryRemaining;
    }

    @Override
    public Map<BluetoothDevice, Double> call() throws Exception {
        mapOutput = new ConcurrentHashMap<>();
        mapDeviceSubWeight = new ConcurrentHashMap<>();

        if(listDevices.size() == 1) {
            mapOutput.put(listDevices.get(0), 0.0);
            return mapOutput;
        }

        initializeMatrix();

        processing();

        sorting();

        return mapOutput;
    }

    //process calculation of ANP on each device
    private void processing() {
        try {
            Object[] subWeight = new Object[4];
            for(BluetoothDevice device : listDevices) {

                // get device parameters
                int rssi = iGatewayService.getDeviceRSSI(device.getAddress());
                String deviceState = iGatewayService.getDeviceState(device.getAddress());
                long powerUsage = iGatewayService.getDevicePowerUsage(device.getAddress());
                double batteryPercent = (double) batteryRemaining;
                double[] powerConstraints = iGatewayService.getPowerUsageConstraints(batteryPercent);

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
                if("active".equals(deviceState)) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // sort data after processing based on the percentage
    private void sorting() {
        MatrixComputation computeWrtRssi = new MatrixComputation(listDevices.size(), listDevices.size());
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
    }

    // initializing matrix data for ANP calculation
    private void initializeMatrix() {

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

        MatrixComputation matrixCompute = new MatrixComputation((GOAL_SIZE + CRITERIA_SIZE + listDevices.size()),(GOAL_SIZE + CRITERIA_SIZE + listDevices.size()));
        this.superMatrix = matrixCompute.getMatrixZero();
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 1, 0, this.matrixAHPWeight[0]);
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 2, 0, this.matrixAHPWeight[1]);
        this.superMatrix = matrixCompute.changeMatrixValue(this.superMatrix, 3, 0, this.matrixAHPWeight[2]);
    }
}
