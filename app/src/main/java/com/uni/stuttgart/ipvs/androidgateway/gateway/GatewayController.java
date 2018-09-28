package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.gateway.mape.MapeAlgorithm;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.ExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.ExhaustivePollingWithAHP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.ExhaustivePollingWithWSM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.FairExhaustivePolling;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.IGatewayScheduler;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.PriorityBasedWithAHP;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.PriorityBasedWithWSM;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.RoundRobin;
import com.uni.stuttgart.ipvs.androidgateway.gateway.scheduler.Semaphore;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.thread.EExecutionType;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by mdand on 4/10/2018.
 */

public class GatewayController extends Service {
    private final IBinder mBinder = new LocalBinder();
    private Context context;
    private Intent mIntent;

    private IGatewayService iGatewayService;
    private IGatewayScheduler iGatewayScheduler;

    private boolean mBound = false;
    private volatile boolean mProcessing = false;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private MapeAlgorithm mapeAlgorithm;

    private Document xmlFile;
    private Runnable runnableMape;
    private Runnable runnableAlgorithm;
    private Thread algorithmThread;

    private Map<String,Object> mapeDataAction;

    private final String[] algorithm = {null};
    private ExecutionTask<Void> executionTask = null;

    private XmlPullParser mfrParser;

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
            iGatewayService.stopScan();
            iGatewayService.setProcessing(false);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if(iGatewayScheduler != null) { iGatewayScheduler.stop(); }
        if (mConnection != null) { unbindService(mConnection); }

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
                for (Map.Entry entry : pwr.entrySet()) {
                    String key = (String) entry.getKey();
                    double[] data = (double[]) entry.getValue();
                    iGatewayService.setPowerUsageConstraints(key, data);
                }

                // input data Timer
                Map<String, Object> tmr = readXMLFile(xmlFile, "DataTimer");
                for (Map.Entry entry : tmr.entrySet()) {
                    String key = (String) entry.getKey();
                    String dataString = (String) entry.getValue();

                    if (key.equalsIgnoreCase("TimeUnit")) {
                        iGatewayService.setTimeUnit(dataString);
                    } else {
                        int data = Integer.valueOf(dataString);
                        iGatewayService.setTimeSettings(key, data);
                    }
                }

                // input data MAPE
                mapeDataAction = readXMLFile(xmlFile, "DataMape");

                //Get Manufacturers List from XML File
                mfrParser = GattDataHelper.parseXML(getAssets().open("Manufacturer.xml"));
                List<PManufacturer> manufacturers = GattDataHelper.processParsing(mfrParser);
                iGatewayService.setManufacturerData(manufacturers);

            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            }


            //Start the Execution Task
            int N = Runtime.getRuntime().availableProcessors();
            executionTask = new ExecutionTask<>(N, N + 1);
            executionTask.setExecutionType(EExecutionType.MULTI_THREAD_POOL);

            //Schedule algorithm thread
            runnableAlgorithm = doSchedulingAlgorithm();

            algorithmThread = executionTask.executeRunnableInThread(runnableAlgorithm, "Algorithm Thread", Thread.MIN_PRIORITY);

            //Set Mape Algorithm
            runnableMape = doMAPEAlgorithm();

            //Repeate MAPE Algorithm every XX Minute set in .XML Settings File
            String mapeAction = (String) mapeDataAction.get("MapeAction");
            if(mapeAction.equalsIgnoreCase("yes")){
                try {
                    executionTask.scheduleWithThreadPoolExecutor(runnableMape, iGatewayService.getTimeSettings("MapeTime"), iGatewayService.getTimeSettings("MapePeriod"), TimeUnit.MILLISECONDS);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

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
            iGatewayScheduler = new Semaphore(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new RoundRobin(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new ExhaustivePolling(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new ExhaustivePollingWithAHP(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new ExhaustivePollingWithWSM(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new FairExhaustivePolling(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new PriorityBasedWithAHP(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
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
            iGatewayScheduler = new PriorityBasedWithWSM(context, mProcessing, iGatewayService, executionTask);
            iGatewayScheduler.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //Scheduling Selection
    private synchronized Runnable doSchedulingAlgorithm() {

        return new Runnable() {
            @Override
            public void run() {

                if (algorithm[0].equals("ep")) {
                    doScheduleEP();
                } else if (algorithm[0].equals("fep")) {
                    doScheduleFEP();
                } else if (algorithm[0].equals("epAhp")) {
                    doScheduleEPwithAHP();
                } else if (algorithm[0].equals("epWsm")) {
                    doScheduleEPwithWSM();
                } else if (algorithm[0].equals("ahp")) {
                    doSchedulePriorityAHP();
                } else if (algorithm[0].equals("wsm")) {
                    doSchedulePriorityWSM();
                }

            }
        };

    }

    // MAPE Algorithm Runnable
    private synchronized Runnable doMAPEAlgorithm() {

        return new Runnable() {
            @Override
            public void run() {

                try {
                    //READ DEVICES FROM NON VOLATILE MEMORY
                    iGatewayService.broadcastClearScreen("Clear the Screen");
                    broadcastUpdate("Available Devices: " + iGatewayService.getScanResultsNonVolatile());
                    broadcastUpdate("Evaluating new MAPE Algorithm...");

                    mapeAlgorithm = new MapeAlgorithm(context, mProcessing, iGatewayService, executionTask, mapeDataAction);
                    algorithm[0] = mapeAlgorithm.startMape();
                    broadcastUpdate("Changing Algorithm...");
                    broadcastUpdate("New Algorithm Is : " + algorithm[0]);

                    Log.d("newAlgorithm", "New Algorithm Is : " + algorithm[0]);

                    // ensure thread is killed first & clear the screen
                    if(iGatewayScheduler != null) { iGatewayScheduler.stop(); }

                    Thread.sleep(1000);
                    algorithmThread = null;
                    algorithmThread = executionTask.executeRunnableInThread(doSchedulingAlgorithm(), "Algorithm Thread", Thread.MAX_PRIORITY);

                } catch (Exception e) {
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
                Node nodeTimer5 = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling();
                results.put("MapeTime", nodeTimer5.getFirstChild().getNodeValue());
                Node nodeTimer6 = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling().getNextSibling();
                results.put("MapePeriod", nodeTimer6.getFirstChild().getNodeValue());
                break;
            case "DataMape":
                list = xmlFile.getElementsByTagName(type);
                Node nodeDataMape = list.item(0);
                nodeData = nodeDataMape.getFirstChild().getNextSibling();
                Node nodeMapeAction = nodeData.getFirstChild().getNextSibling();
                results.put("MapeAction", nodeMapeAction.getFirstChild().getNodeValue());
                Node nodeDataUpload = nodeData.getFirstChild().getNextSibling().getNextSibling().getNextSibling();
                results.put("DataUpload", nodeDataUpload.getFirstChild().getNodeValue());
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
