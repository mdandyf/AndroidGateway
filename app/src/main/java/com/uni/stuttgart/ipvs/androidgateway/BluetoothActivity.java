package com.uni.stuttgart.ipvs.androidgateway;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothActivityCharacteristic;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothActivityReadWrite;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothConfigurationManager;

import static android.widget.Toast.LENGTH_SHORT;

public class BluetoothActivity extends AppCompatActivity {

    protected BluetoothAdapter mBluetoothAdapter;
    protected BluetoothConfigurationManager bluetooth;
    protected Context mContext;
    protected android.content.res.Resources res;
    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mContext = getApplicationContext();
        res = getResources();
        bluetooth = new BluetoothConfigurationManager();
        bluetooth.getBluetoothAdapter();

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
                return;
            }
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
        } else {
            bluetooth.getBleDevice();
            Toast.makeText(mContext, "Start Scanning", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //scanBluetooth();
                } else {
                    Toast.makeText(mContext, "Location access is required to scan for Bluetooth devices.", LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        //disconnect();
        finish();
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivity.class));
                    finish();
                    return true;
                case R.id.navigation_dashboard:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivityCharacteristic.class));
                    finish();
                    return true;
                case R.id.navigation_notifications:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivityReadWrite.class));
                    finish();
                    return true;
            }
            return false;
        }
    };

    public Context getContext() {
        return mContext;
    }

}
