package com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PowerEstimator;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ProcessPriority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class FixedPriority extends AsyncTask<Void, Void, Void> {

    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds

    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledThreadPoolExecutor scheduler2;
    private ScheduledFuture<?> future;
    private ScheduledFuture<?> future2;

    private IGatewayService iGatewayService;
    private PowerEstimator mServicePE;

    private boolean mBoundPE;
    private boolean mScanning;
    private Context context;
    private boolean mProcessing;
    private int cycleCounter = 0;
    private int maxConnectTime = 0;
    private long powerUsage = 0;

    private ProcessPriority processConnecting;
    private ProcessPriority processPowerMeasurement;


    public FixedPriority(Context context, boolean mProcessing, IGatewayService iGatewayService, PowerEstimator mServicePE, boolean mBoundPE) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.mServicePE = mServicePE;
        this.mBoundPE = mBoundPE;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        scheduler = new ScheduledThreadPoolExecutor(5);
        future = scheduler.scheduleAtFixedRate(new FPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        scheduler2 = new ScheduledThreadPoolExecutor(5);
        future2 = scheduler2.scheduleAtFixedRate(new FPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS); // refresh db state after 5 minutes
        return null;
    }

    @Override
    public void onCancelled(Void aVoid) {
        super.onCancelled(aVoid);
        cancelled();
    }

    @Override
    public void onCancelled() {
        cancelled();
    }

    private void cancelled() {
        mProcessing = false;
        future.cancel(true);future2.cancel(true);
        scheduler.shutdownNow();scheduler2.shutdownNow();
        scheduler = null;scheduler2 = null;
        if(mScanning) { try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null);iGatewayService.execScanningQueue();mScanning = false; } catch (RemoteException e) { e.printStackTrace(); }}
        if (processConnecting != null) { processConnecting.interruptThread(); }
        if (processPowerMeasurement != null) { processPowerMeasurement.interruptThread(); }
    }

    private class FPStartScanning implements Runnable {

        @Override
        public void run() {
            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                mProcessing = true;
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {

                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) { iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null); }
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();

                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                    waitThread(SCAN_TIME/2);

                    if(isCancelled()) {future.cancel(false);return;}

                    stop();
                    waitThread(100);

                    if(isCancelled()) {future.cancel(false);return;}
                    connectFP();
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                    waitThread(SCAN_TIME);

                    if(isCancelled()) {future.cancel(false);stop();return;}

                    stop();
                    waitThread(100);

                    if(isCancelled()) {future.cancel(false);return;}
                    connectRR();
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            if(mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = iGatewayService.getScanState();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if(isCancelled()) {future.cancel(false);return;}
        }

        private void connectRR() {
            try {
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
                } else {return;}

                // do connecting by Round Robin
                for (final BluetoothDevice device : scanResults) {
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                    powerUsage = 0;
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();

                    processPowerMeasurement = new ProcessPriority(10);
                    processPowerMeasurement.newThread(doMeasurePower()).start();

                    processUserChoiceAlert(device.getAddress(), device.getName());

                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) { return; }
                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);

                    processConnecting.interruptThread();
                    processPowerMeasurement.interruptThread();

                    iGatewayService.updateDatabaseDevicePowerUsage(device.getAddress(), powerUsage);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void connectFP() {
            try {
                Map<BluetoothDevice, Integer> mapRankedDevices = doRankDevice(iGatewayService.getScanResults());
                if(mapRankedDevices.size() <0 ) {return;}

                // calculate timer for connection (to obtain Round Robin Scheduling)
                int remainingTime = PROCESSING_TIME - SCAN_TIME;;
                maxConnectTime = remainingTime / mapRankedDevices.size();
                broadcastUpdate("\n");
                broadcastUpdate("Connecting to " + mapRankedDevices.size() + " device(s)");
                broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

                for(Map.Entry entry : mapRankedDevices.entrySet()) {
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");

                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();
                    processUserChoiceAlert(device.getAddress(), device.getName());
                    // set timer to xx seconds
                    if((Integer) entry.getValue() == 1) {
                        waitThread(maxConnectTime);
                    } else {
                        waitThread(maxConnectTime/2); // only half of normal connection will be used to connect
                    }
                    if (!mProcessing) { return; }
                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(10);
                    processConnecting.interruptThread();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // implementation of Ranking Devices based on Fixed Priority Algorithm
        private Map<BluetoothDevice, Integer> doRankDevice(List<BluetoothDevice> devices) {
            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start ranking device...");

                Map<BluetoothDevice, Integer> rankedDevices = new HashMap<>();
                Map<BluetoothDevice, Integer> mapPriority = new HashMap<>();
                DataSorterHelper<String> sortString = new DataSorterHelper<>();
                DataSorterHelper<BluetoothDevice> sortDevice = new DataSorterHelper<>();

                // check for priority in case of power estimator
                Map<BluetoothDevice, Long> mapPowerUsage = new HashMap<>();
                for(BluetoothDevice device : devices) { powerUsage = iGatewayService.getDevicePowerUsage(device.getAddress());if(powerUsage > 0) {mapPowerUsage.put(device, powerUsage);} }

                if(mapPowerUsage.size() > 0) {
                    // sort device based on lower energy usage
                    broadcastUpdate("Sort devices based on power usage...");
                    mapPowerUsage = sortDevice.sortMapByComparatorLong(mapPowerUsage, true);
                    devices = new ArrayList<>();
                    for(Map.Entry entry : mapPowerUsage.entrySet()) {
                        devices.add((BluetoothDevice) entry.getKey());
                    }
                }


                Map<String, Integer> mapDevicesRssi = new HashMap<>();
                for(BluetoothDevice device : devices) {mapDevicesRssi.put(device.getAddress(), iGatewayService.getDeviceRSSI(device.getAddress()));}
                mapDevicesRssi = sortString.sortMapByComparator(mapDevicesRssi, true); // sort based on RSSI in ascending (true)

                // check for priority in case of RSSI
                broadcastUpdate("Rank devices based on RSSI...");
                for(Map.Entry entry : mapDevicesRssi.entrySet()) {
                    for(BluetoothDevice device : devices) {
                        if(device.getAddress().equals((String) entry.getKey())) {
                            if((Integer) entry.getValue() >= -80) { // only rssi bigger than -90 dBm that will be prioritized (give 1)
                                mapPriority.put(device, 1);
                            } else {
                                mapPriority.put(device, 2);
                            }
                        }
                    }
                }

                // check for priority in case of active device
                broadcastUpdate("Rank devices based on state...");
                for(BluetoothDevice device : devices) {
                    int priority = mapPriority.get(device);
                    if(priority >= 2) {
                        String deviceState = iGatewayService.getDeviceState(device.getAddress());
                        if(deviceState.equals("active")) {
                            mapPriority.remove(device);
                            mapPriority.put(device, 1);
                        } else if(deviceState.equals("inactive")) {
                            mapPriority.remove(device);
                            mapPriority.put(device, 3);
                        }
                    }
                }

                // check for priority in case of user choice
                broadcastUpdate("Rank devices based on user choice...");
                for(BluetoothDevice device : devices) {
                    int priority = mapPriority.get(device);
                    if(priority >= 2) {
                        String userChoice = iGatewayService.getDeviceUsrChoice(device.getAddress());
                        if(userChoice.equals("Yes")) {
                            mapPriority.remove(device);
                            mapPriority.put(device, 1);
                        } else {
                            mapPriority.remove(device);
                            mapPriority.put(device, 3);
                        }
                    }
                }

                mapPriority = sortDevice.sortMapByComparator(mapPriority, true);

                for(Map.Entry entry : mapPriority.entrySet()) {
                    switch((Integer) entry.getValue()) {
                        case 1:
                            // Higher priority (With full time connection)
                            rankedDevices.put((BluetoothDevice) entry.getKey(), (Integer) entry.getValue());
                            break;
                        case 2:
                            // Medium priority (Not full time connection)
                            rankedDevices.put((BluetoothDevice) entry.getKey(), (Integer) entry.getValue());
                            break;
                        case 3:
                            // Lower priority (Not added)
                            break;
                    }
                }
                return rankedDevices;
            }catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }


    }

    private class FPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            if (isCancelled()) { future2.cancel(true); return; }
            broadcastUpdate("Update all device states...");
            if(mProcessing) {
                try {
                    iGatewayService.updateAllDeviceStates(null);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                future2.cancel(false);
            }
        }
    }

    private Runnable doMeasurePower() {
        return new Runnable() {
            @Override
            public void run() {
                if(!mBoundPE) {
                    broadcastUpdate("Power Executor Calculation Failed...");
                    return;
                }
                boolean process = true;
                while (process) {
                    int voltage = mServicePE.getVoltageNow();
                    long current = mServicePE.getCurrentNow();

                    if(voltage <= 0) {
                        voltage = voltage * -1;
                    }

                    if(current <= 0) {
                        current = current * -1;
                    }

                    powerUsage = powerUsage + (voltage * current * (10^(-9)));
                    process = false;
                }
            }
        };
    }

    private Runnable doConnecting(final String macAddress) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.doConnect(macAddress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void waitThread(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            context.sendBroadcast(intent);
        }
    }

    private void processUserChoiceAlert(String macAddress, String deviceName) {
        try {
            String userChoice = iGatewayService.getDeviceUsrChoice(macAddress);
            if(deviceName == null) {deviceName = "Unknown";};
            if(userChoice == null || userChoice == "") broadcastAlertDialog("Start Service Interface of Device " + macAddress + "-" + deviceName, macAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void broadcastAlertDialog(String message, String macAddress) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.USER_CHOICE_SERVICE);
            intent.putExtra("message", message);
            intent.putExtra("macAddress", macAddress);
            context.sendBroadcast(intent);
        }
    }

}
