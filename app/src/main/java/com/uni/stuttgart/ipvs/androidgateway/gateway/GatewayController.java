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
    private GatewayService mGatewayService;
    private boolean mBound = false;
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
        bindService(new Intent(this, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to GatewayService...");
        return START_STICKY;
    }

    /**
     * Class used for the client Binder.
     */
    public class LocalBinder extends Binder {
        GatewayController getService() {
            // Return this instance of Service so clients can call public methods
            return GatewayController.this;
        }
    }

    private void setWakeLock() {
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockController");
        if((wakeLock != null) && (!wakeLock.isHeld())) { wakeLock.acquire(); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cycleCounter = 0;
        if (scheduler != null && !scheduler.isShutdown()) { scheduler.shutdown(); }
        if(mScanning) { mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);mGatewayService.execScanningQueue();mScanning = false; }
        if (processConnecting != null) { processConnecting.interruptThread(); }
        if (process != null) { process.interruptThread(); }
        if(mConnection != null) {unbindService(mConnection); }
        broadcastUpdate("Unbind GatewayController to GatewayService...");
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        mProcessing = false;
        stopService(mIntent);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) { context = this; setWakeLock(); return null; }

    @Override
    public boolean onUnbind(Intent intent) {return false; }

    /**
     * Gateway Controller Section
     */

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            GatewayService.LocalBinder binder = (GatewayService.LocalBinder) service;
            mGatewayService = binder.getService();
            mBound = true;
            mProcessing = true;
            broadcastUpdate("GatewayController & GatewayService have bound...");
            broadcastUpdate("\n");
            //doScheduleSemaphore();
            //doScheduleRR();
            //doScheduleEP();
            doScheduleFEP();
            //doScheduleFP();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mGatewayService.setProcessing(mProcessing);
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
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerSemaphore();
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
            mGatewayService.disconnectGatt();
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = true;

            waitThread(SCAN_TIME);

            // do Normal Scanning Method
            broadcastUpdate("\n");
            mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
            mGatewayService.execScanningQueue();
            mScanning = false;

            waitThread(100);
            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // do Semaphore for Connecting method
            for (final BluetoothDevice device : scanResults) {
                mGatewayService.doConnect(device.getAddress());
                if (!mProcessing) {
                    return;
                }
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
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerRR();
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
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
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
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // calculate timer for connection (to obtain Round Robin Scheduling)
            if (scanResults.size() != 0) {
                int remainingTime = PROCESSING_TIME - SCAN_TIME;
                maxConnectTime = remainingTime / scanResults.size();
                broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");
            }

            // do connecting by Round Robin
            for (final BluetoothDevice device : scanResults) {
                Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                processConnecting = new ProcessPriority(10);
                processConnecting.newThread(runnable).start();
                // set timer to xx seconds
                waitThread(maxConnectTime);
                if (!mProcessing) {
                    return;
                }
                broadcastUpdate("Wait time finished, disconnected...");
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                waitThread(100);
                processConnecting.interruptThread();
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
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerEP();
            }
        };

        process.newThread(runnablePeriodic).start();
    }

    // implementation of Scheduling using Exhaustive Polling
    private void doGatewayControllerEP() {
        mProcessing = true;
        while (mProcessing) {
            // do Exhaustive Polling Part
            broadcastUpdate("\n");
            broadcastUpdate("Start new cycle");
            cycleCounter++;
            broadcastUpdate("Cycle number " + cycleCounter);
            mGatewayService.disconnectGatt();
            boolean isDataExist = mGatewayService.checkDevice(null);
            if (isDataExist) {
                List<String> devices = mGatewayService.getListActiveDevices();
                for (String device : devices) {
                    mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null);
                }
                mGatewayService.execScanningQueue();
                mScanning = false;
                waitThread(100);
                // do normal scanning only for half of normal scanning time
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME/2);
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            } else {
                // do normal scanning
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME);
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }

            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();

            // do Connecting by using Semaphore
            for (final BluetoothDevice device : scanResults) {
                mGatewayService.doConnect(device.getAddress());
                if (!mProcessing) {
                    return;
                }
            }

            if(!mProcessing) {return;}

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
                mGatewayService.setProcessing(mProcessing);
                doGatewayControllerFEP();
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
                boolean isDataExist = mGatewayService.checkDevice(null);
                if (isDataExist) {
                    broadcastServiceInterface("Start Service Interface");
                    List<String> devices = mGatewayService.getListActiveDevices();
                    for (String device : devices) { mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null); }
                    mGatewayService.execScanningQueue();
                    mScanning = false;
                    // do normal scanning only for half of normal scanning time
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
                    mScanning = true;
                    waitThread(SCAN_TIME/2); // if timer fails, force to stop
                } else {
                    // do normal scanning
                    mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                    mGatewayService.execScanningQueue();
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
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }
        }

        private void connect() {
            List<BluetoothDevice> scanResults = mGatewayService.getScanResults();
            List<String> devices = mGatewayService.getListActiveDevices();

            if(devices == null) { return; }

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;;
            maxConnectTime = remainingTime / devices.size();
            broadcastUpdate("Number of active devices is " + devices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            // do Round Robin part for connection
            for (BluetoothDevice device : scanResults) {
                mGatewayService.updateDatabaseDeviceState(device, "inactive");
                if (devices.contains(device.getAddress())) {
                    Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                    processConnecting = new ProcessPriority(10);
                    processConnecting.newThread(runnable).start();
                    // set timer to xx seconds
                    waitThread(maxConnectTime);
                    if (!mProcessing) {
                        return;
                    }
                    broadcastUpdate("Wait time finished, disconnected...");
                    mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                    waitThread(10);
                    processConnecting.interruptThread();
                }
            }
        }
    }

    private class FEPDeviceDbRefresh implements Runnable {
        @Override
        public void run() {
            mGatewayService.updateAllDeviceStates(null);
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
                mGatewayService.setProcessing(mProcessing);
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
            broadcastUpdate("\n");
            broadcastUpdate("Start new cycle");
            cycleCounter++;
            broadcastUpdate("Cycle number " + cycleCounter);
            mProcessing = true;
            boolean isDataExist = mGatewayService.checkDevice(null);
            if (isDataExist) {
                List<String> devices = mGatewayService.getListActiveDevices();
                for (String device : devices) { mGatewayService.addQueueScanning(device, null, 0, BluetoothLeDevice.FIND_LE_DEVICE, null); }
                mGatewayService.execScanningQueue();
                mScanning = false;
                // do normal scanning only for half of normal scanning time
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME/2);
            } else {
                // do normal scanning
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = true;
                waitThread(SCAN_TIME);
            }

            if(!mProcessing) {return;}

            stop();
            waitThread(100);
            connect();
        }

        private void stop() {
            if(mScanning) {
                mGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCANNING, null);
                mGatewayService.execScanningQueue();
                mScanning = false;
            }
        }

        private void connect() {
            Map<BluetoothDevice, Integer> mapRankedDevices = doRankDevices(mGatewayService.getScanResults());

            // calculate timer for connection (to obtain Round Robin Scheduling)
            int remainingTime = PROCESSING_TIME - SCAN_TIME;;
            maxConnectTime = remainingTime / mapRankedDevices.size();
            broadcastUpdate("Number of active devices is " + mapRankedDevices.size());
            broadcastUpdate("Maximum connection time is " + maxConnectTime / 1000 + " s");

            for(Map.Entry entry : mapRankedDevices.entrySet()) {
                BluetoothDevice device = (BluetoothDevice) entry.getKey();
                Runnable runnable = mGatewayService.doConnecting(device.getAddress(), null);
                processConnecting = new ProcessPriority(10);
                processConnecting.newThread(runnable).start();
                // set timer to xx seconds
                if((Integer) entry.getValue() == 1) {
                    waitThread(maxConnectTime);
                } else {
                    waitThread(maxConnectTime/2); // only half of normal connection will be used to connect
                }
                if (!mProcessing) { return; }
                broadcastUpdate("Wait time finished, disconnected...");
                mGatewayService.doDisconnected(mGatewayService.getCurrentGatt(), "GatewayController");
                waitThread(10);
                processConnecting.interruptThread();
            }
        }

        // implementation of Ranking Devices based on Fixed Priority Algorithm
        private Map<BluetoothDevice, Integer> doRankDevices(List<BluetoothDevice> devices) {
            broadcastUpdate("\n");
            broadcastUpdate("Start ranking device...");

            Map<BluetoothDevice, Integer> rankedDevices = new HashMap<>();
            Map<BluetoothDevice, Integer> mapPriority = new HashMap<>();
            DataSorterHelper<String> sortString = new DataSorterHelper<>();
            DataSorterHelper<BluetoothDevice> sortDevice = new DataSorterHelper<>();

            Map<String, Integer> mapDevicesRssi = new HashMap<>();
            for(BluetoothDevice device : devices) {mapDevicesRssi.put(device.getAddress(), mGatewayService.getDeviceRSSI(device.getAddress()));}
            mapDevicesRssi = sortString.sortMapByComparator(mapDevicesRssi, true); // sort based on RSSI in ascending (true)

            // check for priority in case of RSSI
            for(Map.Entry entry : mapDevicesRssi.entrySet()) {
                for(BluetoothDevice device : devices) {
                    if(device.getAddress().equals((String) entry.getKey())) {
                        if((Integer) entry.getValue() >= -90) { // only rssi bigger than -90 dBm that will be prioritized (give 1)
                            mapPriority.put(device, 1);
                        } else {
                            mapPriority.put(device, 3);
                        }
                    }
                }
            }

            // check for priority in case of manufacturer data
            for(Map.Entry entry : mapPriority.entrySet()) {
                if((Integer) entry.getValue() >= 2) {
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
                    byte[] scanRecord = mGatewayService.getDeviceScanRecord(device.getAddress());
                    Map<String, Object> mapAdvertisement = AdRecordHelper.parseAdvertisement(AdRecordHelper.decodeAdvertisement(scanRecord));
                    if(mapAdvertisement.containsKey("Manufacturer")) {

                    }
                }
            }
            // check for priority in case of services available
            for(Map.Entry entry : mapPriority.entrySet()) {
                if((Integer) entry.getValue() >= 2) {
                    BluetoothDevice device = (BluetoothDevice) entry.getKey();
                    byte[] scanRecord = mGatewayService.getDeviceScanRecord(device.getAddress());
                    Map<String, Object> mapAdvertisement = AdRecordHelper.parseAdvertisement(AdRecordHelper.decodeAdvertisement(scanRecord));
                    if(mapAdvertisement.containsKey("UUIDs")) {

                    }
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
        }
    }

    private class FPDeviceDbRefresh implements Runnable {
        @Override
        public void run() { mGatewayService.updateAllDeviceStates(null); }
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
            intent.putExtra("command", message);
            sendBroadcast(intent);
        }
    }

}
