package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.helper.ExpandableListAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeActivity extends Activity {

    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int SCAN_PERIOD = 10000;
    private static final String TAG = "BLE Activity";

    private PowerManager.WakeLock wakeLock;
    private Intent mBleService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeService mService;

    private View view;
    private ExpandableListView listView;
    private ExpandableListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, List<String>> listDataChild;

    private List<BluetoothDevice> scanResults;
    private Map<BluetoothDevice, List<String>> mapScanResults;

    private boolean mBound = false;

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
                if (mBluetoothAdapter != null && mBluetoothLeScanProcess != null) {
                    listDataHeader = new ArrayList<String>();
                    listDataChild = new HashMap<>();
                    listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
                    mBluetoothLeScanProcess.scanLeDevice(true);
                } else if (mBluetoothAdapter == null) {
                    Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_scan:
                if (mBluetoothAdapter != null && mBluetoothLeScanProcess != null) {
                    mBluetoothLeScanProcess.scanLeDevice(true);
                    makeInfoMessage("scanning", SCAN_PERIOD/1000);
                    Handler mHandler = new Handler();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scanResults = new ArrayList<>();
                            mapScanResults = new HashMap<>();
                            scanResults = mBluetoothLeScanProcess.getScanResult();
                            mapScanResults = mBluetoothLeScanProcess.getScanProperties();
                            updateUI(scanResults, mapScanResults);
                        }
                    }, SCAN_PERIOD);
                } else if (mBluetoothAdapter == null) {
                    Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_connect:
                if (mBluetoothAdapter != null && mBluetoothLeScanProcess != null) {
                    if (scanResults != null && !mBluetoothLeScanProcess.getScanState()) {
                        connectGatt();
                    } else if (mBluetoothLeScanProcess.getScanState()) {
                        Toast.makeText(this, "Scanning is running, Please wait!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Please refresh bluetooth!", Toast.LENGTH_SHORT).show();
                }
        }
        return true;
    }


    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = (View) findViewById(R.id.listViewBle);

        // set of bottom navigation command
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        //set of List view command
        listView = (ExpandableListView) findViewById(R.id.listViewBle);
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<>();
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        registerBroadcastListener();
        if (checkBluetoothState()) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothLeScanProcess = new BluetoothLeScanProcess();
            mService = new BluetoothLeService();
            mBluetoothLeScanProcess.setContext(this);
            mBluetoothLeScanProcess.setBluetoothAdapter(mBluetoothAdapter);
        } else {
            Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
            return;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {super.onStop();}

    @Override
    public void onDestroy() {
        super.onDestroy();
        mService.disconnect();
        unbindService(mConnection);
        unregisterReceiver(mReceiver);
        wakeLock.release();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mBluetoothLeScanProcess.scanLeDevice(true);
                } else {
                    Toast.makeText(this, "Location access is required to scan for Bluetooth devices.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
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
                    startActivity(new Intent(getApplicationContext(), com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeActivity.class));
                    return true;
                case R.id.navigation_notifications:
                    startActivity(new Intent(getApplicationContext(), BluetoothLeActivityReadWrite.class));
                    return true;
            }
            return false;
        }
    };

    private void registerBroadcastListener() {
        //Set a filter to only receive bluetooth state changed events.
        IntentFilter filter1 = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter1);

        //Set a filter to only receive bluetooth gatt connected events.
        IntentFilter filter2 = new IntentFilter(BluetoothLeService.ACTION_GATT_CONNECTED);
        registerReceiver(mReceiver, filter2);

        //Set a filter when bluetooth trying to connect events
        IntentFilter filter3 = new IntentFilter(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        registerReceiver(mReceiver, filter3);

        //Set a filter when bluetooth gatt disconnected events
        IntentFilter filter4 = new IntentFilter(BluetoothLeService.ACTION_DESCRIPTOR_WRITE);
        registerReceiver(mReceiver, filter4);

        //set a filter when bluetooth scan start
        IntentFilter filter5 = new IntentFilter(BluetoothLeService.ACTION_DESCRIPTOR_READ);
        registerReceiver(mReceiver, filter5);

        //set a filter when bluetooth scan stop
        IntentFilter filter6 = new IntentFilter(BluetoothLeService.EXTRA_DATA);
        registerReceiver(mReceiver, filter6);

        //set a filter when bluetooth data available
        IntentFilter filter7 = new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE);
        registerReceiver(mReceiver, filter7);

        //set a filter when bluetooth data available
        IntentFilter filter8 = new IntentFilter(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        registerReceiver(mReceiver, filter8);
    }

    private boolean checkBluetoothState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Location Access", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        /** force user to turn on bluetooth */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            turnOn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(turnOn);
        } else {
            // Nothing to do
        }

        return true;
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /** Defines state changes from Bluetooth */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            setBroadcastResult(intent, action);
        }

    };

    /** Connect to the Gatt Server */
    private void connectGatt() {
        mBleService = new Intent(this, BluetoothLeService.class);
        bindService(mBleService, mConnection, Context.BIND_AUTO_CREATE);
        makeInfoMessage("connecting", SCAN_PERIOD/1000);
        for(BluetoothDevice device : scanResults) {
            mBleService.putExtra("bluetoothDevice", device);
            mService.connect();
        }
    }

    /** info message on bottom screen */
    private void makeInfoMessage(String message, int duration) {
        Snackbar.make(view, message, duration)
                .setAction("Action", null).show();
    }

    private void setBroadcastResult(Intent intent, String action) {
        String deviceAddress = intent.getStringExtra("blutoothMac");
        if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
            //updateUI();
        } else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {

        } else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

        } else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

        } else if(BluetoothLeService.EXTRA_DATA.equals(action)) {

        } else if(BluetoothLeService.ACTION_DESCRIPTOR_READ.equals(action)) {

        } else if(BluetoothLeService.ACTION_DESCRIPTOR_WRITE.equals(action)) {

        }
    }

    private void updateUI(List<BluetoothDevice> input, Map<BluetoothDevice, List<String>> properties) {
        for(BluetoothDevice device : input) {
            List<String> setProperties = new ArrayList<>();
            setProperties.add("Name : " + device.getName());
            setProperties.add("Status :" + "disconnected");
            setProperties.add("Type: " + device.getType());
            List<String> listProperties = properties.get(device);
            for(String detail : listProperties) {
                setProperties.add(detail);
            }
            listDataHeader.add(device.getAddress());
            listDataChild.put(device.getAddress(), setProperties);
        }
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
    }

    private void updateUI(BluetoothGatt gatt, Map<BluetoothGattService, List<String>> characteristic) {

    }

}
