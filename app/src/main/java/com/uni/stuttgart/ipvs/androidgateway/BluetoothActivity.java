package com.uni.stuttgart.ipvs.androidgateway;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
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
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.Bluetooth;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothImpl;

import static android.widget.Toast.LENGTH_SHORT;

public class BluetoothActivity extends AppCompatActivity {

    private BluetoothConfigurationManager bluetoothConfig;
    private Bluetooth bluetooth;

    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        bluetooth = new BluetoothImpl();
        bluetooth.setContext(getApplicationContext());

        bluetoothConfig = new BluetoothConfigurationManager();
        bluetooth.setBluetoothAdapter(bluetoothConfig.getBluetoothAdapter());
        turnBluetooth();

        /*bluetooth.setBleScanner(bluetoothConfig.getBleDevice());
        bluetoothConfig.setBluetooth(bluetooth);
        bluetoothConfig.scanBluetooth();*/

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

        turnBluetooth();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //scanBluetooth();
                } else {
                    Toast.makeText(getApplicationContext(), "Location access is required to scan for Bluetooth devices.", LENGTH_SHORT).show();
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

    private void turnBluetooth() {
        if (bluetooth.getBluetoothAdapter() == null || !bluetooth.getBluetoothAdapter().isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
        }
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

}
