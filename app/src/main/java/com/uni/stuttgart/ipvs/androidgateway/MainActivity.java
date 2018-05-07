package com.uni.stuttgart.ipvs.androidgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.support.v7.app.AppCompatActivity;
import android.widget.TabHost;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.ScannerFragment;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayFragment;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;

/**
 * Created by mdand on 3/25/2018.
 */

public class MainActivity extends AppCompatActivity {

    private TabHost tabHost;
    private TabHost.TabSpec spec;
    private FragmentTabHost mTabHost;
    private boolean isServiceStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), android.R.id.tabcontent);
        isServiceStarted = false;

        addTab("Gateway", "GATEWAY", GatewayFragment.class, null);
        addTab("Scanner", "SCANNER", ScannerFragment.class, null);
        addTab("Services", "SERVICES", com.uni.stuttgart.ipvs.androidgateway.service.ServiceInterface.class, null);

        //set tab which one you want to open first time

        mTabHost.setCurrentTab(0);

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
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
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(GatewayService.START_SERVICE_INTERFACE)) {
                String message = intent.getStringExtra("message");
                if(!isServiceStarted) {
                    addTab("Services", "SERVICES", com.uni.stuttgart.ipvs.androidgateway.service.ServiceInterface.class, null);
                    isServiceStarted = true;
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    unregisterReceiver(mReceiver);
                }
            }
        }
    };



    private void addTab(String tag, String indicator, Class<?> fragmentClass, Drawable icon) {
        mTabHost.addTab(
                mTabHost.newTabSpec(tag).setIndicator(indicator, icon),
                fragmentClass, null);
    }

    private void broadcastCommand(String message, String action) {
        final Intent intent = new Intent(action);
        intent.putExtra("command", message);
        sendBroadcast(intent);
    }
}
