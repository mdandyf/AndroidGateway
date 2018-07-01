package com.uni.stuttgart.ipvs.androidgateway.service.fragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.helper.NetworkUtil;

import java.util.List;

public class UploadCloudFragment extends Fragment {

    private String macAddress;

    private TextView nameText;
    private TextView macText;
    private Button uploadBtn;

    //Service Atts
    private IGatewayService myService;
    boolean isBound = false;

    //Firebase
    private FirebaseDatabase mDatabase;
    DatabaseReference mDeviceReference;

    private NetworkChangeReceiver mReceiver;
    private boolean isUpload;

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
        View view = inflater.inflate(R.layout.fragment_upload_cloud, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        //Initialize Layout Components
        nameText = view.findViewById(R.id.device_name);
        macText = view.findViewById(R.id.device_mac);
        uploadBtn = view.findViewById(R.id.upload_btn);

        //Bind to Gateway Service
        Intent intent = new Intent(getActivity(), GatewayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        //Firebase
        mDatabase = FirebaseDatabase.getInstance();
        mDeviceReference = mDatabase.getReference("devices");

        mReceiver = new NetworkChangeReceiver();
        isUpload = false;


        //Retrieve Initial Data
        Bundle bundle = getArguments();
        macAddress = bundle.getString("mac");

        //View Initial Data
        if(bundle != null) {
            nameText.setText(bundle.getString("name"));
            macText.setText(macAddress);
        }

        //Upload Button
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    UploadDeviceData(macAddress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    //Service Connection
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            myService = IGatewayService.Stub.asInterface(service);

            getActivity().registerReceiver(mReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
            myService = null;

            getActivity().unregisterReceiver(mReceiver);
        }
    };


    public void UploadDeviceData(String macAddress) throws RemoteException {

        if (myService != null) {

            int conn = NetworkUtil.getConnectivityStatus(getContext());

            if(conn == NetworkUtil.TYPE_WIFI) {


                //Services
                List<ParcelUuid> listServiceUUID = myService.getServiceUUIDs(macAddress);

                //array of services
                String[] stringServiceUUIDs = new String[listServiceUUID.size()];
                //populate
                for (int i = 0; i < listServiceUUID.size(); i++) {
                    stringServiceUUIDs[i] = listServiceUUID.get(i).toString();
                }

                //Loop on services to get characteristics of each
                for (String service : stringServiceUUIDs) {
                    //Characteristics of this Service
                    List<ParcelUuid> listCharacteristicUUID = myService.getCharacteristicUUIDs(macAddress, service);
                    //array of characteristics
                    String[] stringCharacteristicUUIDs = new String[listCharacteristicUUID.size()];
                    //populate
                    for (int i = 0; i < listCharacteristicUUID.size(); i++) {
                        stringCharacteristicUUIDs[i] = listCharacteristicUUID.get(i).toString();
                    }

                    //Loop on characteristics to get property, value of each
                    for (String characteristic : stringCharacteristicUUIDs) {
                        String property = myService.getCharacteristicProperty(macAddress, service, characteristic);
                        String value = myService.getCharacteristicValue(macAddress, service, characteristic);

                        //Upload to firebase this service with its characteristics
                        mDeviceReference.child(macAddress).child("services").child(service).child("characteristics").
                                child(characteristic).child("property").setValue(property);

                        mDeviceReference.child(macAddress).child("services").child(service).child("characteristics").
                                child(characteristic).child("value").setValue(value);

                    }
                }

                Toast.makeText(getContext(), "Data Uploaded to Cloud", Toast.LENGTH_SHORT).show();

                isUpload = true;
            }
            else{
                Toast.makeText(getContext(), "Must enable WiFi to upload data", Toast.LENGTH_SHORT).show();
                isUpload = false;
            }
        } else {
            Toast.makeText(getContext(), "No Data Available", Toast.LENGTH_SHORT).show();
        }
    }

    public class NetworkChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            String status = NetworkUtil.getConnectivityStatusString(context);

            Toast.makeText(context, status, Toast.LENGTH_SHORT).show();

            if(status.equalsIgnoreCase("Wifi enabled") && isUpload == false){
                try {
                    UploadDeviceData(macAddress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
