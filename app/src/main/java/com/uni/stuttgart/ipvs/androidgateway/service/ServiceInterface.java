package com.uni.stuttgart.ipvs.androidgateway.service;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataLookUp;
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.BatteryFragment;
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.HeartRateFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ServiceInterface extends ListFragment {

    private Context context;
    private boolean mBound;
    private SimpleAdapter adapter;
    private List<Map<String, String>> dataAdapter;
    private ListView listView;

    private IGatewayService iGatewayService;
    private ScheduledThreadPoolExecutor scheduler;


    //Define Characteristics
    private String batteryCharacteristicLong;
    private String batteryCharacteristicUUID;

    private String heartRateCharacteristicLong;
    private String heartRateCharacteristicUUID;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_service, container, false);
        listView = (ListView) v.findViewById(android.R.id.list);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dataAdapter = new ArrayList<Map<String, String>>();
                adapter = new SimpleAdapter(context, dataAdapter,
                        android.R.layout.simple_list_item_2,
                        new String[] {"Device Name", "Device Address"},
                        new int[] {android.R.id.text1,
                                android.R.id.text2});
                listView.setAdapter(adapter);
            }
        });

       return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null && !scheduler.isShutdown()) { scheduler.shutdown(); }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

        String ServiceLong;
        String ServiceUUID;

        super.onListItemClick(l, v, position, id);
        Map<String, String> dataMap = dataAdapter.get(position);
        if (dataMap == null) return;

        //Selected Device MAC address
        String deviceAddress = dataMap.get("Device Address");


        //Select which fragment that will be used
        try {

            //Services
            List<ParcelUuid> listServicesUUID = iGatewayService.getServiceUUIDs(deviceAddress);
            //array of services
            String[] stringServiceUUIDs = new String[listServicesUUID.size()];
            //populate
            for (int i = 0; i < listServicesUUID.size(); i++) {
                stringServiceUUIDs[i] = listServicesUUID.get(i).toString();
            }

            for(int i =0 ; i< stringServiceUUIDs.length; i++){

                //get service
                ServiceLong = stringServiceUUIDs[i];
                ServiceUUID = ServiceLong.substring(4, 8);

                //check if it's battery or heart sensor
                switch(ServiceUUID) {
                    case "180f":
                        launchBatteryFragment(deviceAddress, ServiceLong);
                        break;
                    case "180d":
                        launchHeartRateFragment(deviceAddress, ServiceLong);
                        break;
                    case "1800":
                        Toast.makeText(getContext(),  "Service UUID " + ServiceUUID, Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Toast.makeText(getContext(),  "Service Not Supported " + ServiceUUID, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    //handles battery fragment call
    public void launchBatteryFragment(String deviceAddress, String serviceUUID){

        //Characteristics
        try {
            List<ParcelUuid>  listCharacteristicUUID = iGatewayService.getCharacteristicUUIDs(deviceAddress, serviceUUID);

            //array of characteristics
            String[] stringCharacteristicUUIDs = new String[listCharacteristicUUID.size()];
            //populate
            for(int i = 0; i<listCharacteristicUUID.size();i++) {
                stringCharacteristicUUIDs[i] =  listCharacteristicUUID.get(i).toString();
            }

            for(int i =0 ; i< stringCharacteristicUUIDs.length; i++){

                batteryCharacteristicLong = stringCharacteristicUUIDs[i];
                batteryCharacteristicUUID = batteryCharacteristicLong.substring(4, 8);

                if(batteryCharacteristicUUID.equalsIgnoreCase("2a19"))
                    break;
            }

          //  Toast.makeText(getContext(), characteristicUUID, Toast.LENGTH_SHORT).show();

        //Value
        String batteryValueLong = iGatewayService.getCharacteristicValue(deviceAddress, serviceUUID, batteryCharacteristicLong);
        String batteryValue = batteryValueLong.substring(3,5);

        //Convert HEX to DEC
        int batteryPercent = Integer.parseInt(batteryValue, 16);

           //Toast.makeText(getContext(), serviceUUID + "/" + batterycharacteristicUUID , Toast.LENGTH_SHORT).show();

            //Mapping
            UUID serviceMap = UUID.fromString(serviceUUID);
            UUID characteristicMap = UUID.fromString(batteryCharacteristicLong);

            GattDataLookUp gattData = new GattDataLookUp();
            String serviceName = gattData.serviceNameLookup(serviceMap);
            String characteristicName = gattData.characteristicNameLookup(characteristicMap);


        //Pass Data to Battery Fragment
        Bundle bundle = new Bundle();
        bundle.putString("mac", deviceAddress);
        bundle.putString("serviceLong", serviceUUID);
        bundle.putString("service", serviceName);
        bundle.putString("characteristicLong", batteryCharacteristicLong);
        bundle.putString("characteristic", characteristicName);
        bundle.putString("value", batteryPercent+"");
        BatteryFragment batteryFragment = new BatteryFragment();
        batteryFragment.setArguments(bundle);
        replaceFragment(R.id.main_frame, batteryFragment, "Battery");
        } catch (RemoteException e) {
        e.printStackTrace();
    }
    }


    //handles heart rate fragment call
    public void launchHeartRateFragment(String deviceAddress, String serviceUUID){

        UUID characteristicMap;
        String characteristicName;
        UUID sensorLocationMap;
        String sensorLocationName;

        GattDataLookUp gattData = new GattDataLookUp();

        //Service Mapping
        UUID serviceMap = UUID.fromString(serviceUUID);
        String serviceName = gattData.serviceNameLookup(serviceMap);

        Bundle bundle = new Bundle();
        bundle.putString("mac", deviceAddress);
        bundle.putString("serviceLong", serviceUUID);
        bundle.putString("service", serviceName);

        //Characteristics
        try {
            List<ParcelUuid> listCharacteristicUUID = iGatewayService.getCharacteristicUUIDs(deviceAddress, serviceUUID);

            //array of characteristics
        String[] stringCharacteristicUUIDs = new String[listCharacteristicUUID.size()];
        //populate
        for(int i = 0; i<listCharacteristicUUID.size();i++) {
            stringCharacteristicUUIDs[i] =  listCharacteristicUUID.get(i).toString();
        }

            for(int i =0 ; i< stringCharacteristicUUIDs.length; i++){

                heartRateCharacteristicLong = stringCharacteristicUUIDs[i];
                heartRateCharacteristicUUID = heartRateCharacteristicLong.substring(4, 8);

                //check if it's sensor location or heart rate
                switch(heartRateCharacteristicUUID) {
                    case "2a38":
                        String sensorLocationValueLong = iGatewayService.getCharacteristicValue(deviceAddress, serviceUUID, heartRateCharacteristicLong);
                        String sensorLocationValue = sensorLocationValueLong.substring(3,5);

                        //Sensor Location Characteristic Mapping
                        characteristicMap = UUID.fromString(heartRateCharacteristicLong);
                        characteristicName = gattData.characteristicNameLookup(characteristicMap);

                        sensorLocationName = gattData.bodySensorLocationLookup(sensorLocationValue);

                        bundle.putString("sensorLocationCharacteristicLong", heartRateCharacteristicLong);
                        bundle.putString("sensorLocationCharacteristic", characteristicName);
                        bundle.putString("sensorLocation", sensorLocationName);
                        break;
                    case "2a37":
                        String heartRateValueLong = iGatewayService.getCharacteristicValue(deviceAddress, serviceUUID, heartRateCharacteristicLong);
                        String heartRateValue = heartRateValueLong.substring(6,8);
                        int heartRateBpm = Integer.parseInt(heartRateValue, 16);

                        //Heart Rate Characteristic Mapping
                        characteristicMap = UUID.fromString(heartRateCharacteristicLong);
                        characteristicName = gattData.characteristicNameLookup(characteristicMap);

                        bundle.putString("heartRateCharacteristicLong" , heartRateCharacteristicLong);
                        bundle.putString("heartRateCharacteristic", characteristicName);
                        bundle.putString("heartRate", heartRateBpm + "");
                        break;
                    default:
                        Toast.makeText(getContext(),  "Characteristic Not Supported " + heartRateCharacteristicUUID, Toast.LENGTH_SHORT).show();
                        break;
                }
            }

        //pass data
        HeartRateFragment heartRateFragment = new HeartRateFragment();
        heartRateFragment.setArguments(bundle);
        replaceFragment(R.id.main_frame, heartRateFragment, "HeartRate");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            iGatewayService = IGatewayService.Stub.asInterface(service);
            mBound = true;
            scheduler = new ScheduledThreadPoolExecutor(10);
            scheduler.scheduleAtFixedRate(new ServiceInterfaceRun(), 10, 5 * 1000, MILLISECONDS); // check dbase for new device every 5 seconds
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    protected class ServiceInterfaceRun implements Runnable {

        @Override
        public void run() {
            // populate result from database
            try {
                List<String> activeDevices = iGatewayService.getListActiveDevices();
                List<BluetoothDevice> scanResults = iGatewayService.getScanResults();
                for(final BluetoothDevice device : scanResults) {
                    for(String macAddress: activeDevices) {
                        if(device.getAddress().equals(macAddress)) {
                            String userChoice = iGatewayService.getDeviceUsrChoice(macAddress);
                            if(userChoice != null) {
                                if(userChoice.equals("Yes")) {
                                    // put data into adapter
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Map<String, String> data = new HashMap<>();
                                            data.put("Device Name", "Unknown");
                                            if(device.getName() != null) { data.put("Device Name", device.getName()); }
                                            data.put("Device Address", device.getAddress());
                                            if(!dataAdapter.contains(data)) { dataAdapter.add(data);adapter.notifyDataSetChanged(); }
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void replaceFragment(int containerView, android.support.v4.app.Fragment classFragment, String tag) {
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(containerView, classFragment, tag)
                .addToBackStack(tag)
                .commitAllowingStateLoss();
    }
}
