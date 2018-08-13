package com.uni.stuttgart.ipvs.androidgateway.service;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.util.Log;
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
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.UploadCloudFragment;

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

        //bind to service
        Intent intent = new Intent(context, GatewayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        if(mReceiver != null) { getActivity().unregisterReceiver(mReceiver); }
        if(mBound) { getActivity().unbindService(mConnection); }
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
                switch(ServiceLong) {
                    case "0000180f-0000-1000-8000-00805f9b34fb":        //Battery Service
                        launchBatteryFragment(deviceAddress, ServiceLong);
                        break;
                    case "0000180d-0000-1000-8000-00805f9b34fb":        //Heart Service
                        //launchUploadCloudFragment(deviceAddress);
                        launchHeartRateFragment(deviceAddress, ServiceLong);
                        break;
                    case "0000fee0-0000-1000-8000-00805f9b34fb":        //MIBAND
                        launchUploadCloudFragment(deviceAddress);
                        break;
                    case "0000fff0-0000-1000-8000-00805f9b34fb":        //VEMITER
                        launchUploadCloudFragment(deviceAddress);
                        break;
                    case "00001816-0000-1000-8000-00805f9b34fb":        // Cycling Speed and Cadence
                        launchUploadCloudFragment(deviceAddress);
                        break;
                    case "00001800-0000-1000-8000-00805f9b34fb":
                        Toast.makeText(getContext(),  "Service UUID " + ServiceUUID, Toast.LENGTH_SHORT).show();
                        break;
                    case "a8b3fa04-4834-4051-89d0-3de95cddd318":        // BeeWi Humidity Sensor
                        launchUploadCloudFragment(deviceAddress);
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


    //Handle Upload to Cloud
    public void launchUploadCloudFragment(String macAddress) throws RemoteException {

        String deviceName = iGatewayService.getDeviceName(macAddress);

        //Pass Data to Cloud Fragment
        Bundle bundle = new Bundle();
        bundle.putString("mac", macAddress);
        bundle.putString("name", deviceName);
        UploadCloudFragment uploadCloudFragment = new UploadCloudFragment();
        uploadCloudFragment.setArguments(bundle);
        replaceFragment(R.id.main_frame, uploadCloudFragment, "Cloud");

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


            //check active devices once
            try {
                List<String> activeDevices = iGatewayService.getListActiveDevices();
                Map<String, String> data = new HashMap<>();

                for(String mac : activeDevices){
                    String deviceName = iGatewayService.getDeviceName(mac);
                    data.put("Device Address", mac);
                    data.put("Device Name", deviceName);
                    if(!dataAdapter.contains(data)) { dataAdapter.add(data);adapter.notifyDataSetChanged(); }
                    //Log.d("message", dataAdapter.get(0).toString());
                    //Log.d("message", dataAdapter.get(1).toString());

                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            getActivity().registerReceiver(mReceiver, new IntentFilter(GatewayService.FINISH_READ));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.FINISH_READ)) {

                //Toast.makeText(getContext(), "Device Added Ya MArwan", Toast.LENGTH_SHORT).show();

                final String macAddress = intent.getStringExtra("command");

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            String deviceName = iGatewayService.getDeviceName(macAddress);

                            Map<String, String> data = new HashMap<>();
                            data.put("Device Name", deviceName);
                            data.put("Device Address", macAddress);
                            if(!dataAdapter.contains(data)) { dataAdapter.add(data);adapter.notifyDataSetChanged(); }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                    }
                });

            }
        }
    };


    private void replaceFragment(int containerView, android.support.v4.app.Fragment classFragment, String tag) {
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(containerView, classFragment, tag)
                .addToBackStack(tag)
                .commitAllowingStateLoss();
    }
}
