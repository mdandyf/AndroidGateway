package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AHP extends AsyncTask<Void, Void, Map<BluetoothDevice, String>> {

    private Map<BluetoothDevice, Object[]> mapInput;
    private Map<BluetoothDevice, String> mapOutput;
    private Map<BluetoothDevice, Long> mapPowerUsage;

    private double[][] matrix4x4;
    private double[][] matrix4x4Standardize;
    private double[] matrix4x4Weight;
    private double[] matrix4x4SubWeight;

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

        this.matrix4x4 = setMatrix4x4(1.00, 0.14, 0.2, 0.11,
                                      7.00, 1.00, 3.00, 1.00,
                                      5.00, 0.33, 1.00, 0.33,
                                      9.00, 1.00, 3.00, 1.00);
        this.matrix4x4Standardize = setMatrix4x4Standardize();
        this.matrix4x4Weight = setMatrix4x4Weight();
        this.matrixAHPWeight = matrix4x4Weight;

        // set Rssi Matrix
        this.matrix2x2 = setMatrix2x2(1.00, 3,
                                      0.33, 1.00);
        this.matrix2x2Standardize = setMatrix2x2Standardize();
        this.matrix2x2Weight = setMatrix2x2Weight();
        this.matrix2x2SubWeight = setMatrix2x2SubWeight(matrix2x2Weight, matrixAHPWeight[0]);
        this.rssiMatrix = matrix2x2SubWeight;

        // set DeviceState Matrix
        this.matrix2x2 = setMatrix2x2(1.00, 5.00,
                                      0.20, 1.00);
        this.matrix2x2Standardize = setMatrix2x2Standardize();
        this.matrix2x2Weight = setMatrix2x2Weight();
        this.matrix2x2SubWeight = setMatrix2x2SubWeight(matrix2x2Weight, matrixAHPWeight[1]);
        this.deviceStateMatrix = matrix2x2SubWeight;

        // set UserChoice Matrix
        this.matrix2x2 = setMatrix2x2(1.00, 7.00,
                                      0.14, 1.00);
        this.matrix2x2Standardize = setMatrix2x2Standardize();
        this.matrix2x2Weight = setMatrix2x2Weight();
        this.matrix2x2SubWeight = setMatrix2x2SubWeight(matrix2x2Weight, matrixAHPWeight[2]);
        this.userChoiceMatrix = matrix2x2SubWeight;

        //set PowerUsage Matrix
        this.matrix3x3 = setMatrix3x3(1.00, 0.33, 0.11,
                                      3.00, 1.00, 2.00,
                                            9.00, 0.50, 1.00);
        this.matrix3x3Standardize = setMatrix3x3Standardize();
        this.matrix3x3Weight = setMatrix3x3Weight();
        this.matrix3x3SubWeight = setMatrix3x3SubWeight(matrix3x3Weight, matrixAHPWeight[3]);
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

                //calculate percentage UserChoice
                if("Yes".equals(userChoice)) {
                    devicePercentage = devicePercentage + userChoiceMatrix[0];
                } else {
                    devicePercentage = devicePercentage + userChoiceMatrix[1];
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

    /**
     * =======================================================================================================================
     * Calculation Section (4x4 matrix)
     * =======================================================================================================================
     */

    private double[][] setMatrix4x4(double matrix00, double matrix01, double matrix02, double matrix03,
                                    double matrix10, double matrix11, double matrix12, double matrix13,
                                    double matrix20, double matrix21, double matrix22, double matrix23,
                                    double matrix30, double matrix31, double matrix32, double matrix33) {
        double[][] matrix4x4 = new double[4][4];
        matrix4x4[0][0] = matrix00;matrix4x4[0][1] = matrix01;matrix4x4[0][2] = matrix02;matrix4x4[0][3] = matrix03;
        matrix4x4[1][0] = matrix10;matrix4x4[1][1] = matrix11;matrix4x4[1][2] = matrix12;matrix4x4[1][3] = matrix13;
        matrix4x4[2][0] = matrix20;matrix4x4[2][1] = matrix21;matrix4x4[2][2] = matrix22;matrix4x4[2][3] = matrix23;
        matrix4x4[3][0] = matrix30;matrix4x4[3][1] = matrix31;matrix4x4[3][2] = matrix32;matrix4x4[3][3] = matrix33;
        return matrix4x4;
    }

    private double[][] setMatrix4x4Standardize() {
        double[][] matrixStandardize = new double[4][4];
        double sumColumn0 = matrix4x4[0][0] + matrix4x4[1][0] + matrix4x4[2][0] + matrix4x4[3][0];
        double sumColumn1 = matrix4x4[0][1] + matrix4x4[1][1] + matrix4x4[2][1] + matrix4x4[3][1];
        double sumColumn2 = matrix4x4[0][2] + matrix4x4[1][2] + matrix4x4[2][2] + matrix4x4[3][2];
        double sumColumn3 = matrix4x4[0][3] + matrix4x4[1][3] + matrix4x4[2][3] + matrix4x4[3][3];

        matrixStandardize[0][0] = matrix4x4[0][0]/sumColumn0;matrixStandardize[0][1] = matrix4x4[0][1]/sumColumn1;matrixStandardize[0][2] = matrix4x4[0][2]/sumColumn2;matrixStandardize[0][3] = matrix4x4[0][3]/sumColumn3;
        matrixStandardize[1][0] = matrix4x4[1][0]/sumColumn0;matrixStandardize[1][1] = matrix4x4[1][1]/sumColumn1;matrixStandardize[1][2] = matrix4x4[1][2]/sumColumn2;matrixStandardize[1][3] = matrix4x4[1][3]/sumColumn3;
        matrixStandardize[2][0] = matrix4x4[2][0]/sumColumn0;matrixStandardize[2][1] = matrix4x4[2][1]/sumColumn1;matrixStandardize[2][2] = matrix4x4[2][2]/sumColumn2;matrixStandardize[2][3] = matrix4x4[2][3]/sumColumn3;
        matrixStandardize[3][0] = matrix4x4[3][0]/sumColumn0;matrixStandardize[3][1] = matrix4x4[3][1]/sumColumn1;matrixStandardize[3][2] = matrix4x4[3][2]/sumColumn2;matrixStandardize[3][3] = matrix4x4[3][3]/sumColumn3;

        return matrixStandardize;
    }

    private double[] setMatrix4x4Weight() {
        double[] matrixWeight = new double[4];
        matrixWeight[0] = (matrix4x4Standardize[0][0] + matrix4x4Standardize[0][1] + matrix4x4Standardize[0][2] + matrix4x4Standardize[0][3]) / 4;
        matrixWeight[1] = (matrix4x4Standardize[1][0] + matrix4x4Standardize[1][1] + matrix4x4Standardize[1][2] + matrix4x4Standardize[1][3]) / 4;
        matrixWeight[2] = (matrix4x4Standardize[2][0] + matrix4x4Standardize[2][1] + matrix4x4Standardize[2][2] + matrix4x4Standardize[2][3]) / 4;
        matrixWeight[3] = (matrix4x4Standardize[3][0] + matrix4x4Standardize[3][1] + matrix4x4Standardize[3][2] + matrix4x4Standardize[3][3]) / 4;
        return matrixWeight;
    }

    private double[] setMatrix4x4SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[2];
        double sumWeight = inputWeight[0] + inputWeight[1] + inputWeight[2] + inputWeight[3];
        matrixSubWeight[0] = (inputWeight[0] * inputMainWeight) / sumWeight;
        matrixSubWeight[1] = (inputWeight[1] * inputMainWeight) / sumWeight;
        matrixSubWeight[2] = (inputWeight[2] * inputMainWeight) / sumWeight;
        matrixSubWeight[3] = (inputWeight[3] * inputMainWeight) / sumWeight;
        return matrixSubWeight;
    }

    /**
     * =======================================================================================================================
     * Calculation Section (3x3 matrix)
     * =======================================================================================================================
     */

    private double[][] setMatrix3x3(double matrix00, double matrix01, double matrix02,
                                    double matrix10, double matrix11, double matrix12,
                                    double matrix20, double matrix21, double matrix22) {
        double[][] matrix = new double[3][3];
        matrix[0][0] = matrix00;matrix[0][1] = matrix01;matrix[0][2] = matrix02;
        matrix[1][0] = matrix10;matrix[1][1] = matrix11;matrix[1][2] = matrix12;
        matrix[2][0] = matrix20;matrix[2][1] = matrix21;matrix[2][2] = matrix22;
        return matrix;
    }

    private double[][] setMatrix3x3Standardize() {
        double[][] matrixStandardize = new double[4][4];
        double sumColumn0 = matrix3x3[0][0] + matrix3x3[1][0] + matrix3x3[2][0];
        double sumColumn1 = matrix3x3[0][1] + matrix3x3[1][1] + matrix3x3[2][1];
        double sumColumn2 = matrix3x3[0][2] + matrix3x3[1][2] + matrix3x3[2][2];

        matrixStandardize[0][0] = matrix3x3[0][0]/sumColumn0;matrixStandardize[0][1] = matrix3x3[0][1]/sumColumn1;matrixStandardize[0][2] = matrix3x3[0][2]/sumColumn2;
        matrixStandardize[1][0] = matrix3x3[1][0]/sumColumn0;matrixStandardize[1][1] = matrix3x3[1][1]/sumColumn1;matrixStandardize[1][2] = matrix3x3[1][2]/sumColumn2;
        matrixStandardize[2][0] = matrix3x3[2][0]/sumColumn0;matrixStandardize[2][1] = matrix3x3[2][1]/sumColumn1;matrixStandardize[2][2] = matrix3x3[2][2]/sumColumn2;
        return matrixStandardize;
    }

    private double[] setMatrix3x3Weight() {
        double[] matrixWeight = new double[4];
        matrixWeight[0] = (matrix3x3Standardize[0][0] + matrix3x3Standardize[0][1] + matrix3x3Standardize[0][2]) / 3;
        matrixWeight[1] = (matrix3x3Standardize[1][0] + matrix3x3Standardize[1][1] + matrix3x3Standardize[1][2]) / 3;
        matrixWeight[2] = (matrix3x3Standardize[2][0] + matrix3x3Standardize[2][1] + matrix3x3Standardize[2][2]) / 3;
        return matrixWeight;
    }

    private double[] setMatrix3x3SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[3];
        double sumWeight = inputWeight[0] + inputWeight[1] + inputWeight[2];
        matrixSubWeight[0] = (inputWeight[0] * inputMainWeight) / sumWeight;
        matrixSubWeight[1] = (inputWeight[1] * inputMainWeight) / sumWeight;
        matrixSubWeight[2] = (inputWeight[2] * inputMainWeight) / sumWeight;
        return matrixSubWeight;
    }


    /**
     * =======================================================================================================================
     * Calculation Section (2x2 matrix)
     * =======================================================================================================================
     */

    private double[][] setMatrix2x2(double input00, double input01,
                                     double input10, double input11) {
        double[][] matrix = new double[2][2];
        matrix[0][0] = input00; matrix[0][1] = input01;
        matrix[1][0] = input10; matrix[1][1] = input11;
        return matrix;
    }

    private double[][] setMatrix2x2Standardize() {
        double[][] matrixStandardize = new double[2][2];
        double sumColumn0 = matrix2x2[0][0] + matrix2x2[1][0];
        double sumColumn1 = matrix2x2[0][1] + matrix2x2[1][1];

        matrixStandardize[0][0] = matrix2x2[0][0]/sumColumn0;matrixStandardize[0][1] = matrix2x2[0][1]/sumColumn1;
        matrixStandardize[1][0] = matrix2x2[1][0]/sumColumn0;matrixStandardize[1][1] = matrix2x2[1][1]/sumColumn1;
        return matrixStandardize;
    }

    private double[] setMatrix2x2Weight() {
        double[] matrixWeight = new double[2];
        matrixWeight[0] = (matrix2x2Standardize[0][0] + matrix2x2Standardize[0][1])/2;
        matrixWeight[1] = (matrix2x2Standardize[1][0] + matrix2x2Standardize[1][1])/2;
        return matrixWeight;
    }

    private double[] setMatrix2x2SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[2];
        double sumWeight = inputWeight[0] + inputWeight[1];
        matrixSubWeight[0] = (inputWeight[0] * inputMainWeight) /sumWeight;
        matrixSubWeight[1] = (inputWeight[1] * inputMainWeight) /sumWeight;
        return matrixSubWeight;
    }

}
