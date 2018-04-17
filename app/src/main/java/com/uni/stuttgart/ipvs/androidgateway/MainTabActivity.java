package com.uni.stuttgart.ipvs.androidgateway;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.ScannerActivity;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayActivity;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;

/**
 * Created by mdand on 3/25/2018.
 */

public class MainTabActivity extends TabActivity {

    private TabHost tabHost;
    private TabHost.TabSpec spec;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintab);

        tabHost = (TabHost) findViewById(android.R.id.tabhost); // initiate TabHost
        Intent intent;

        spec = tabHost.newTabSpec("Gateway"); // Create a new TabSpec using tab host
        spec.setIndicator("GATEWAY"); // set the “HOME” as an indicator
        // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent(this, GatewayActivity.class);
        spec.setContent(intent);
        tabHost.addTab(spec);

        // Do the same for the other tabs

        spec = tabHost.newTabSpec("Scanner"); // Create a new TabSpec using tab host
        spec.setIndicator("SCANNER"); // set the “CONTACT” as an indicator
        // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent(this, ScannerActivity.class);
        spec.setContent(intent);
        tabHost.addTab(spec);

        //set tab which one you want to open first time 0 or 1 or 2
        tabHost.setCurrentTab(0);
        broadcast("Start Services", GatewayService.START_COMMAND);

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                // display the name of the tab whenever a tab is changed
                Toast.makeText(getApplicationContext(), tabId, Toast.LENGTH_SHORT).show();
                if(tabId == "Scanner") {
                    broadcast("Stop Services", GatewayService.TERMINATE_COMMAND);
                } else if (tabId == "Gateway") {
                    broadcast("Start Services", GatewayService.START_COMMAND);
                }
            }
        });
    }

    private void broadcast(String message, String action) {
        final Intent intent = new Intent(action);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }
}
