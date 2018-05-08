package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract;

import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.helper.AdRecordHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.DataSorterHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.ProcessPriority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {
    private static final int SCAN_TIME = 10000; // set scanning and reading time to 10 seoonds
    private static final int PROCESSING_TIME = 60000; // set processing time to 60 seconds
    private final IBinder mBinder = new LocalBinder();
    private int maxConnectTime = 0;
    private Context context;
    private ScheduledThreadPoolExecutor scheduler;
    private Intent mIntent;
    private IGatewayService iGatewayService;
    private GatewayPowerEstimator mServicePE;

    private boolean mBound = false;
    private boolean mBoundPE = false;
    private boolean mProcessing = false;
    private boolean mScanning = false;

    private ProcessPriority process;
    private ProcessPriority processConnecting;
    private int cycleCounter;
    private Runnable runnablePeriodic = null;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        cycleCounter = 0;
        mIntent = intent;
        context = this;

        bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to GatewayService...");

        bindService(new Intent(context, GatewayPowerEstimator.class), mConnectionPE, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to PowerEstimator...");

        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockController");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cycleCounter = 0;
        if (scheduler != null && !scheduler.isShutdown()) { scheduler.shutdown(); }
        if(mScanning) { try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);iGatewayService.execScanningQueue();mScanning = false; } catch (RemoteException e) { e.printStackTrace(); }}
        if (processConnecting != null) { processConnecting.interruptThread(); }
        if (process != null) { process.interruptThread(); }
        if(mConnectionPE != null && mBoundPE) {unbindService(mConnectionPE); }
        if(mConnection != null) {unbindService(mConnection); }
        broadcastUpdate("Unbind GatewayController to GatewayService...");
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        mProcessing = false;
        stopService(mIntent);
        stopSelf();
    }

    /**
     * Gateway Controller Binding Section
     */


    @Override
    public IBinder onBind(Intent intent) { context = this; setWakeLock(); return mBinder; }

    @Override
    public boolean onUnbind(Intent intent) {return false; }

    /**
     * Class used for the client for binding to GatewayController.
     */
    public class LocalBinder extends Binder {
        GatewayController getService() {
            // Return this instance of Service so clients can call public methods
            return GatewayController.this;
        }
    }

    /**
     * Gateway Controller Main Program Section
     */

    /**
     * Defines callbacks for service binding via AIDL for IGatewayService
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            iGatewayService = IGatewayService.Stub.asInterface(service);
            mBound = true;
            mProcessing = true;
            broadcastUpdate("GatewayController & GatewayService have bound...");
            broadcastUpdate("\n");
            //doScheduleSemaphore();
            //doScheduleRR();
            //doScheduleEP();
            //doScheduleFEP();
            doScheduleFP();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            try { iGatewayService.setProcessing(mProcessing); } catch (RemoteException e) { e.printStackTrace(); }
        }
    };

    protected ServiceConnection mConnectionPE = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            GatewayPowerEstimator.LocalBinder binder = (GatewayPowerEstimator.LocalBinder) service;
            mServicePE = binder.getService();
            mBoundPE = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBoundPE = false;
        }
    };

    /**
     * Start Scheduling Algorithm Section
     */

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // Scheduling based on waiting for callback connection (Semaphore Scheduling)
    private void doScheduleSemaphore() {
        broadcastUpdate("Start Semaphore Scheduling...");
        process = new ProcessPriority(3);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.setProcessing(mProcessing);
                    doGatewayControllerSemaphore();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of Semaphore Scheduling Gateway Controller
    private void doGatewayControllerSemaphore() {

        mProcessing = true;

        while (mProcessing) {
            broadcastUpdate("\n");
            broadcastUpdate("Start new cycle...");
            cycleCounter++;
            broadcastUpdate("Cycle number " + cycleCounter);
            try {
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = true;

                waitThread(SCAN_TIME);

                // do Normal Scanning Method
                broadcastUpdate("\n");
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = false;

                waitThread(100);
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                // do Semaphore for Connecting method
                for (final BluetoothDevice device : scanResults) {
                    iGatewayService.doConnect(device.getAddress());
                    if (!mProcessing) {
                        return;
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling using Round Robin Method
    private void doScheduleRR() {
        broadcastUpdate("Start Round Robin Scheduling...");
        process = new ProcessPriority(3);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try { iGatewayService.setProcessing(mProcessing); doGatewayControllerRR(); } catch (RemoteException e) { e.printStackTrace(); }

            }
        };

        process.newThread(runnablePeriodic).start();
    }


    // Implementation of Round Robin Scheduling Gateway Controller
    private void doGatewayControllerRR() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        scheduler.scheduleAtFixedRate(new RRStartScanning(), 0, PROCESSING_TIME + 10, MILLISECONDS);
    }

    private class RRStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle...");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                iGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME);
                stop();
                waitThread(100);
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            broadcastUpdate("\n");
            if(mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            try {
                scanResults = iGatewayService.getScanResults();
                // calculate timer for connection (to obtain Round Robin Scheduling)
                if (scanResults.size() != 0) {
                    int remainingTime = PROCESSING_TIME - SCAN_TIME;
                    maxConnectTime = remainingTime / scanResults.size();
                    broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
                } else {return;}

                // do connecting by Round Robin
                for (final BluetoothDevice device : scanResults) {
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) { return; }
                    broadcastUpdate("Wait time finished, disconnected...");
                    iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(100);
                    processConnecting.interruptThread();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    //scheduling using Exhaustive Polling (EP)
    private void doScheduleEP() {
        broadcastUpdate("Start Exhaustive Polling Scheduling...");
        process = new ProcessPriority(3);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.setProcessing(mProcessing);
                    doGatewayControllerEP();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of Scheduling using Exhaustive Polling
    private void doGatewayControllerEP() {
        mProcessing = true;
        try {
            while (mProcessing) {
                // do Exhaustive Polling Part
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {
                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) {
                        iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                    }
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                    waitThread(100);
                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME/2);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME);
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                }

                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();

                // do Connecting by using Semaphore
                for (final BluetoothDevice device : scanResults) {
                    iGatewayService.doConnect(device.getAddress());
                    if (!mProcessing) {
                        return;
                    }
                }

                if(!mProcessing) {return;}

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling using Fair Exhaustive Polling (FEP)
    private void doScheduleFEP() {
        broadcastUpdate("Start Fair Exhaustive Polling Scheduling...");
        process = new ProcessPriority(1);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.setProcessing(mProcessing);
                    doGatewayControllerFEP();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation Fair Exhaustive Polling (FEP) Scheduling Gateway Controller
    private void doGatewayControllerFEP() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        scheduler.scheduleAtFixedRate(new FEPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        scheduler.scheduleAtFixedRate(new FEPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS); // refresh db state after 5 minutes
    }

    private class FEPStartScanning implements Runnable {
        @Override
        public void run() {
            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start new cycle");
                cycleCounter++;
                broadcastUpdate("Cycle number " + cycleCounter);
                // do polling slaves part
                boolean isDataExist = iGatewayService.checkDevice(null);
                if (isDataExist) {
                    List<String> devices = iGatewayService.getListActiveDevices();
                    for (String device : devices) { iGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null); }
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME/2); // if timer fails, force to stop
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME); // if timer fails, force to stop
                }

                stop();
                waitThread(100);
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            broadcastUpdate("\n");
            if(mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = null;
            List<String> devices = null;
            try {
                scanResults = iGatewayService.getScanResults();
                devices = iGatewayService.getListActiveDevices();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            if(devices == null || devices.size() <= 0) { return; }

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : scanResults) {
                try {
                    iGatewayService.updateDatabaseDeviceState(device, "inactive");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (devices.contains(device.getAddress())) {
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(doConnecting(device.getAddress())).start();
                    processUserChoiceAlert(device.getAddress(), device.getName());
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        return;
                    }
                    broadcastUpdate("Wait time finished, disconnected...");
                    try {
                        iGatewayService.doDisconnected(iGatewayService.getCurrentGatt(), "GatewayController");
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    waitThread(10);
                    processConnecting.interruptThread();
                }
            }
        }
    }

    private class FEPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            try {
                iGatewayService.updateAllDeviceStates(null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling based on Fixed Priority
    private void doScheduleFP() {
        broadcastUpdate("Start Fixed Priority Scheduling...");
        process = new ProcessPriority(3);
        runnablePeriodic = new Runnable() {
            @Override
            public void run() {
                try {
                    iGatewayService.setProcessing(mProcessing);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                doGatewayControllerFP();
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of scheduling based on Fixed Priority
    private void doGatewayControllerFP() {
        scheduler = new ScheduledThreadPoolExecutor(10);
        scheduler.scheduleAtFixedRate(new FPStartScanning(), 0, PROCESSING_TIME + 1, MILLISECONDS);
        scheduler.scheduleAtFixedRate(new FPDeviceDbRefresh(), 5 * PROCESSING_TIME, 5 * PROCESSING_TIME, MILLISECONDS); // refresh db state after 5 minutes
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
                    mScanning = false;
                    // do normal scanning only for half of normal scanning time
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME/2);
                } else {
                    // do normal scanning
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME);
                }

                if(!mProcessing) {return;}

                stop();
                waitThread(100);
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            if(mScanning) {
                try {
                    iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                    iGatewayService.execScanningQueue();
                    mScanning = false;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        private void connect() {
            try {
                Map<BluetoothDevice, Integer> mapRankedDevices = doRankDevices(iGatewayService.getScanResults());

                // calculate timer for connection (to obtain Round Robin Scheduling)
                int remainingTime = PROCESSING_TIME - SCAN_TIME;;
                maxConnectTime = remainingTime / mapRankedDevices.size();
                broadcastUpdate("Number of active devices is " + mapRankedDevices.size());
                broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

                for(Map.Entry entry : mapRankedDevices.entrySet()) {
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
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

            }
        }

        // implementation of Ranking Devices based on Fixed Priority Algorithm
        private Map<BluetoothDevice, Integer> doRankDevices(List<BluetoothDevice> devices) {

            try {
                broadcastUpdate("\n");
                broadcastUpdate("Start ranking device...");

                Map<BluetoothDevice, Integer> rankedDevices = new HashMap<>();
                Map<BluetoothDevice, Integer> mapPriority = new HashMap<>();
                DataSorterHelper<String> sortString = new DataSorterHelper<>();
                DataSorterHelper<BluetoothDevice> sortDevice = new DataSorterHelper<>();

                Map<String, Integer> mapDevicesRssi = new HashMap<>();
                for(BluetoothDevice device : devices) {mapDevicesRssi.put(device.getAddress(), iGatewayService.getDeviceRSSI(device.getAddress()));}
                mapDevicesRssi = sortString.sortMapByComparator(mapDevicesRssi, true); // sort based on RSSI in ascending (true)

                // check for priority in case of RSSI
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

                // check for priority in case of user choice
                for(Map.Entry entry : mapPriority.entrySet()) {
                    if((Integer) entry.getValue() >= 2) {
                        BluetoothDevice device = (BluetoothDevice) entry.getKey();
                        String userChoice = iGatewayService.getDeviceUsrChoice(device.getAddress());
                        if(userChoice != null) {
                            if(userChoice.equals("Yes")) {
                                mapPriority.remove(device);
                                mapPriority.put(device, 1);
                            } else if(userChoice.equals("No")) {
                                mapPriority.remove(device);
                                mapPriority.put(device, 3);
                            }
                        }
                    }
                }
                // check for priority in case of power estimator
                for(Map.Entry entry : mapPriority.entrySet()) {
                    if((Integer) entry.getValue() >= 2) {
                        BluetoothDevice device = (BluetoothDevice) entry.getKey();

                    }
                }

                // other priority cases
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
            try {
                iGatewayService.updateAllDeviceStates(null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * End of Algorithm Section
     */

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    /**
     * Start Method Routine
     */

    public void updateDeviceUserChoice(String macAddress, String userChoice) {
        if(mBound) {
            try {
                iGatewayService.updateDatabaseDeviceUsrChoice(macAddress, userChoice);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if(userChoice.equals("Yes")) {processStartServiceInterface();}
    }

    private void processStartServiceInterface() {
        broadcastServiceInterface("Start Service Interface");
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

    private void setWakeLock() {
        if((wakeLock != null) && (!wakeLock.isHeld())) { wakeLock.acquire(); }
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
            sendBroadcast(intent);
        }
    }

    private void broadcastServiceInterface(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.START_SERVICE_INTERFACE);
            intent.putExtra("message", message);
            sendBroadcast(intent);
        }
    }

    private void broadcastAlertDialog(String message, String macAddress) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.USER_CHOICE_SERVICE);
            intent.putExtra("message", message);
            intent.putExtra("macAddress", macAddress);
            sendBroadcast(intent);
        }
    }

}
