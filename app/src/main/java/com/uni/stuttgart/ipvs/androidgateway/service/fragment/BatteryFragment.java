package com.uni.stuttgart.ipvs.androidgateway.service.fragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;

public class BatteryFragment extends Fragment {

    private String deviceMac;
    private String serviceUUIDLong;
    private String characteristicUUIDLong;


    private TextView serviceText;
    private TextView characteristicText;
    private TextView valueText;
    private android.widget.ProgressBar batteryBar;

    private Handler mHandler = new Handler();

    //Service Atts
    private IGatewayService myService;
    boolean isBound = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        //Initialize Layout Components
        serviceText = view.findViewById(R.id.battery_service);
        characteristicText = view.findViewById(R.id.battery_characteristic);
        valueText = view.findViewById(R.id.battery_value);
        batteryBar = view.findViewById(R.id.battery_bar);

        //Bind to Gateway Service
        Intent intent = new Intent(getActivity(), GatewayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);


        //Retrieve Initial Data
        Bundle bundle = getArguments();

        deviceMac = bundle.getString("mac");
        serviceUUIDLong = bundle.getString("serviceLong");
        characteristicUUIDLong = bundle.getString("characteristicLong");

        //View Initial Data
        if(bundle != null) {
            serviceText.setText(bundle.getString("service"));
            characteristicText.setText(bundle.getString("characteristic"));
            String value = bundle.getString("value");;
            valueText.setText(value + " %");
            int valueInt = Integer.parseInt(value);
            batteryBar.setProgress(valueInt);
        }

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isBound) { getActivity().unbindService(mConnection); }
    }


    //Service Connection
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            myService = IGatewayService.Stub.asInterface(service);

            getActivity().registerReceiver(mReceiver, new IntentFilter(GatewayService.FINISH_READ));


        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
                isBound = false;
                myService = null;
            getActivity().unregisterReceiver(mReceiver);
            Toast.makeText(getContext(), "Gateway Service Disconnected", Toast.LENGTH_SHORT).show();
        }
    };


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.FINISH_READ)) {

                final String macAddress = intent.getStringExtra("command");

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        //Update Characteristic Value
                        String batteryValueLong = null;

                        try {
                            batteryValueLong = myService.getCharacteristicValue(deviceMac, serviceUUIDLong, characteristicUUIDLong);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        String batteryValue = batteryValueLong.substring(3,5);

                        //Convert HEX to DEC
                        int batteryPercent = Integer.parseInt(batteryValue, 16);

                        valueText.setText(batteryPercent + " %");
                        batteryBar.setProgress(batteryPercent);

                        Toast.makeText(getContext(), "Value Updated", Toast.LENGTH_SHORT).show();

                    }
                });

            }
        }
    };



}
