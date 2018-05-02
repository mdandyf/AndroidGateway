package com.uni.stuttgart.ipvs.androidgateway.service;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.uni.stuttgart.ipvs.androidgateway.R;

public class BaseContainer extends Fragment {
    public void replaceFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.replace(R.id.container_framelayout, fragment);
        transaction.commit();
        getChildFragmentManager().executePendingTransactions();
    }

    public boolean popFragment() {
        boolean isPop = false;
        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            isPop = true;
            getChildFragmentManager().popBackStack();
        }
        return isPop;
    }
}
