package com.uni.stuttgart.ipvs.androidgateway;

import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TabHost;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.ScannerActivity;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayActivity;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.service.ServiceInterfaceActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mdand on 3/25/2018.
 */

public class MainTabActivity extends TabActivity {

    private TabHost tabHost;
    private TabHost.TabSpec spec;
    private List<String> listTabName;
    private int serviceCounter;
    private boolean isServiceStarted;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintab);

        tabHost = (TabHost) findViewById(android.R.id.tabhost); // initiate TabHost
        listTabName = new ArrayList<>();
        isServiceStarted = false;

        addTabActivity("Gateway", "GATEWAY", GatewayActivity.class);
        addTabActivity("Scanner", "SCANNER", ScannerActivity.class);

        //set tab which one you want to open first time
        tabHost.setCurrentTab(0);

        broadcastCommand("Start Services", GatewayService.START_COMMAND);
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                // display the name of the tab whenever a tab is changed
                Toast.makeText(getApplicationContext(), tabId, Toast.LENGTH_SHORT).show();
                if (tabId == "Gateway") {
                    broadcastCommand("Start Services", GatewayService.START_COMMAND);
                } else if(tabId == "Scanner") {
                    broadcastCommand("Stop Services", GatewayService.TERMINATE_COMMAND);
                }
            }
        });

        IntentFilter filter = new IntentFilter(GatewayService.START_SERVICE_INTERFACE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(GatewayService.START_SERVICE_INTERFACE)) {
                String message = intent.getStringExtra("message");
                if(!isServiceStarted) {
                    addTabActivity("Services", "SERVICES", ServiceInterfaceActivity.class);
                    isServiceStarted = true;
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    unregisterReceiver(mReceiver);
                }
            }
        }
    };



    private void addTabActivity(String tag, String indicator, Class<?> activityClass) {
        listTabName.add(tag);
        spec = tabHost.newTabSpec(tag);
        spec.setIndicator(indicator);
        Intent intent = new Intent(this, activityClass);
        spec.setContent(intent);
        tabHost.addTab(spec);
    }

    private void broadcastCommand(String message, String action) {
        final Intent intent = new Intent(action);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }
}
