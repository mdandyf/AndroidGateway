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
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataLookUp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class HeartRateFragment extends Fragment {

    private String deviceMac;
    private String serviceUUIDLong;
    private String locationCharacteristicUUIDLong;
    private String heartRateCharacteristicUUIDLong;

    private TextView serviceText;
    private TextView locationCharacteristicText;
    private TextView locationValueText;
    private TextView heartCharacteristicText;
    private TextView heartValueText;

    private Bundle bundle;

    private GattDataLookUp gattData = new GattDataLookUp();

    private Handler mHandler = new Handler();

    //Service Atts
    private IGatewayService myService;
    boolean isBound = false;


    public HeartRateFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_heart_rate, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        //Bind to Gateway Service
        Intent intent = new Intent(getActivity(), GatewayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        //Initialize Layout Components
        serviceText = view.findViewById(R.id.heart_service);
        locationCharacteristicText = view.findViewById(R.id.location_characteristic);
        locationValueText = view.findViewById(R.id.location_value);
        heartCharacteristicText = view.findViewById(R.id.heart_characteristic);
        heartValueText = view.findViewById(R.id.heart_value);

        //Retrieve Initial Data
        bundle = getArguments();

        deviceMac = bundle.getString("mac");
        serviceUUIDLong = bundle.getString("serviceLong");
        locationCharacteristicUUIDLong = bundle.getString("sensorLocationCharacteristicLong");
        heartRateCharacteristicUUIDLong = bundle.getString("heartRateCharacteristicLong");


        //View Initial Data
        if(bundle != null) {
            serviceText.setText(bundle.getString("service"));
            locationCharacteristicText.setText(bundle.getString("sensorLocationCharacteristic"));
            locationValueText.setText(bundle.getString("sensorLocation"));

            Toast.makeText(getContext(), bundle.getString("heartRate"), Toast.LENGTH_SHORT).show();

            if(bundle.getString("heartRate") != null) {
                heartCharacteristicText.setText(bundle.getString("heartRateCharacteristic"));
                heartValueText.setText(bundle.getString("heartRate"));
            }
        }


        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mReceiver);
    }

    //Service Connection
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            myService = IGatewayService.Stub.asInterface(service);

            /*if(myService != null){
                updateData.run();
            }*/

            getActivity().registerReceiver(mReceiver, new IntentFilter(GatewayService.FINISH_READ));

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
            myService = null;
            Toast.makeText(getContext(), "Gateway Service Disconnected", Toast.LENGTH_SHORT).show();
        }
    };


    /*private Runnable updateData = new Runnable() {
        @Override
        public void run() {

            //Update Location Value
            String sensorLocationValueLong = null;
            try {
                sensorLocationValueLong = myService.getCharacteristicValue(deviceMac, serviceUUIDLong, locationCharacteristicUUIDLong);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            String sensorLocationValue = sensorLocationValueLong.substring(3,5);

            //Sensor Location Mapping
            String sensorLocationName = gattData.bodySensorLocationLookup(sensorLocationValue);
            locationValueText.setText(sensorLocationName);

            //Update Heart Rate Value
            if(bundle.getString("heartRate") != null) {
                String heartRateValueLong = null;
                try {
                    heartRateValueLong = myService.getCharacteristicValue(deviceMac, serviceUUIDLong, heartRateCharacteristicUUIDLong);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                String heartRateValue = heartRateValueLong.substring(6, 8);

                int heartRateBpm = Integer.parseInt(heartRateValue, 16);
                heartValueText.setText(heartRateBpm + "");

            }
            Toast.makeText(getContext(), "Value Updated", Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(this, 10000);
            }
    };*/


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.FINISH_READ)) {

                final String macAddress = intent.getStringExtra("command");

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        //Update Location Value
                        String sensorLocationValueLong = null;
                        try {
                            sensorLocationValueLong = myService.getCharacteristicValue(deviceMac, serviceUUIDLong, locationCharacteristicUUIDLong);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        String sensorLocationValue = sensorLocationValueLong.substring(3,5);

                        //Sensor Location Mapping
                        String sensorLocationName = gattData.bodySensorLocationLookup(sensorLocationValue);
                        locationValueText.setText(sensorLocationName);

                        //Update Heart Rate Value
                        if(bundle.getString("heartRate") != null) {
                            String heartRateValueLong = null;
                            try {
                                heartRateValueLong = myService.getCharacteristicValue(deviceMac, serviceUUIDLong, heartRateCharacteristicUUIDLong);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            String heartRateValue = heartRateValueLong.substring(6, 8);

                            int heartRateBpm = Integer.parseInt(heartRateValue, 16);
                            heartValueText.setText(heartRateBpm + "");

                        }
                        Toast.makeText(getContext(), "Value Updated", Toast.LENGTH_SHORT).show();

                    }
                });

            }
        }
    };

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().unregisterReceiver(mReceiver);
    }
}
