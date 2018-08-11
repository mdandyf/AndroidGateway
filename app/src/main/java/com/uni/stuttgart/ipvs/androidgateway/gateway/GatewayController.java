package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private Document xmlFile;
    private Runnable runnablePeriodic;
    private Runnable runnableMape;
    private Runnable runnableAlgorithm;
    private HandlerThread algThread;
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

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProcessing = false;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopService(mIntent);
        stopSelf();
    }

    /**
     * Gateway Controller Binding Section
     */


    @Override
    public IBinder onBind(Intent intent) {
        context = this;
        setWakeLock();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try {
            //iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
            //iGatewayService.execScanningQueue();

            iGatewayService.stopScan();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        try {
            iGatewayService.setProcessing(false);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if (fep != null) {
            fep.stop();
        }
        if (ep != null) {
            ep.stop();
        }
        if (epAhp != null) {
            epAhp.stop();
        }
        if (epWsm != null) {
            epWsm.stop();
        }
        if (rr != null) {
            rr.stop();
        }
        if (sem != null) {
            sem.stop();
        }
        if (ahp != null) {
            ahp.stop();
        }
        if (anp != null) {
            anp.stop();
        }
        if (wsm != null) {
            wsm.stop();
        }
        if (wpm != null) {
            wpm.stop();
        }


        if (mConnection != null) {
            unbindService(mConnection);
        }
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

            try {
                // read from .xml settings file
                xmlFile = GattDataHelper.parseXML(new InputSource(getAssets().open("Settings.xml")));

                // input data algorithm
                Map<String, Object> alg = readXMLFile(xmlFile, "DataAlgorithm");
                algorithm[0] = (String) alg.get("algorithm");

                // input data powerConstraint
                Map<String, Object> pwr = readXMLFile(xmlFile, "DataPowerConstraint");
                for(Map.Entry entry : pwr.entrySet()) {
                    String key = (String) entry.getKey();
                    double[] data = (double[]) entry.getValue();
                    iGatewayService.setPowerUsageConstraints(key, data);
                }

                // input data Timer
                Map<String, Object> tmr = readXMLFile(xmlFile, "DataTimer");
                for(Map.Entry entry : tmr.entrySet()) {
                    String key = (String) entry.getKey();
                    String dataString = (String) entry.getValue();

                    if (key.equalsIgnoreCase("TimeUnit")) {
                        iGatewayService.setTimeUnit(dataString);
                    } else {
                        int data = Integer.valueOf(dataString);
                        iGatewayService.setTimeSettings(key, data);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }


            //MARWAN
            executionTask = new ExecutionTask<Void>(1,2);
            executionTask.setExecutionType(EExecutionType.MULTI_THREAD_POOL);

            //isAlgorithmChanged[0] = false;

            //Schedule algorithm thread
            runnableAlgorithm = doSchedulingAlgorithm();

            algorithmThread = executionTask.executeRunnableInThread(runnableAlgorithm, "Algorithm Thread", Thread.MAX_PRIORITY);

            //MAPE
            //runnableMape = doMAPEAlgorithm();

            //REPEAT MAPE EVERY 1 MINUTE
            //executionTask.scheduleWithThreadPoolExecutor(runnableMape, 60000, 60000, TimeUnit.MILLISECONDS);


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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        try {
            iGatewayService.setProcessing(mProcessing);
            iGatewayService.setHandler(null, "mGatewayHandler", "Gateway");
            fep = new FairExhaustivePolling(context, mProcessing, iGatewayService);
            fep.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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

            try {
                        //READ DEVICES FROM NON VOLATILE MEMORY
                        broadcastUpdate("Available Devices: " + iGatewayService.getScanResultsNonVolatile());
                        broadcastUpdate("Evaluating new MAPE Algorithm...");

                        //Toast.makeText(context, "NV Devices: " + iGatewayService.getScanResultsNonVolatile(), Toast.LENGTH_SHORT).show();

                        mapeAlgorithm = new MapeAlgorithm(context, mProcessing, iGatewayService, executionTask);
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


                        // ensure thread is killed first
                        try {
                            algorithmThread.interrupt();
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                           e.printStackTrace();
                        }

                        algorithmThread = executionTask.executeRunnableInThread(runnableAlgorithm, "Algorithm Thread", Thread.MAX_PRIORITY);
            }
            catch (Exception e) {
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

    private Map<String, Object> readXMLFile(Document xmlFile, String type) {
        Map<String, Object> results = new HashMap<>();
        switch (type) {
            case "DataAlgorithm":
                NodeList list = xmlFile.getElementsByTagName(type);
                Node nodeDataAlgo = list.item(0);
                Node nodeData = nodeDataAlgo.getFirstChild().getNextSibling();
                Node nodeAlgo = nodeData.getFirstChild().getNextSibling();
                results.put("algorithm", nodeAlgo.getFirstChild().getNodeValue());
                break;
            case "DataPowerConstraint":
                list = xmlFile.getElementsByTagName(type);
                Node node = list.item(0);

                nodeData = node.getFirstChild().getNextSibling();
                Node batLvlDown = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                int batLevel = Integer.valueOf(batLvlDown.getFirstChild().getNodeValue());
                Node batLvlUp = batLvlDown.getNextSibling().getNextSibling();
                int batLevelUp = Integer.valueOf(batLvlUp.getFirstChild().getNodeValue());
                String constraint = String.valueOf(batLevel) + "," + String.valueOf(batLevelUp);
                results.put(constraint, getPowerConstraint(batLvlUp));

                Node nodeData2 = node.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                batLvlDown = nodeData2.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                batLevel = Integer.valueOf(batLvlDown.getFirstChild().getNodeValue());
                batLvlUp = batLvlDown.getNextSibling().getNextSibling();
                batLevelUp = Integer.valueOf(batLvlUp.getFirstChild().getNodeValue());
                constraint = String.valueOf(batLevel) + "," + String.valueOf(batLevelUp);
                results.put(constraint, getPowerConstraint(batLvlUp));

                Node nodeData3 = node.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling();
                batLvlDown = nodeData3.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                batLevel = Integer.valueOf(batLvlDown.getFirstChild().getNodeValue());
                batLvlUp = batLvlDown.getNextSibling().getNextSibling();
                batLevelUp = Integer.valueOf(batLvlUp.getFirstChild().getNodeValue());
                constraint = String.valueOf(batLevel) + "," + String.valueOf(batLevelUp);
                results.put(constraint, getPowerConstraint(batLvlUp));
                break;
            case "DataTimer":
                list = xmlFile.getElementsByTagName(type);
                Node nodeDataTimer = list.item(0);
                nodeData = nodeDataTimer.getFirstChild().getNextSibling();
                Node nodeTimer1 = nodeData.getFirstChild().getNextSibling();
                results.put("ProcessingTime", nodeTimer1.getFirstChild().getNodeValue());
                Node nodeTimer2 = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                results.put("ScanningTime", nodeTimer2.getFirstChild().getNodeValue());
                Node nodeTimer3 = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling();
                results.put("ScanningTime2", nodeTimer3.getFirstChild().getNodeValue());
                Node nodeTimer4 = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling();
                results.put("TimeUnit", nodeTimer4.getFirstChild().getNodeValue());
                break;
        }
        return results;
    }

    private double[] getPowerConstraint(Node batLvlUp) {
        double[] powerConstraint = new double[3];
        Node th1 = batLvlUp.getNextSibling().getNextSibling().getFirstChild();
        String value = th1.getNodeValue();
        int index = value.indexOf("^");
        int cn = Integer.valueOf(value.substring(0, index));
        int pw = Integer.valueOf(value.substring(index + 1));
        double threshold1 = Math.pow(cn, pw);

        Node th2 = batLvlUp.getNextSibling().getNextSibling().getNextSibling().getNextSibling().getFirstChild();
        String value2 = th2.getNodeValue();
        index = value2.indexOf("^");
        cn = Integer.valueOf(value2.substring(0, index));
        pw = Integer.valueOf(value2.substring(index + 1));
        double threshold2 = Math.pow(cn, pw);

        Node th3 = batLvlUp.getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getFirstChild();
        String value3 = th3.getNodeValue();
        index = value3.indexOf("^");
        cn = Integer.valueOf(value3.substring(0, index));
        pw = Integer.valueOf(value3.substring(index + 1));
        double threshold3 = Math.pow(cn, pw);

        powerConstraint[0] = threshold1;
        powerConstraint[1] = threshold2;
        powerConstraint[2] = threshold3;

        return powerConstraint;
    }

    private void setWakeLock() {
        if ((wakeLock != null) && (!wakeLock.isHeld())) {
            wakeLock.acquire();
        }
    }

    private void broadcastUpdate(String message) {
        if (mProcessing) {
            final Intent intent = new Intent(GatewayService.MESSAGE_COMMAND);
            intent.putExtra("command", message);
            sendBroadcast(intent);
        }
    }

}
