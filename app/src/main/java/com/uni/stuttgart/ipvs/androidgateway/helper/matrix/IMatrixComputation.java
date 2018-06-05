package com.uni.stuttgart.ipvs.androidgateway.helper.matrix;

public interface IMatrixComputation {
    public double[][] getMatrixIdentity();
    public double[][] getMatrixZero();
    public double[][] changeMatrixValue(double[][] inputMatrix, int row, int column, double value);
    public double[][] getMatrixStandardize(double[][] inputMatrix);
    public double[] getMatrixWeight(double[][] matrixStandardize);
    public double[] getMatrixSubWeight(double[] inputMatrix1, double inputMainWeight);
    public double[][] matrixMultiplication(double[][] a, double[][] b) throws Exception;
    abstract boolean isValidMatrix(double[][] a, double[][] b);
    abstract void finalizeMatrices() throws Throwable;
}
