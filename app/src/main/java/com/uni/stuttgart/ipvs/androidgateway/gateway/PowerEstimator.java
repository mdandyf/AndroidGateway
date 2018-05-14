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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PowerEstimator extends Service {
    private IBinder iBinder;
    private final IBinder mBinder = new LocalBinder();

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> future;

    private BatteryManager batteryManager;

    private long currentAvg; // in microampere
    private long currentNow; // in microampere
    private int voltageAvg; // in millivolt
    private int voltageNow; // in millivolt
    private long batteryRemaining; // in microampere-hours
    private long batteryRemainingPercent; // in percent
    private long batteryRemainingEnergy; // in nanowatt-hours
    private int batteryTemperature; // in degree celcius
    private int batteryPercentage; // in percent

    @Override
    public void onCreate() {
        super.onCreate();

        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);

        currentAvg = 0;
        currentNow = 0;
        voltageAvg = 0;
        voltageNow = 0;
        batteryRemaining = 0;
        batteryRemainingPercent = 0;
        batteryRemainingEnergy = 0;
        batteryTemperature = 0;
        batteryPercentage = 0;

        // listen to the battery change
        registerReceiver(this.BatteryInfo, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // start scheduler to measure battery properties
        scheduler = new ScheduledThreadPoolExecutor(5);
        future = scheduler.scheduleAtFixedRate(new ReadPowerData(), 0, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // stop the battery change listener
        unregisterReceiver(this.BatteryInfo);
        // stop the scheduler
        future.cancel(true);
        scheduler.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public boolean onUnbind(Intent intent) {return false; }

    /**
     * Class used for the client for binding to GatewayController.
     */
    public class LocalBinder extends Binder {
        PowerEstimator getService() {
            // Return this instance of Service so clients can call public methods
            return PowerEstimator.this;
        }
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
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private BroadcastReceiver BatteryInfo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();

            voltageNow = bundle.getInt("voltage");
            voltageAvg = bundle.getInt("voltage");

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int percent = (level * 100) / scale;
            batteryPercentage = percent;
        }
    };


}
