package com.uni.stuttgart.ipvs.androidgateway.service.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.uni.stuttgart.ipvs.androidgateway.R;

public class BatteryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_battery, container, false);
        return super.onCreateView(inflater, container, savedInstanceState);
    }
}
