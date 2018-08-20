package com.uni.stuttgart.ipvs.androidgateway.gateway.mape;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.RemoteException;
import android.util.Log;

import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.uni.stuttgart.ipvs.androidgateway.helper.NetworkUtils;

public class MapeAlgorithm {

    private IGatewayService iGatewayService;
    private Context context;
    private boolean mProcessing;

    private double scheduleResult;
    private double uploadResult;
    private int batteryInput;
    private int deviceInput;
    private int connectionInput;
    private Map<String,Object> mapeAction;

    private int uploadOutput;
    private int priorityOutput;

    private String resultAlg;
    private ExecutionTask<Void> executionTask;

    public MapeAlgorithm(Context context, boolean mProcessing, IGatewayService iGatewayService, ExecutionTask<Void> executionTask, Map<String,Object> mapeAction) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
        this.executionTask = executionTask;
        this.mapeAction = mapeAction;
    }


    public String startMape() {

        try {
            //GET CURRENT BATTERY LEVEL
            batteryInput = getBatteryLevel();

            //GET CURRENT NUMBER OF CONNECTIBLE DEVICES
            List<BluetoothDevice> activeDevices = iGatewayService.getScanResultsNonVolatile();
            Log.d("mape", "MAPE Devices: " + activeDevices.size()+"");
            deviceInput = activeDevices.size();
            if(deviceInput > 10){
                deviceInput = 10;
            }

            String mapeDataUpload = (String) mapeAction.get("DataUpload");
            if(mapeDataUpload.equalsIgnoreCase("yes")){
                //GET CONNECTIVITY STATE
                int conn = NetworkUtils.getConnectivityStatus(context);
                if(conn == NetworkUtils.TYPE_WIFI){
                    connectionInput = 0;
                    fuzzyProcess1(batteryInput, deviceInput, connectionInput);

                    if(uploadOutput == 1){
                        Log.d("Upload", "Data has been uploaded to the cloud");
                        iGatewayService.uploadDataCloud();
                    }
                }else if(conn == NetworkUtils.TYPE_MOBILE){
                    connectionInput = 1;
                    fuzzyProcess1(batteryInput, deviceInput, connectionInput);

                    if(uploadOutput == 1){
                        Log.d("Upload", "Data has been uploaded to the cloud");
                        iGatewayService.uploadDataCloud();
                    }
                }else {
                    Log.d("upload", "No Internet, upload failed");
                    fuzzyProcess2(batteryInput, deviceInput);
                }
            }else {
                fuzzyProcess2(batteryInput,deviceInput);
            }

            //CHOOSE SCHEDULING ALGORITHM
            switch(priorityOutput) {
                case 1: resultAlg = "epAhp";        //worst case
                    break;
                case 2: resultAlg = "ep";
                    break;
                case 3: resultAlg =  "ahp";
                    break;
                case 4: resultAlg = "epWsm";
                    break;
                case 5: resultAlg = "fep";
                    break;
                case 6: resultAlg = "wsm";        //best case
                    break;
                default:
                    break;
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return resultAlg;

    }


    //GET CURRENT BATTERY LEVEL
    public int getBatteryLevel() {

        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        //levelTxt.setText("Battery Level: " + ((int) (((float) level / (float) scale) * 100.0f)) + "%");

        return ((int) (((float) level / (float) scale) * 100.0f));
    }


    private void fuzzyProcess1(int battery, int device, int connection){

        InputStream in = null;

        try {
            in = context.getAssets().open("schedule_controller.fcl");
        } catch (IOException e) {
            e.printStackTrace();
        };


        // Load from 'FCL' file
        String fileName = "schedule_controller.fcl";
        FIS fis = FIS.load(in,true);

        // Error while loading?
        if( fis == null ) {
            System.err.println("Can't load file: '" + fileName + "'");
            return;
        }

        // Set inputs
        fis.setVariable("schedule_controller","battery_level", battery);
        fis.setVariable("schedule_controller","devices_number", device);

        fis.setVariable("upload_controller", "battery_level", battery);
        fis.setVariable("upload_controller", "internet_connection", connection);


        // Evaluate
        fis.evaluate();

        Variable priority = fis.getFunctionBlock("schedule_controller").getVariable("priority_degree");
        Variable uploadState = fis.getFunctionBlock("upload_controller").getVariable("upload_status");

        // Print ruleSet
        Log.d("mape: ", "scheduleResult" + priority.defuzzify());
        scheduleResult = ((int) Math.round(priority.defuzzify()));

        // Print ruleSet
        Log.d("mape: ", "uploadResult" + uploadState.defuzzify());
        uploadResult = ((int) Math.round(uploadState.defuzzify()));

        if(uploadResult >= 0.5){
            Log.d("upload", "upload data");

            /*try {
                iGatewayService.uploadDataCloud();

            } catch (RemoteException e) {
                e.printStackTrace();
            }*/

        }else {
            Log.d("upload", "do not upload data");
        }

        uploadOutput = (int) uploadResult;
        priorityOutput = (int) scheduleResult;
    }

    //FUZZY LOGIC TO GET PRIORITY DEGREE
    private void fuzzyProcess2(int battery, int device) {

        InputStream in = null;

        try {
            in = context.getAssets().open("schedule_controller.fcl");
        } catch (IOException e) {
            e.printStackTrace();
        };


        // Load from 'FCL' file
        String fileName = "schedule_controller.fcl";
        FIS fis = FIS.load(in,true);

        // Error while loading?
        if( fis == null ) {
            System.err.println("Can't load file: '" + fileName + "'");
            return;
        }

        // Set inputs
        fis.setVariable("schedule_controller","battery_level", battery);
        fis.setVariable("schedule_controller","devices_number", device);


        // Evaluate
        fis.evaluate();

        Variable priority = fis.getFunctionBlock("schedule_controller").getVariable("priority_degree");

        // Print ruleSet
        Log.d("Saida: ", "" + priority.defuzzify());
        scheduleResult = ((int) Math.round(priority.defuzzify()));

        priorityOutput = (int) scheduleResult;
    }


}
