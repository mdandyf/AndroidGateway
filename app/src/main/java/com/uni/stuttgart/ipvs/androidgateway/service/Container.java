package com.uni.stuttgart.ipvs.androidgateway.service;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.service.fragment.BatteryFragment;

import java.util.List;
import java.util.UUID;

public class Container extends BaseContainer {
    private boolean mIsViewInited = false;
    private Bundle bundle;
    private IGatewayService iGatewayService;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.container_fragment, container, false);
        bundle = savedInstanceState;
        if (!mIsViewInited) {
            mIsViewInited = true;
            initView();
        }
        return v;
    }

    private void initView() {
        String macAddress = bundle.getString("MACAddress");
        IBinder iBinder = bundle.getBinder("GatewayService");
        IBinder service = (IBinder) iBinder;
        iGatewayService = IGatewayService.Stub.asInterface(service);
        try {
            List<ParcelUuid> serviceUUIDs = iGatewayService.getServiceUUIDs(macAddress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        replaceFragment(new BatteryFragment(), false);

    }
}
