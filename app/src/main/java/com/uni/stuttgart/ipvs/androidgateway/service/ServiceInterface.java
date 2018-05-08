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
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayController;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.BatteryFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        super.onListItemClick(l, v, position, id);
        Map<String, String> dataMap = dataAdapter.get(position);
        if (dataMap == null) return;

        String deviceAddress = dataMap.get("Device Address");

        // here select which fragment that will be used
        try {
            List<ParcelUuid> listServicesUUID = iGatewayService.getServiceUUIDs(deviceAddress);
            replaceFragment(R.id.main_frame, new BatteryFragment(), "Battery");
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
