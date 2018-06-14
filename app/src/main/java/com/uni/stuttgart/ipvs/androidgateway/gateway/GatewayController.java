package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.ExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.FairExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithAHP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithANP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithWPM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithWSM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.RoundRobin;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.Semaphore;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {
    private final IBinder mBinder = new LocalBinder();
    private Context context;
    private Intent mIntent;
    private IGatewayService iGatewayService;

    private boolean mBound = false;
    private boolean mProcessing = false;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private Semaphore sem;
    private FairExhaustivePolling fep;
    private ExhaustivePolling ep;
    private RoundRobin rr;
    private PriorityBasedWithAHP ahp;
    private PriorityBasedWithANP anp;
    private PriorityBasedWithWSM wsm;
    private PriorityBasedWithWPM wpm;

    private Runnable runnablePeriodic;
    private Thread threadPeriodic;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mIntent = intent;
        context = this;

        bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
        broadcastUpdate("Bind GatewayController to GatewayService...");

        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockController");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProcessing = false;
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        stopService(mIntent);
        stopSelf();
    }

    /**
     * Gateway Controller Binding Section
     */


    @Override
    public IBinder onBind(Intent intent) { context = this; setWakeLock(); return mBinder; }

    @Override
    public boolean onUnbind(Intent intent) {
        try { iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);iGatewayService.execScanningQueue(); } catch (RemoteException e) { e.printStackTrace(); }
        try { iGatewayService.setProcessing(false); } catch (RemoteException e) { e.printStackTrace(); }

        if(fep != null) {fep.stop();}
        if(ep != null) {ep.stop();}
        if(rr != null) {rr.stop();}
        if(sem != null) {sem.stop();}
        if(ahp != null) {ahp.stop();}
        if(anp != null) {anp.stop();}
        if(wsm != null) {wsm.stop();}
        if(wpm != null) {wpm.stop();}

        if(threadPeriodic != null) {threadPeriodic.interrupt();}
        if(mConnection != null) {unbindService(mConnection); }
        broadcastUpdate("Unbind GatewayController to GatewayService...");
        return false;
    }

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
            initDatabase();

            //doScheduleSemaphore();
            //doScheduleRR();
            //doScheduleEP();
            doScheduleFEP();
            //doSchedulePriorityAHP();
            //doSchedulePriorityANP();
            //doSchedulePriorityWSM();
            //doSchedulePriorityWPM();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
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
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            sem = new Semaphore(context, mProcessing, iGatewayService);
            sem.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling using Round Robin Method
    private void doScheduleRR() {
        broadcastUpdate("Start Round Robin Scheduling...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            rr = new RoundRobin(context, mProcessing, iGatewayService);
            rr.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    //scheduling using Exhaustive Polling (EP)
    private void doScheduleEP() {
        broadcastUpdate("Start Exhaustive Polling Scheduling...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            ep = new ExhaustivePolling(context, mProcessing, iGatewayService);
            ep.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling using Fair Exhaustive Polling (FEP)
    private void doScheduleFEP() {
        broadcastUpdate("Start Fair Exhaustive Polling Scheduling...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            fep =  new FairExhaustivePolling(context, mProcessing, iGatewayService);
            fep.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling based on Priority with AHP decision making algorithm
    private void doSchedulePriorityAHP() {
        broadcastUpdate("Start Priority Scheduling with AHP...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            ahp = new PriorityBasedWithAHP(context, mProcessing, iGatewayService);
            ahp.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    
    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling based on Priority with ANP decision making algorithm
    private void doSchedulePriorityANP() {
        broadcastUpdate("Start Priority Scheduling with ANP...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            anp = new PriorityBasedWithANP(context, mProcessing, iGatewayService);
            anp.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling based on Priority with WSM decision making algorithm
    private void doSchedulePriorityWSM() {
        broadcastUpdate("Start Priority Scheduling with WSM...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            wsm = new PriorityBasedWithWSM(context, mProcessing, iGatewayService);
            wsm.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    // scheduling based on Priority with WPM decision making algorithm
    private void doSchedulePriorityWPM() {
        broadcastUpdate("Start Priority Scheduling with WPM...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            wpm = new PriorityBasedWithWPM(context, mProcessing, iGatewayService);
            wpm.start();
        } catch (Exception e) { e.printStackTrace(); }
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

    private void initDatabase() {
        try {
            broadcastUpdate("Initialize database...");
            broadcastUpdate("\n");
            iGatewayService.initializeDatabase();
            iGatewayService.insertDatabasePowerUsage("Case1", 60, 100, 1 * Math.pow(10, 14), 1 * Math.pow(10, 15), 1 * Math.pow(10, 15));
            iGatewayService.insertDatabasePowerUsage("Case2", 20, 60, 1 * Math.pow(10, 13), 1 * Math.pow(10, 14), 1 * Math.pow(10, 14));
            iGatewayService.insertDatabasePowerUsage("Case3", 0, 20, 1 * Math.pow(10, 12), 1 * Math.pow(10, 13), 1 * Math.pow(10, 13));

            /*//MI BAND 2
            iGatewayService.insertDatabaseManufacturer("0x0157", "Anhui Huami Information Technology", "0000fee1-0000-1000-8000-00805f9b34fb");
            iGatewayService.insertDatabaseManufacturer("0x0157", "Anhui Huami Information Technology", "0000fee0-0000-1000-8000-00805f9b34fb");
            //VEMITER
            iGatewayService.insertDatabaseManufacturer("0x0401", "Vemiter Lamp Service", "0000fff0-0000-1000-8000-00805f9b34fb");
            //Simulator Battery
            iGatewayService.insertDatabaseManufacturer("0x0002", "Intel Corp.", "0000180f-0000-1000-8000-00805f9b34fb");
            //Simulaltor Heart
            iGatewayService.insertDatabaseManufacturer("0x0002", "Intel Corp.", "0000180d-0000-1000-8000-00805f9b34fb");*/
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setWakeLock() {
        if((wakeLock != null) && (!wakeLock.isHeld())) { wakeLock.acquire(); }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            sendBroadcast(intent);
        }
    }

}
