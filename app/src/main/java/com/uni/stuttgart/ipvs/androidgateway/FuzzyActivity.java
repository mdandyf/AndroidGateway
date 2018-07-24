package com.uni.stuttgart.ipvs.androidgateway;

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

import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by marwanamrh on 7/22/2018.
 */

public class FuzzyActivity extends AppCompatActivity {

    private TextView fuzzyTxt;
    private TextView levelTxt;
    private TextView deviceTxt;
    private double fuzzyResult;
    private int batteryInput;
    private int deviceInput;
    private int priorityOutput;

    private Handler handler;
    private Runnable runnable;

    private IGatewayService iGatewayService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuzzy);

        fuzzyTxt = findViewById(R.id.fuzzy_txt);
        levelTxt = findViewById(R.id.level_txt);
        deviceTxt = findViewById(R.id.device_txt);


        //bind to service
        Intent intent = new Intent(getApplicationContext(), GatewayService.class);
        this.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);


        /*
        runnable = new Runnable() {
            @Override
            public void run() {



                handler.postDelayed(runnable,  50000);
            }
        };

        handler = new Handler();
        handler.postDelayed(runnable, 0);
*/
        //fuzzyProcess(2,8);

    }

    @Override
    protected void onStart() {
        //registerReceiver(batteryMonitor, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        super.onStart();
    }

    @Override
    protected void onStop() {
        //unregisterReceiver(batteryMonitor);
        super.onStop();

    }

    public int getBatteryLevel() {

        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        levelTxt.setText("Battery Level: " + ((int) (((float) level / (float) scale) * 100.0f)) + "%");

        return ((int) (((float) level / (float) scale) * 100.0f));
    }

    /*private BroadcastReceiver batteryMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            service = (int) (Math.random() * 9 + 1);
            food = (int) (Math.random() * 9 + 1);

            fuzzyProcess(service, food);

            // Charge Status
             deviceStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryLevel = (int) (((float) level / (float) scale) * 100.0f);

            levelTxt.setText(batteryLevel+"");

            switch (deviceStatus){
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING: currentBatteryStatus = "Not Charging";
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING: currentBatteryStatus = "Discharging";
                    break;
                case BatteryManager.BATTERY_STATUS_CHARGING: currentBatteryStatus = "Charging";
                    break;
                case BatteryManager.BATTERY_STATUS_FULL: currentBatteryStatus = "Battery Full";
                    break;
                default: break;
            }

            stateTxt.setText(currentBatteryStatus+"");
        }
    };*/


    public int fuzzyProcess(int battery, int device) {

        InputStream in = null;

        try {
            in = getApplicationContext().getAssets().open("schedule_controller.fcl");
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
        fuzzyTxt.setText("Fuzzy Output: " + fuzzyResult);

        return (int) fuzzyResult;
    }


    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            iGatewayService = IGatewayService.Stub.asInterface(service);

            batteryInput = getBatteryLevel();

            //check active devices once
            try {
                List<String> activeDevices = iGatewayService.getListActiveDevices();

                deviceInput = activeDevices.size();

                deviceTxt.setText("Number of Devices: " + deviceInput);

                priorityOutput = fuzzyProcess(batteryInput, deviceInput);

                //choose Schedule

                switch(priorityOutput) {
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    case 4:
                        break;
                    case 5:
                        break;
                    case 6:
                        break;
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }

            //getActivity().registerReceiver(mReceiver, new IntentFilter(GatewayService.FINISH_READ));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };


}
