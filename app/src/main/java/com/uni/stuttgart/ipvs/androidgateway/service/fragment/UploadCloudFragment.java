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
    private Button saveBtn;

    //Service Atts
    private IGatewayService myService;
    boolean isBound = false;

    private boolean isSaved;

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
    public View onCreateView(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        saveBtn = view.findViewById(R.id.save_btn);

        //Bind to Gateway Service
        Intent intent = new Intent(getActivity(), GatewayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        isSaved = false;


        //Retrieve Initial Data
        Bundle bundle = getArguments();
        macAddress = bundle.getString("mac");

        //View Initial Data
        if(bundle != null) {
            nameText.setText(bundle.getString("name"));
            macText.setText(macAddress);
        }

        //Save Data Button
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if(myService != null){
                        Toast.makeText(getContext(), "Data has been saved to database", Toast.LENGTH_SHORT).show();
                        myService.saveCloudData(macAddress);
                    }
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
        if(isBound) { getActivity().unbindService(mConnection); }
    }


    //Service Connection
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            myService = IGatewayService.Stub.asInterface(service);
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
            myService = null;
        }
    };



}
