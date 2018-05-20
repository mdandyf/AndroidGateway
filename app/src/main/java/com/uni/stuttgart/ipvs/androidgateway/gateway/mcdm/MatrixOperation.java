package com.uni.stuttgart.ipvs.androidgateway.gateway.mcdm;

public class MatrixOperation {

    public static double[][] matrix5x5;
    public static double[][] matrix5x5Standardize;
    public static double[] matrix5x5Weight;
    public static double[] matrix5x5SubWeight;

    public static double[][] matrix4x4;
    public static double[][] matrix4x4Standardize;
    public static double[] matrix4x4Weight;
    public static double[] matrix4x4SubWeight;

    public static double[][] matrix3x3;
    public static double[][] matrix3x3Standardize;
    public static double[] matrix3x3Weight;
    public static double[] matrix3x3SubWeight;

    public static double[][] matrix2x2;
    public static double[][] matrix2x2Standardize;
    public static double[] matrix2x2Weight;
    public static double[] matrix2x2SubWeight;

    /**
     * =======================================================================================================================
     * Calculation Section (7x7 matrix)
     * =======================================================================================================================
     */

    /**
     * =======================================================================================================================
     * Calculation Section (6x6 matrix)
     * =======================================================================================================================
     */

    /**
     * =======================================================================================================================
     * Calculation Section (5x5 matrix)
     * =======================================================================================================================
     */

    public static double[][] setMatrix5x5(double matrix00, double matrix01, double matrix02, double matrix03, double matrix04,
                                          double matrix10, double matrix11, double matrix12, double matrix13, double matrix14,
                                          double matrix20, double matrix21, double matrix22, double matrix23, double matrix24,
                                          double matrix30, double matrix31, double matrix32, double matrix33, double matrix34,
                                          double matrix40, double matrix41, double matrix42, double matrix43, double matrix44) {
        double[][] matrix5x5 = new double[5][5];
        matrix5x5[0][0] = matrix00;matrix5x5[0][1] = matrix01;matrix5x5[0][2] = matrix02;matrix5x5[0][3] = matrix03;matrix5x5[0][4] = matrix04;
        matrix5x5[1][0] = matrix10;matrix5x5[1][1] = matrix11;matrix5x5[1][2] = matrix12;matrix5x5[1][3] = matrix13;matrix5x5[1][4] = matrix14;
        matrix5x5[2][0] = matrix20;matrix5x5[2][1] = matrix21;matrix5x5[2][2] = matrix22;matrix5x5[2][3] = matrix23;matrix5x5[2][4] = matrix24;
        matrix5x5[3][0] = matrix30;matrix5x5[3][1] = matrix31;matrix5x5[3][2] = matrix32;matrix5x5[3][3] = matrix33;matrix5x5[3][4] = matrix34;
        matrix5x5[4][0] = matrix40;matrix5x5[4][1] = matrix41;matrix5x5[4][2] = matrix42;matrix5x5[4][3] = matrix43;matrix5x5[4][4] = matrix44;
        return matrix5x5;
    }

    public static double[][] setMatrix5x5Standardize() {
        double[][] matrixStandardize = new double[5][5];
        double sumColumn0 = matrix5x5[0][0] + matrix5x5[1][0] + matrix5x5[2][0] + matrix5x5[3][0]+ matrix5x5[4][0];
        double sumColumn1 = matrix5x5[0][1] + matrix5x5[1][1] + matrix5x5[2][1] + matrix5x5[3][1]+ matrix5x5[4][1];
        double sumColumn2 = matrix5x5[0][2] + matrix5x5[1][2] + matrix5x5[2][2] + matrix5x5[3][2]+ matrix5x5[4][2];
        double sumColumn3 = matrix5x5[0][3] + matrix5x5[1][3] + matrix5x5[2][3] + matrix5x5[3][3]+ matrix5x5[4][3];
        double sumColumn4 = matrix5x5[0][4] + matrix5x5[1][4] + matrix5x5[2][4] + matrix5x5[3][4]+ matrix5x5[4][4];

        matrixStandardize[0][0] = matrix5x5[0][0]/sumColumn0;matrixStandardize[0][1] = matrix5x5[0][1]/sumColumn1;matrixStandardize[0][2] = matrix5x5[0][2]/sumColumn2;matrixStandardize[0][3] = matrix5x5[0][3]/sumColumn3;matrixStandardize[0][4] = matrix5x5[0][4]/sumColumn4;
        matrixStandardize[1][0] = matrix5x5[1][0]/sumColumn0;matrixStandardize[1][1] = matrix5x5[1][1]/sumColumn1;matrixStandardize[1][2] = matrix5x5[1][2]/sumColumn2;matrixStandardize[1][3] = matrix5x5[1][3]/sumColumn3;matrixStandardize[1][4] = matrix5x5[1][4]/sumColumn4;
        matrixStandardize[2][0] = matrix5x5[2][0]/sumColumn0;matrixStandardize[2][1] = matrix5x5[2][1]/sumColumn1;matrixStandardize[2][2] = matrix5x5[2][2]/sumColumn2;matrixStandardize[2][3] = matrix5x5[2][3]/sumColumn3;matrixStandardize[2][4] = matrix5x5[2][4]/sumColumn4;
        matrixStandardize[3][0] = matrix5x5[3][0]/sumColumn0;matrixStandardize[3][1] = matrix5x5[3][1]/sumColumn1;matrixStandardize[3][2] = matrix5x5[3][2]/sumColumn2;matrixStandardize[3][3] = matrix5x5[3][3]/sumColumn3;matrixStandardize[3][4] = matrix5x5[3][4]/sumColumn4;
        matrixStandardize[4][0] = matrix5x5[4][0]/sumColumn0;matrixStandardize[4][1] = matrix5x5[4][1]/sumColumn1;matrixStandardize[4][2] = matrix5x5[4][2]/sumColumn2;matrixStandardize[4][3] = matrix5x5[4][3]/sumColumn3;matrixStandardize[4][4] = matrix5x5[4][4]/sumColumn4;

        return matrixStandardize;
    }

    public static double[] setMatrix5x5Weight() {
        double[] matrixWeight = new double[5];
        matrixWeight[0] = (matrix5x5Standardize[0][0] + matrix5x5Standardize[0][1] + matrix5x5Standardize[0][2] + matrix5x5Standardize[0][3] + matrix5x5Standardize[0][4]) / 5;
        matrixWeight[1] = (matrix5x5Standardize[1][0] + matrix5x5Standardize[1][1] + matrix5x5Standardize[1][2] + matrix5x5Standardize[1][3] + matrix5x5Standardize[1][4]) / 5;
        matrixWeight[2] = (matrix5x5Standardize[2][0] + matrix5x5Standardize[2][1] + matrix5x5Standardize[2][2] + matrix5x5Standardize[2][3] + matrix5x5Standardize[2][4]) / 5;
        matrixWeight[3] = (matrix5x5Standardize[3][0] + matrix5x5Standardize[3][1] + matrix5x5Standardize[3][2] + matrix5x5Standardize[3][3] + matrix5x5Standardize[3][4]) / 5;
        matrixWeight[4] = (matrix5x5Standardize[4][0] + matrix5x5Standardize[4][1] + matrix5x5Standardize[4][2] + matrix5x5Standardize[4][3] + matrix5x5Standardize[4][4]) / 5;
        return matrixWeight;
    }

    public static double[] setMatrix5x5SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[5];
        double sumWeight = inputWeight[0] + inputWeight[1] + inputWeight[2] + inputWeight[3] + inputWeight[4];
        matrixSubWeight[0] = (inputWeight[0] * inputMainWeight) / sumWeight;
        matrixSubWeight[1] = (inputWeight[1] * inputMainWeight) / sumWeight;
        matrixSubWeight[2] = (inputWeight[2] * inputMainWeight) / sumWeight;
        matrixSubWeight[3] = (inputWeight[3] * inputMainWeight) / sumWeight;
        matrixSubWeight[4] = (inputWeight[4] * inputMainWeight) / sumWeight;
        return matrixSubWeight;
    }

    /**
     * =======================================================================================================================
     * Calculation Section (4x4 matrix)
     * =======================================================================================================================
     */

    public static double[][] setMatrix4x4(double matrix00, double matrix01, double matrix02, double matrix03,
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

    public static double[][] setMatrix4x4Standardize() {
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

    public static double[] setMatrix4x4Weight() {
        double[] matrixWeight = new double[4];
        matrixWeight[0] = (matrix4x4Standardize[0][0] + matrix4x4Standardize[0][1] + matrix4x4Standardize[0][2] + matrix4x4Standardize[0][3]) / 4;
        matrixWeight[1] = (matrix4x4Standardize[1][0] + matrix4x4Standardize[1][1] + matrix4x4Standardize[1][2] + matrix4x4Standardize[1][3]) / 4;
        matrixWeight[2] = (matrix4x4Standardize[2][0] + matrix4x4Standardize[2][1] + matrix4x4Standardize[2][2] + matrix4x4Standardize[2][3]) / 4;
        matrixWeight[3] = (matrix4x4Standardize[3][0] + matrix4x4Standardize[3][1] + matrix4x4Standardize[3][2] + matrix4x4Standardize[3][3]) / 4;
        return matrixWeight;
    }

    public static double[] setMatrix4x4SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[4];
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

    public static double[][] setMatrix3x3(double matrix00, double matrix01, double matrix02,
                                    double matrix10, double matrix11, double matrix12,
                                    double matrix20, double matrix21, double matrix22) {
        double[][] matrix = new double[3][3];
        matrix[0][0] = matrix00;matrix[0][1] = matrix01;matrix[0][2] = matrix02;
        matrix[1][0] = matrix10;matrix[1][1] = matrix11;matrix[1][2] = matrix12;
        matrix[2][0] = matrix20;matrix[2][1] = matrix21;matrix[2][2] = matrix22;
        return matrix;
    }

    public static double[][] setMatrix3x3Standardize() {
        double[][] matrixStandardize = new double[4][4];
        double sumColumn0 = matrix3x3[0][0] + matrix3x3[1][0] + matrix3x3[2][0];
        double sumColumn1 = matrix3x3[0][1] + matrix3x3[1][1] + matrix3x3[2][1];
        double sumColumn2 = matrix3x3[0][2] + matrix3x3[1][2] + matrix3x3[2][2];

        matrixStandardize[0][0] = matrix3x3[0][0]/sumColumn0;matrixStandardize[0][1] = matrix3x3[0][1]/sumColumn1;matrixStandardize[0][2] = matrix3x3[0][2]/sumColumn2;
        matrixStandardize[1][0] = matrix3x3[1][0]/sumColumn0;matrixStandardize[1][1] = matrix3x3[1][1]/sumColumn1;matrixStandardize[1][2] = matrix3x3[1][2]/sumColumn2;
        matrixStandardize[2][0] = matrix3x3[2][0]/sumColumn0;matrixStandardize[2][1] = matrix3x3[2][1]/sumColumn1;matrixStandardize[2][2] = matrix3x3[2][2]/sumColumn2;
        return matrixStandardize;
    }

    public static double[] setMatrix3x3Weight() {
        double[] matrixWeight = new double[4];
        matrixWeight[0] = (matrix3x3Standardize[0][0] + matrix3x3Standardize[0][1] + matrix3x3Standardize[0][2]) / 3;
        matrixWeight[1] = (matrix3x3Standardize[1][0] + matrix3x3Standardize[1][1] + matrix3x3Standardize[1][2]) / 3;
        matrixWeight[2] = (matrix3x3Standardize[2][0] + matrix3x3Standardize[2][1] + matrix3x3Standardize[2][2]) / 3;
        return matrixWeight;
    }

    public static double[] setMatrix3x3SubWeight(double[] inputWeight, double inputMainWeight) {
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

    public static double[][] setMatrix2x2(double input00, double input01,
                                    double input10, double input11) {
        double[][] matrix = new double[2][2];
        matrix[0][0] = input00; matrix[0][1] = input01;
        matrix[1][0] = input10; matrix[1][1] = input11;
        return matrix;
    }

    public static double[][] setMatrix2x2Standardize() {
        double[][] matrixStandardize = new double[2][2];
        double sumColumn0 = matrix2x2[0][0] + matrix2x2[1][0];
        double sumColumn1 = matrix2x2[0][1] + matrix2x2[1][1];

        matrixStandardize[0][0] = matrix2x2[0][0]/sumColumn0;matrixStandardize[0][1] = matrix2x2[0][1]/sumColumn1;
        matrixStandardize[1][0] = matrix2x2[1][0]/sumColumn0;matrixStandardize[1][1] = matrix2x2[1][1]/sumColumn1;
        return matrixStandardize;
    }

    public static double[] setMatrix2x2Weight() {
        double[] matrixWeight = new double[2];
        matrixWeight[0] = (matrix2x2Standardize[0][0] + matrix2x2Standardize[0][1])/2;
        matrixWeight[1] = (matrix2x2Standardize[1][0] + matrix2x2Standardize[1][1])/2;
        return matrixWeight;
    }

    public static double[] setMatrix2x2SubWeight(double[] inputWeight, double inputMainWeight) {
        double[] matrixSubWeight = new double[2];
        double sumWeight = inputWeight[0] + inputWeight[1];
        matrixSubWeight[0] = (inputWeight[0] * inputMainWeight) /sumWeight;
        matrixSubWeight[1] = (inputWeight[1] * inputMainWeight) /sumWeight;
        return matrixSubWeight;
    }
}
