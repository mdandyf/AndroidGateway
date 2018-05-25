package com.uni.stuttgart.ipvs.androidgateway.helper.matrix;

public class MatrixComputation implements IMatrixComputation {

    private int numberRows;
    private int numberColumns;

    public MatrixComputation(int rows, int columns) {
        this.numberRows = rows;
        this.numberColumns = columns;
    }

    @Override
    public double[][] getMatrixIdentity() {
        double[][] floats = new double[this.numberRows][this.numberColumns];
        for (int i = 0; i < this.numberRows; i++) {
            for (int j = 0; j < this.numberColumns; j++) {
                if(i == j) {floats[i][j] = 1;}
                else {floats[i][j] = 0;}
            }
        }

        return floats;
    }

    @Override
    public double[][] getMatrixZero() {
        double[][] floats = new double[this.numberRows][this.numberColumns];
        for (int i = 0; i < this.numberRows; i++) {
            for (int j = 0; j < this.numberColumns; j++) {
                floats[i][j] = 0;
            }
        }

        return floats;
    }

    @Override
    public double[][] changeMatrixValue(double[][] inputMatrix, int row, int column, double value) {
        double[][] outputMatrix = inputMatrix;
        outputMatrix[row][column] = value;
        return outputMatrix;
    }

    @Override
    public double[][] getMatrixStandardize(double[][] inputMatrix) {
        double[] sumColumn = new double[this.numberColumns];
        double[][] matrixStandardize = new double[this.numberRows][this.numberColumns];

        for(int i = 0; i < this.numberRows; i++) {
            for(int j = 0; j < this.numberColumns; j++) {
                sumColumn[j] = sumColumn[j] + inputMatrix[i][j];
            }
        }

        for(int i = 0; i < this.numberRows; i++) {
            for (int j = 0; j < this.numberColumns; j++) {
                matrixStandardize[i][j] = inputMatrix[i][j] / sumColumn[j];
            }
        }


        return matrixStandardize;
    }

    @Override
    public double[] getMatrixWeight(double[][] matrixStandardize) {
        double[] matrixWeight = new double[this.numberRows];
        for(int i = 0; i < this.numberRows; i++) {
            for(int j = 0; j < this.numberColumns; j++) {
                matrixWeight[i] = matrixWeight[i] + matrixStandardize[i][j];
            }
            matrixWeight[i] = matrixWeight[i] / this.numberRows;
        }
        return matrixWeight;
    }

    @Override
    public double[] getMatrixSubWeight(double[] inputMatrix1, double inputMainWeight) {
        double sumWeight = 0;
        for(int i = 0; i < this.numberRows; i++) {
            sumWeight = sumWeight + inputMatrix1[i];
        }

        double[] matrixSubWeight = new double[this.numberRows];
        for(int i = 0; i < this.numberRows; i++) {
            matrixSubWeight[i] = (inputMatrix1[i] * inputMainWeight) / sumWeight;
        }

        return matrixSubWeight;
    }

    @Override
    public double[][] matrixMultiplication(double[][] a, double[][] b) throws Exception {
        if (!isValidMatrix(a, b)) throw new Exception("The matrices are invalid");

        int row = a.length;
        int col = b[0].length;
        int p = b.length;
        double[][] result = new double[row][col];

        for (int i = 0; i < row; i++) {
            for(int j = 0; j < col; j++){
                for(int k = 0; k < p; k++){
                    result[i][j] += a[i][k] * b [k][j];
                }
            }
        }

        return result;
    }

    @Override
    public boolean isValidMatrix(double[][] a, double[][] b) {
        if (a.length < 1 || b.length < 1 || a[0].length < 1 || b[0].length < 1) return false;
        if (a[0].length != b.length) return false;
        return true;
    }

    @Override
    public void finalizeMatrices() throws Throwable {
        finalize();
    }
}
