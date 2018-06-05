package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PowerEstimator {

    private ExecutionTask<String> executionTask;
    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;

    private BatteryManager batteryManager;
    private Context context;
    private long currentAvg; // in microampere
    private long currentNow; // in microampere
    private int voltageAvg; // in millivolt
    private int voltageNow; // in millivolt
    private long batteryRemaining; // in microampere-hours
    private long batteryRemainingPercent; // in percent
    private long batteryRemainingEnergy; // in nanowatt-hours
    private int batteryTemperature; // in degree celcius
    private int batteryPercentage; // in percent

    public PowerEstimator(Context context) {
        this.context = context;
        batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    public void start() {
        // set all value to default
        currentAvg = 0;
        currentNow = 0;
        voltageAvg = 0;
        voltageNow = 0;
        batteryRemaining = 0;
        batteryRemainingPercent = 0;
        batteryRemainingEnergy = 0;
        batteryTemperature = 0;
        batteryPercentage = 0;

        // start scheduler to measure battery properties
        int N = Runtime.getRuntime().availableProcessors();
        executionTask = new ExecutionTask<>(N, N*2);
        scheduler = executionTask.scheduleWithThreadPoolExecutor(new ReadPowerData(), 0, 100, TimeUnit.MILLISECONDS);
        future = executionTask.getFuture();
    }

    public void stop() {
        // stop the scheduler
        future.cancel(true);
        scheduler.shutdownNow();
    }

    public long getCurrentAvg() {
        return currentAvg;
    }

    public long getCurrentNow() {
        return currentNow;
    }

    public int getVoltageAvg() {
        return voltageAvg;
    }

    public int getVoltageNow() {
        return voltageNow;
    }

    public long getBatteryRemaining() {
        return batteryRemaining;
    }

    public long getBatteryRemainingPercent() {
        return batteryRemainingPercent;
    }

    public long getBatteryRemainingEnergy() {
        return batteryRemainingEnergy;
    }

    public int getBatteryTemperature() {
        return batteryTemperature;
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    private class ReadPowerData implements Runnable {
        @Override
        public void run() {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    currentAvg = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                    currentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    batteryRemaining = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    batteryRemainingPercent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    batteryRemainingEnergy = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                } else {
                    String current = readBatteryInfo("/sys/class/power_supply/battery/current_now", "current_now");
                    if(current != null) {currentNow = Integer.valueOf(current);}

                    current = readBatteryInfo("/sys/class/power_supply/battery/current_avg", "current_avg");
                    if(current != null) {currentAvg = Integer.valueOf(current);}
                }

                String voltage = readBatteryInfo("/sys/class/power_supply/battery/voltage_now", "voltage_now");
                if(voltage != null) {voltageNow = Integer.valueOf(voltage);}

                if(batteryRemainingPercent == 0) {
                    String capacity = readBatteryInfo("/sys/class/power_supply/battery/capacity", "capacity");
                    if(capacity != null) {batteryRemainingPercent = Integer.valueOf(capacity);}
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String readBatteryInfo(String fileName, String name) {
        BufferedReader bufferedReader = null;
        String line = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(fileName));
            while ((line = bufferedReader.readLine()) != null) { Log.d(name, line); return line;}
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bufferedReader != null)
                    bufferedReader.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return line;
    }


}
