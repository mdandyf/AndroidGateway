package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
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
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.database.DatabaseConnectionManager;
import com.uni.stuttgart.ipvs.androidgateway.helper.ExpandableListAdapter;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattLookUp;
import com.uni.stuttgart.ipvs.androidgateway.helper.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int SCAN_PERIOD = 10;
    private static final String TAG = "Bluetooth Activity";

    private PowerManager.WakeLock wakeLock;
    private Intent mBleService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeService mService;

    private View view;
    private Context context;
    private ExpandableListView listView;
    private ExpandableListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, List<String>> listDataChild;

    private List<BluetoothDevice> scanResults;
    private Map<BluetoothDevice, GattDataJson> mapScanResults;
    private DatabaseConnectionManager database;
    private ConcurrentLinkedQueue<BluetoothLe> queue;
    private Menu menuBar;
    private int callbackCounter;

    private boolean mBound = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
        menu.findItem(R.id.menu_refresh).setActionView(null);
        menuBar = menu;
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
            case R.id.action_scan:
                if (mBluetoothAdapter != null && mBluetoothLeScanProcess != null) {
                    listDataHeader = new ArrayList<String>();
                    listDataChild = new HashMap<>();
                    listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
                    callbackCounter = 0;

                    Handler mHandler = new Handler();
                    mBluetoothLeScanProcess.scanLeDevice(true);
                    menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeScanProcess.scanLeDevice(false);
                            scanResults = new ArrayList<>();
                            mapScanResults = new HashMap<>();
                            scanResults = mBluetoothLeScanProcess.getScanResult();
                            mapScanResults = mBluetoothLeScanProcess.getScanProperties();
                            updateUI(scanResults, mapScanResults);
                            menuBar.findItem(R.id.menu_refresh).setActionView(null);
                        }
                    }, SCAN_PERIOD * 1000);
                } else if (mBluetoothAdapter == null) {
                    Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case R.id.action_connect:
                if (mBluetoothAdapter != null && mBluetoothLeScanProcess != null) {
                    if (scanResults != null && !mBluetoothLeScanProcess.getScanState()) {
                        menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
                        connectGatt();
                    } else if (mBluetoothLeScanProcess.getScanState()) {
                        Toast.makeText(this, "Scanning is running, Please wait!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Please refresh or turn on bluetooth!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_subscribe:
                if (mBluetoothAdapter != null && mBound && mService != null) {
                    if (mService.getConnectedGattNum() != 0) {
                        subscribeAndReadGatt();
                        processQueue();
                    } else {
                        Toast.makeText(this, "No Gatt Server is connected yet", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Service for Bluetooth Gatt has not been started", Toast.LENGTH_SHORT).show();
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

        //set of List view command
        listView = (ExpandableListView) findViewById(R.id.listViewBle);
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<>();
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        context = this;

        database = new DatabaseConnectionManager(this);

        registerBroadcastListener();
        if (checkBluetoothState()) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);
            mBleService = new Intent(this, BluetoothLeService.class);
            bindService(mBleService, mConnection, Context.BIND_AUTO_CREATE);
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
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            mService.disconnect();
            unbindService(mConnection);
        }
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

    /**
     * Defines state changes from Bluetooth
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            setBroadcastResult(intent, action);
            if(scanResults.size() == callbackCounter) {
                menuBar.findItem(R.id.menu_refresh).setActionView(null);
            }

        }

    };

    /**
     * Connect to the Gatt Server
     */
    private void connectGatt() {
        if (mService != null && mBound) {
            for (BluetoothDevice device : scanResults) {
                mService.connect(device);
            }
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth Service is not connected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Subscribe to every changes happened in Gatt Server
     */
    private void subscribeAndReadGatt() {
        List<BluetoothGatt> listGatt = mService.getConnectedGatt();
        queue = new ConcurrentLinkedQueue<>();
        BluetoothLe ble = new BluetoothLe();

        for (BluetoothGatt gatt : listGatt) {
            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                    for (int i = 0; i < properties.length(); i++) {
                        try {
                            String property = properties.getString(i);
                            if (property.equals("Read")) {
                                ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.READ);
                            } else if (property.equals("Write")) {
                                ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.WRITE);
                            } else if (property.equals("Notify")) {
                                ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_NOTIFY);
                            } else if (property.equals("Indicate")) {
                                ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_INDICATE);
                            }
                            queue.add(ble);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }

    private void processQueue() {
        if (!queue.isEmpty()) {
            for(BluetoothLe bleQueue = queue.poll(); bleQueue != null; bleQueue = queue.poll()) {
                if (bleQueue != null) {
                    synchronized (bleQueue) {
                        if (bleQueue.getTypeCommand() == BluetoothLe.READ) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mService.setBluetoothGatt(bleQueue.getGatt());
                            mService.readCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID());
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.REGISTER_NOTIFY) {
                            mService.setBluetoothGatt(bleQueue.getGatt());
                            mService.writeDescriptorNotify(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.REGISTER_INDICATE) {
                            mService.setBluetoothGatt(bleQueue.getGatt());
                            mService.writeDescriptorIndication(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.WRITE) {
                            mService.setBluetoothGatt(bleQueue.getGatt());
                            //mService.writeCharacteristic();
                        } else {
                            Log.d(TAG, "Unknown Command Queue");
                        }


                    }
                } else {
                    Log.d(TAG, "Command Queue is empty.");
                }
            }

        }
    }

    private void setBroadcastResult(Intent intent, String action) {
        String jsonData = intent.getStringExtra("bluetoothData");
        if (jsonData != null) {
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                JSONObject json = JsonParser.readJsonObjectFromString(jsonData);
                try {
                    Toast.makeText(getApplicationContext(), "connected to " + json.get("id"), Toast.LENGTH_SHORT).show();
                    updateDatabase(jsonData, action, new Date());
                    callbackCounter++;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "disconnected from " + jsonData, Toast.LENGTH_SHORT).show();
                updateDatabase(jsonData, action, new Date());
                callbackCounter++;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            } else if (BluetoothLeService.ACTION_READ_RSSI.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            } else if (BluetoothLeService.EXTRA_DATA.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            } else if (BluetoothLeService.ACTION_DESCRIPTOR_READ.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            } else if (BluetoothLeService.ACTION_DESCRIPTOR_WRITE.equals(action)) {
                updateUI(jsonData);
                updateDatabase(jsonData, action, new Date());
            }
        }
    }

    /**
     * Update UI before connected
     */
    private void updateUI(List<BluetoothDevice> input, Map<BluetoothDevice, GattDataJson> properties) {
        for (BluetoothDevice device : input) {
            List<String> setProperties = new ArrayList<>();
            GattDataJson jsonData = properties.get(device);
            JSONObject object = jsonData.getJsonAdvertising();
            GattDataJson jsonString = new GattDataJson(object.toString());
            listDataHeader.add(device.getAddress());
            listDataChild.put(device.getAddress(), jsonString.getPreparedChildData());
        }
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
        listView.setAdapter(listAdapter);
    }

    /**
     * Update UI after connected
     */
    private void updateUI(String jsonData) {
        JSONObject json = JsonParser.readJsonObjectFromString(jsonData);
        Log.d(TAG, jsonData);
        try {
            String id = json.getString("id");
            if (listDataHeader.contains(id)) {
                listDataChild.remove(listDataHeader.get(listDataHeader.indexOf(id)));
                GattDataJson jsonString = new GattDataJson(jsonData);
                listDataChild.put(id, jsonString.getPreparedChildData());
                listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);

                View groupView = listAdapter.getGroupView(listDataHeader.indexOf(id), false, null, null);
                listAdapter.changeGroupColor(listDataHeader.indexOf(id), groupView, Color.RED);
                listView.setAdapter(listAdapter);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void updateDatabase(String jsonData, String action, Date date) {
        database.insertData(jsonData, action, date);
    }

}
