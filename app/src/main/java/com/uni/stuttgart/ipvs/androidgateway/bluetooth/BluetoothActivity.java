package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.LogoutActivity;
import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.helper.ExpandableListAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static android.widget.Toast.LENGTH_SHORT;

public class BluetoothActivity extends AppCompatActivity {
    private static final int BLUETOOTH_TIMER = 60; // in seconds

    private PowerManager.WakeLock wakeLock;
    private Intent mBleService;
    private Bluetooth bluetooth;
    private BluetoothService mService;

    private boolean mBound = false;
    private ExpandableListView listView;
    private ExpandableListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, List<String>> listDataChild;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_refresh:
                listDataHeader = new ArrayList<String>();
                listDataChild = new HashMap<>();
                listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
                mService.setBluetoothServiceStart();
                Toast.makeText(getApplicationContext(), "Refreshing", Toast.LENGTH_SHORT).show();
            case R.id.action_scan:
                if (mService.getBluetooth() == null || mService.getBleDevice() == null) {
                    mService.setBluetoothServiceStart();
                    Toast.makeText(getApplicationContext(), "Bluetooth Service is turned on", Toast.LENGTH_SHORT).show();
                } else if (mService.getBluetooth() != null && mService.getScanState() == false && mBound) {
                    mService.scanBluetooth();
                } else if (mService.getScanState()) {
                    Toast.makeText(this, "scan is running", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                }
                break;
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        mBleService = new Intent(this, BluetoothService.class);
        bindService(mBleService, mConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set of bottom navigation command
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        //set of List view command
        listView = (ExpandableListView) findViewById(R.id.listViewBle);
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<>();
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);

        registerIntent();

        bluetooth = new BluetoothImpl();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBound) {
            stopService(mBleService);
        }
        unregisterReceiver(mReceiver);
        wakeLock.release();
    }


    /**
     * for bottom navigation
     */
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivity.class));
                    return true;
                /*case R.id.navigation_dashboard:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivityCharacteristic.class));
                    return true;*/
                case R.id.navigation_notifications:
                    startActivity(new Intent(getApplicationContext(), BluetoothActivityReadWrite.class));
                    return true;
            }
            return false;
        }
    };

    private void registerIntent() {
        //Set a filter to only receive bluetooth state changed events.
        IntentFilter filter1 = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter1);

        //Set a filter to only receive bluetooth gatt connected events.
        IntentFilter filter2 = new IntentFilter(BluetoothService.ACTION_GATT_CONNECTED);
        registerReceiver(mReceiver, filter2);

        //Set a filter when bluetooth trying to connect events
        IntentFilter filter3 = new IntentFilter(BluetoothService.ACTION_GATT_CONNECTING);
        registerReceiver(mReceiver, filter3);

       //Set a filter when bluetooth gatt disconnected events
        IntentFilter filter4 = new IntentFilter(BluetoothService.ACTION_DATA_WRITTEN);
        registerReceiver(mReceiver, filter4);

        //set a filter when bluetooth scan start
        IntentFilter filter5 = new IntentFilter(BluetoothService.SCAN_START);
        registerReceiver(mReceiver, filter5);

        //set a filter when bluetooth scan stop
        IntentFilter filter6 = new IntentFilter(BluetoothService.SCAN_STOP);
        registerReceiver(mReceiver, filter6);

        //set a filter when bluetooth data available
        IntentFilter filter7 = new IntentFilter(BluetoothService.ACTION_DATA_AVAILABLE);
        registerReceiver(mReceiver, filter7);

        //set a filter when bluetooth data available
        IntentFilter filter8 = new IntentFilter(BluetoothService.ACTION_GATT_SERVICE_DISCOVERED);
        registerReceiver(mReceiver, filter8);
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * Defines state changes from Bluetooth
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            if (action.equals(BluetoothService.ACTION_GATT_CONNECTING)) {
                prepareListData(action);
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                switch (bluetoothState) {
                    case BluetoothAdapter.STATE_ON:
                        if (mService.getBluetooth() == null) {
                            mService.setBluetoothServiceStart();
                            Toast.makeText(getApplicationContext(), "Bluetooth Service is turned on", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        mService.setBluetooth(null);
                }
            } else if (action.equals(BluetoothService.ACTION_GATT_CONNECTED)) {
                prepareListData(action);
            } else if (action.equals(BluetoothService.ACTION_GATT_DISCONNECTED)) {
                prepareListData(action);
            } else if (action.equals(BluetoothService.ACTION_GATT_SERVICE_DISCOVERED)) {
                prepareListData(action);
            } else if (action.equals(BluetoothService.SCAN_START)) {
                prepareListData(action);
            } else if (action.equals(BluetoothService.SCAN_STOP)) {
                // start scaning after xx seconds
                Handler mHandler = new Handler();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (bluetooth != null) {
                            mService.setBluetoothServiceStart();
                            Log.d("Ble", "running scanning after " + BLUETOOTH_TIMER + " seconds");
                            ;
                        }
                    }
                }, (BLUETOOTH_TIMER * 1000));
            } else if (action.equals(BluetoothService.ACTION_DATA_AVAILABLE)) {
                prepareListData(action);
            } else if (action.equals(BluetoothService.ACTION_DATA_WRITTEN)) {
                prepareListData(action);
            }

        }
    };

    /**
     * preparing data to be used in listView
     */
    private boolean prepareListData(String action) {

        int changedState = 0;

        // avoid if service has not been connected
        if (!mBound && mService == null) {
            return false;
        }

        // prepare bluetooth data
        bluetooth = mService.getBluetooth();
        if (bluetooth != null) {
            ScanResult scanResult = bluetooth.getBluetoothDevice();
            if (scanResult != null) {
                BluetoothDevice device = scanResult.getDevice();
                if (device != null) {
                    BluetoothGatt gatt = bluetooth.getBluetoothGatt();

                    if (action.equals(BluetoothService.ACTION_GATT_CONNECTING)) {
                        if (device != null && device.getAddress() != null && !listDataHeader.contains(device.getAddress())) {
                            listDataHeader.add(device.getAddress());
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(1, null, null, device, scanResult));
                        }
                    } else if (action.equals(BluetoothService.ACTION_GATT_CONNECTED)) {
                        if (!listDataHeader.contains(device.getAddress())) {
                            listDataHeader.add(device.getAddress());
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(0, null, null, device, scanResult));
                        } else {
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(0, null, null, device, scanResult));
                        }
                        // to be used to change color of appearance connected device
                        changedState = listDataHeader.indexOf(device.getAddress());
                    } else if (action.equals(BluetoothService.ACTION_GATT_DISCONNECTED)) {
                        if (listDataHeader.contains(device.getAddress())) {
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(1, null, null, device, scanResult));
                            // to be used to change color of appearance disconnected device
                            changedState = listDataHeader.indexOf(device.getAddress());
                        }
                    } else if (action.equals(BluetoothService.ACTION_DATA_AVAILABLE)) {
                        if (listDataHeader.contains(device.getAddress())) {
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(3, bluetooth.getBluetoothCharacteristic().get(gatt), null, device, scanResult));
                        }
                    } else if (action.equals(BluetoothService.ACTION_GATT_SERVICE_DISCOVERED)) {
                        if (listDataHeader.contains(device.getAddress())) {
                            listDataChild.put(listDataHeader.get(listDataHeader.indexOf(device.getAddress())), getDataChild(2, null, bluetooth.getBluetoothService().get(gatt), device, scanResult));
                        }
                    }
                }
            }
        }

        listAdapter = new ExpandableListAdapter(getApplicationContext(), listDataHeader, listDataChild);
        if (action.equals(BluetoothService.ACTION_GATT_CONNECTED)) {
            listAdapter.changeGroupFontColor(changedState, null, R.color.colorRed);
        } else if(action.equals(BluetoothService.ACTION_GATT_DISCONNECTED)) {
            listAdapter.changeGroupFontColor(changedState, null, R.color.colorBlack);
        }

        listView.setAdapter(listAdapter);

        return true;
    }

    private List<String> getDataChild(int data, BluetoothGattCharacteristic characteristic, List<BluetoothGattService> service, BluetoothDevice device, ScanResult scanResult) {
        List<String> child = new ArrayList<>();

        /** set device bluetooth name & rssi */
        if (device != null && device.getName() != null) {
            child.add("Name: " + device.getName());
            child.add("Rssi: " + scanResult.getRssi());
        } else {
            child.add("Name: " + "N/A");
            child.add("Rssi: " + scanResult.getRssi());
        }

        switch (data) {
            case 0:
                child.add("Status: connected");
            case 1:
                child.add("Status: disconnected");
            case 2:
                if (service != null) {
                    child.add("Status: connected");
                    child.add("Services discovered: ");
                    for (BluetoothGattService ser : service) {
                        child.add("Service : " + ser.getUuid().toString() + " - " + BluetoothGattLookUp.serviceNameLookup(ser.getUuid()));
                        List<BluetoothGattCharacteristic> characteristics = ser.getCharacteristics();
                        for(BluetoothGattCharacteristic cha : characteristics) {
                            child.add("Characteristic: " + BluetoothGattLookUp.characteristicNameLookup(cha.getUuid()));
                            if(cha.getValue() != null) {child.add("Read Value: " + cha.getStringValue(0));}
                            else if(cha.getStringValue(0) != null) {child.add("Read Value: " + cha.getStringValue(0));}
                            else if(cha.getUuid() != null) {child.add("Read Value: " + BluetoothGattLookUp.characteristicNameLookup(cha.getUuid()));}
                            else if(cha.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) != null) {child.add("Read Value: " + cha.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));}
                            else if(cha.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0) != null) {child.add("Read Value: " + cha.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0));}
                            else {child.add("== end of values ==");}
                        }
                    }
                }
            case 3:
                if (characteristic != null) {
                    child.add("Status: connected");
                    child.add("Characteristic: " + BluetoothGattLookUp.characteristicNameLookup(characteristic.getUuid()));
                    child.add("Value: " + characteristic.getStringValue(0));
                } else {

                }


        }
        return child;
    }
}
