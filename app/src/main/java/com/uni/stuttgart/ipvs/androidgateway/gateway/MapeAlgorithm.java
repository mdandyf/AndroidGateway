package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.thread.ExecutionTask;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class MapeAlgorithm {

    private IGatewayService iGatewayService;
    private Context context;
    private boolean mProcessing;

    private ExecutionTask<String> executionTask;

    private double fuzzyResult;
    private int batteryInput;
    private int deviceInput;
    private int priorityOutput;
    private String resultAlg;

    public MapeAlgorithm(Context context, boolean mProcessing, IGatewayService iGatewayService) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.iGatewayService = iGatewayService;
    }


    public String startMape() {

        //GET CURRENT BATTERY LEVEL
        batteryInput = getBatteryLevel();


        try {

            //GET CURRENT NUMBER OF CONNECTIBLE DEVICES
            List<BluetoothDevice> activeDevices = iGatewayService.getScanResults();

            deviceInput = activeDevices.size();

            if(deviceInput > 10){
                deviceInput = 10;
            }

            //deviceTxt.setText("Number of Devices: " + deviceInput);

            //GET PRIORITY DEGREE BY APPLYING FUZZY LOGIC
            priorityOutput = fuzzyProcess(batteryInput, deviceInput);

            //CHOOSE SCHEDULING ALGORITHM
            switch(priorityOutput) {
                case 1: resultAlg = "ep";
                    break;
                case 2: resultAlg = "fep";
                    break;
                case 3: resultAlg =  "epAhp";
                    break;
                case 4: resultAlg = "epWsm";
                    break;
                case 5: resultAlg = "ahp";
                    break;
                case 6: resultAlg = "wsm";
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


    //FUZZY LOGIC TO GET PRIORITY DEGREE
    public int fuzzyProcess(int battery, int device) {

        Log.d("fuzzy1", "Beggining Fuzzy read and calculate...");

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
            return -1;
        }

        // Set inputs
        fis.setVariable("battery_level", battery);
        fis.setVariable("devices_number", device);

        // Evaluate
        fis.evaluate();

        Variable priority = fis.getFunctionBlock("schedule_controller").getVariable("priority_degree");

        // Print ruleSet
        Log.d("Saida: ", "" + priority.defuzzify());
        fuzzyResult = ((int) Math.round(priority.defuzzify()));

        // Show output variable
        //fuzzyTxt.setText("Fuzzy Output: " + fuzzyResult);

        Log.d("fuzzy2", "FUZZY ENDED");

        return (int) fuzzyResult;
    }


}
