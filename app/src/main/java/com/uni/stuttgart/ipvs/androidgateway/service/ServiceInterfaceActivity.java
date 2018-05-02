package com.uni.stuttgart.ipvs.androidgateway.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTabHost;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.BatteryFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceInterfaceActivity extends FragmentActivity {
    private static final Integer PROCESSING_TIME = 60000;
    private FragmentTabHost mTabHost;
    private Intent intent;
    private Context context;
    private List<String> listTagTabView = new ArrayList<>();
    private boolean isBound = false;
    private IGatewayService iGatewayService;
    private ScheduledThreadPoolExecutor scheduler;
    private IBinder iBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serviceinterface);
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), android.R.id.tabcontent);
        context = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
                while(true) {
                    if(isBound) {
                        scheduler = new ScheduledThreadPoolExecutor(2);
                        scheduler.scheduleAtFixedRate(new StartServiceInterface(), 0, PROCESSING_TIME, TimeUnit.MILLISECONDS);
                        break;
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private class StartServiceInterface implements Runnable {
        @Override
        public void run() {
            if(isBound) {
                List<String> listActiveDevices = null;
                try {
                    listActiveDevices = iGatewayService.getListActiveDevices();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                Bundle bundle = null;
                for(String device : listActiveDevices) {
                    bundle = new Bundle();
                    bundle.putCharSequence("MACAddress", device);
                    bundle.putBinder("GatewayService", iBinder);
                    addTabView(device, device, Container.class, bundle);
                    Log.d("Service Interface", "Viewing Device " + device);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mConnection != null) {unbindService(mConnection);}
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            iGatewayService = IGatewayService.Stub.asInterface(service);
            iBinder = service;
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) { isBound = false; }
    };

    public void addTabView(String tag, String indicator, Class<?> fragmentContainerClass, Bundle args) {
        if(!listTagTabView.contains(tag)) {
            listTagTabView.add(tag);
            mTabHost.addTab(mTabHost.newTabSpec(tag).setIndicator(indicator), fragmentContainerClass, args);
            Toast.makeText(context, "Started TabHost 1", Toast.LENGTH_LONG).show();
            //setScreenTabView();
        }
    }

    private void setScreenTabView() {
        for (int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++) {
            final TextView tv = (TextView) mTabHost.getTabWidget().getChildAt(i).findViewById(android.R.id.title);
            if (tv == null)
                continue;
            else
                tv.setTextSize(10);
        }
    }

    @Override
    public void onBackPressed() {
        boolean isPopFragment = false;
        String currentTabTag = mTabHost.getCurrentTabTag();
        isPopFragment = ((BaseContainer)getSupportFragmentManager().findFragmentByTag(currentTabTag)).popFragment();

        if (!isPopFragment) {
            finish();
        }
    }


}

