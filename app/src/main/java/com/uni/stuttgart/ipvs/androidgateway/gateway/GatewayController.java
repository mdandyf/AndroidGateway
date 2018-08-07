package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.ExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.ExhaustivePollingWithAHP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.ExhaustivePollingWithWSM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.FairExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithAHP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithANP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithWPM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.PriorityBasedWithWSM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.RoundRobin;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduling.Semaphore;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.EExecutionType;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;
import com.uni.stuttgart.ipvs.androidgateway.thread.ThreadTrackingPriority;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    private MapeAlgorithm mapeAlgorithm;


    private Semaphore sem;
    private FairExhaustivePolling fep;
    private ExhaustivePolling ep;
    private ExhaustivePollingWithAHP epAhp;
    private ExhaustivePollingWithWSM epWsm;
    private RoundRobin rr;
    private PriorityBasedWithAHP ahp;
    private PriorityBasedWithANP anp;
    private PriorityBasedWithWSM wsm;
    private PriorityBasedWithWPM wpm;

    private Runnable runnablePeriodic;
    private Runnable runnableMape;
    private Runnable runnableAlgorithm;
    private Thread mapeThread;
    private Thread algorithmThread;


    private final String[] algorithm = {null};
    private final boolean[] isAlgorithmChanged = {false};
    private ExecutionTask<Void> executionTask = null;

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

        //registerReceiver(scanReceiver, new IntentFilter(GatewayService.STOP_SCAN));

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProcessing = false;
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        //unregisterReceiver(scanReceiver);
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
        if(epAhp != null) {epAhp.stop();}
        if(epWsm != null) {epWsm.stop();}
        if(rr != null) {rr.stop();}
        if(sem != null) {sem.stop();}
        if(ahp != null) {ahp.stop();}
        if(anp != null) {anp.stop();}
        if(wsm != null) {wsm.stop();}
        if(wpm != null) {wpm.stop();}

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


            //MARWAN

            //Choose First Algorithm
            // read from .xml settings file
            Document xmlFile = null;
            try {
                xmlFile = GattDataHelper.parseXML(new InputSource( getAssets().open("Settings.xml") ));
                NodeList list = xmlFile.getElementsByTagName("DataAlgorithm");
                Node nodeDataAlgo = list.item(0);
                Node nodeData = nodeDataAlgo.getFirstChild().getNextSibling();
                Node nodeAlgo = nodeData.getFirstChild().getNextSibling();
                algorithm[0] = nodeAlgo.getFirstChild().getNodeValue();
            } catch (IOException e) {
                e.printStackTrace();
            }

            executionTask = new ExecutionTask<Void>(1,2);
            executionTask.setExecutionType(EExecutionType.MULTI_THREAD_POOL);

            //isAlgorithmChanged[0] = false;

            //Schedule algorithm thread
            runnableAlgorithm = doSchedulingAlgorithm();

            algorithmThread = executionTask.executeRunnableInThread(runnableAlgorithm, "Algorithm Thread", Thread.MAX_PRIORITY);

            //MAPE
            runnableMape = doMAPEAlgorithm();
            //REPEAT MAPE EVERY 1 MINUTE
            executionTask.scheduleWithThreadPoolExecutor(runnableMape, 60000, 60000, TimeUnit.MILLISECONDS);


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

    //scheduling using Exhaustive Polling (EP) with Analytical Hierarchy Process
    private void doScheduleEPwithAHP() {
        broadcastUpdate("Start Exhaustive Polling Scheduling with AHP...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            epAhp = new ExhaustivePollingWithAHP(context, mProcessing, iGatewayService);
            epAhp.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */

    //scheduling using Exhaustive Polling (EP) with Weighted Sum Model
    private void doScheduleEPwithWSM() {
        broadcastUpdate("Start Exhaustive Polling Scheduling with WSM...");
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            epWsm = new ExhaustivePollingWithWSM(context, mProcessing, iGatewayService);
            epWsm.start();
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


    //MARWAN

    private synchronized Runnable doSchedulingAlgorithm() {

        return  new Runnable() {
            @Override
            public void run() {

                if(algorithm[0].equals("ep")) {
                    doScheduleEP();
                } else if(algorithm[0].equals("fep")) {
                    doScheduleFEP();
                } else if(algorithm[0].equals("epAhp")) {
                    doScheduleEPwithAHP();
                } else if(algorithm[0].equals("epWsm")) {
                    doScheduleEPwithWSM();
                } else if(algorithm[0].equals("ahp")) {
                    doSchedulePriorityAHP();
                } else if(algorithm[0].equals("wsm")) {
                    doSchedulePriorityWSM();
                }

                //isAlgorithmChanged[0] = false;
            }
        };

    }






    private synchronized Runnable doMAPEAlgorithm() {

        return new Runnable() {
            @Override
            public void run() {

                //int mod;

            try {

                /*if(algorithm[0].equalsIgnoreCase("fep") || algorithm[0].equalsIgnoreCase("ahp") || algorithm[0].equalsIgnoreCase("wsm")){
                    mod = 1;
                }else {
                    mod = 10;
                }*/



                //READ DEVICES FROM NON VOLATILE MEMORY

                //while (true) {

                    /*if (((iGatewayService.getCycleCounter() % mod) == 0) && (iGatewayService.getCycleCounter() > 1)
                            && (iGatewayService.getScanState() == false)
                            ){*/

                        broadcastUpdate("Available Devices: " + iGatewayService.getScanResults().size());
                        broadcastUpdate("Evaluating new MAPE Algorithm...");

                        Log.d("devices", "Available Devices: " + iGatewayService.getScanResults().size());

                        mapeAlgorithm = new MapeAlgorithm(context, mProcessing, iGatewayService);
                        algorithm[0] = mapeAlgorithm.startMape();
                        broadcastUpdate("Changing Algorithm...");
                        broadcastUpdate("New Algorithm Is : " + algorithm[0]);

                        Log.d("newAlgorithm", "New Algorithm Is : " + algorithm[0]);

                        if(fep != null) {fep.stop();}
                        if(ep != null) {ep.stop();}
                        if(epAhp != null) {epAhp.stop();}
                        if(epWsm != null) {epWsm.stop();}
                        if(ahp != null) {ahp.stop();}
                        if(wsm != null) {wsm.stop();}

                        algorithmThread.interrupt();
                        //isAlgorithmChanged[0] = true;
                        algorithmThread = executionTask.executeRunnableInThread(runnableAlgorithm, "Algorithm Thread", Thread.MAX_PRIORITY);

                        mapeThread.interrupt();
                    /*}
                        else{continue;}*/

               // }
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }

            }
        };

    }



    /**
     * End of Algorithm Section
     */

    /*                                                                                                                               *
     * ============================================================================================================================= *
     * ============================================================================================================================= *
     */


/*    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.STOP_SCAN)) {

                mapeThread = executionTask.executeRunnableInThread(execMapeAlgorithm(), "MAPE Thread", Thread.MIN_PRIORITY);

            }
        }
    };*/



    /**
     * Start Method Routine
     */

    private void initDatabase() {
        try {
            broadcastUpdate("Initialize database...");
            broadcastUpdate("\n");
            iGatewayService.initializeDatabase();
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
