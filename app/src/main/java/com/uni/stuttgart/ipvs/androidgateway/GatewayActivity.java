package com.uni.stuttgart.ipvs.androidgateway;

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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothGattHelper;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothGattLookUp;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothJsonDataProcess;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLe;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeService;
import com.uni.stuttgart.ipvs.androidgateway.helper.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GatewayActivity extends AppCompatActivity {

    private static final int SCAN_PERIOD = 10;
    private static final String TAG = "Gateway Activity";

    private int countCommandLine;

    private TextView txtLine1;
    private TextView txtLine2;
    private TextView txtLine3;
    private TextView txtLine4;
    private TextView txtLine5;
    private TextView txtLine6;
    private TextView txtLine7;
    private TextView txtLine8;
    private TextView txtLine9;
    private TextView txtLine10;
    private TextView txtLine11;
    private TextView txtLine12;

    private List<BluetoothDevice> scanResults;
    private Map<BluetoothDevice, BluetoothJsonDataProcess> mapScanResults;
    private boolean mBound = false;
    private Handler mHandler;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeService mService;

    private ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
    private BluetoothLe ble = new BluetoothLe();

    private Context context = this;
    private Intent mBleService;
    private Object lock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway);

        countCommandLine = 0;
        txtLine1 = (TextView) findViewById(R.id.textView1);
        txtLine2 = (TextView) findViewById(R.id.textView2);
        txtLine3 = (TextView) findViewById(R.id.textView3);
        txtLine4 = (TextView) findViewById(R.id.textView4);
        txtLine5 = (TextView) findViewById(R.id.textView5);
        txtLine6 = (TextView) findViewById(R.id.textView6);
        txtLine7 = (TextView) findViewById(R.id.textView7);
        txtLine8 = (TextView) findViewById(R.id.textView8);
        txtLine9 = (TextView) findViewById(R.id.textView9);
        txtLine10 = (TextView) findViewById(R.id.textView10);
        txtLine11 = (TextView) findViewById(R.id.textView11);
        txtLine12 = (TextView) findViewById(R.id.textView11);

        initQueue();
        execQueue();
    }

    private void initQueue() {
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.CHECK_PERMISSION);
        queue.add(ble);
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.SCANNING);
        queue.add(ble);
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.CONNECTING);
        queue.add(ble);
    }

    private boolean execQueue() {
        if (!queue.isEmpty()) {
            for (BluetoothLe bluetoothLe = (BluetoothLe) queue.poll(); bluetoothLe != null; bluetoothLe = (BluetoothLe) queue.poll()) {
                synchronized (bluetoothLe) {
                    boolean status = commandCall(bluetoothLe);
                    if (!status) {
                        finish();
                    }
                }
            }
        }

        return true;
    }

    private boolean commandCall(BluetoothLe bluetoothLe) {

        int type = bluetoothLe.getTypeCommand();

        if (type == BluetoothLe.CHECK_PERMISSION) {

            setCommandLine("Start sequence commands...");

            //step checking bluetoothAdapter & permissions
            setCommandLine("Checking permissions...");
            if (!checkBluetoothState() || mBluetoothAdapter == null) {
                Toast.makeText(this, "Please turn on bluetooth!", Toast.LENGTH_SHORT).show();
                return false;
            } else if (!checkLocationState()) {
                Toast.makeText(this, "Please turn on location!", Toast.LENGTH_SHORT).show();
                return false;
            }
            setCommandLine("Checking permissions done...");

        } else if (type == BluetoothLe.SCANNING) {
            //step scan BLE
            scanResults = new ArrayList<>();
            mapScanResults = new HashMap<>();
            setCommandLine("Scanning bluetooth...");
            mBluetoothLeScanProcess.scanLeDevice(true);
            mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        mBluetoothLeScanProcess.scanLeDevice(false);
                        scanResults = mBluetoothLeScanProcess.getScanResult();
                        mapScanResults = mBluetoothLeScanProcess.getScanProperties();
                        setCommandLine("Stop scanning bluetooth...");
                        setCommandLine("Found " + scanResults.size() + " device(s)");
                        lock.notifyAll();
                    }
                }
            }, SCAN_PERIOD * 1000);

        } else if (type == BluetoothLe.CONNECTING) {

            Thread threadBleConnect = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        try {
                            Log.d(TAG, "Waiting for scanning to be finished");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        setMessage("Starting Ble Service...");
                        mBleService = new Intent(context, BluetoothLeService.class);
                        bindService(mBleService, mConnection, Context.BIND_AUTO_CREATE);

                        /** wait until service has been started */
                        while (mService == null) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (mService != null && mBound) {
                            setMessage("Ble Service started...");

                            setMessage("Registering Broadcast Listener...");
                            registerBroadcastListener();
                            setMessage("Broadcast Listener Registered...");

                            for (BluetoothDevice device : scanResults) {
                                setMessage("connecting to " + device.getAddress());
                                mService.connect(device);
                            }
                        } else {
                            setMessage("Bluetooth Service is not connected");
                        }

                    }
                }

                private void setMessage(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCommandLine(message);
                        }
                    });
                }
            });

            threadBleConnect.start();

        } else if (type == BluetoothLe.CONNECTED) {
            setCommandLine("Connected to " + bluetoothLe.getGatt().getDevice().getAddress());
        } else if (type == BluetoothLe.DISCONNECTED) {
            setCommandLine("Disconnected from " + bluetoothLe.getGatt().getDevice().getAddress());
        } else if (type == BluetoothLe.READ) {
            setCommandLine("Reading Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mService.setBluetoothGatt(bluetoothLe.getGatt());
            mService.readCharacteristic(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID());
        } else if (type == BluetoothLe.REGISTER_NOTIFY) {
            setCommandLine("Registering Notify Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mService.setBluetoothGatt(bluetoothLe.getGatt());
            mService.writeDescriptorNotify(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), BluetoothGattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.REGISTER_INDICATE) {
            setCommandLine("Registering Indicate Characteristic " + bluetoothLe.getCharacteristicUUID().toString());
            mService.setBluetoothGatt(bluetoothLe.getGatt());
            mService.writeDescriptorIndication(bluetoothLe.getServiceUUID(), bluetoothLe.getCharacteristicUUID(), BluetoothGattLookUp.shortUUID("2902"));
        } else if (type == BluetoothLe.WRITE) {
            mService.setBluetoothGatt(bluetoothLe.getGatt());
            //mService.writeCharacteristic();
        }
        return true;
    }

    /**
     * Defines state changes from Bluetooth
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            String jsonData = intent.getStringExtra("bluetoothData");

            if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                switch (bluetoothState) {
                    case BluetoothAdapter.STATE_ON:
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        break;
                }
            }

            if (jsonData != null) {
                if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                    BluetoothGatt gatt = mService.getBluetoothGatt();
                    ble = new BluetoothLe(gatt, null, null, null, BluetoothLe.CONNECTED);
                    ble.setJsonData(jsonData);
                    queue.add(ble);
                    execQueue();
                } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                    BluetoothGatt gatt = mService.getBluetoothGatt();
                    ble = new BluetoothLe(gatt, null, null, null, BluetoothLe.DISCONNECTED);
                    ble.setJsonData(jsonData);
                    queue.add(ble);
                    execQueue();
                } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                    BluetoothGatt gatt = mService.getBluetoothGatt();
                    for (BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            JSONArray properties = BluetoothGattHelper.decodeProperties(characteristic);
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

                                    ble.setJsonData(jsonData);
                                    queue.add(ble);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    execQueue();
                }
            }
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

    private boolean checkBluetoothState() {

        /** force user to turn on bluetooth */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            turnOn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(turnOn);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }

            mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);

        } else {
            // bluetooth is already turned on
        }

        return true;
    }

    private boolean checkLocationState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** force user to turn on location service */
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Location Access", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        return true;
    }

    /**
     * View Related Routine
     */

    private boolean setCommandLine(String info) {

        countCommandLine++;

        while (true) {
            if (txtLine1.getText() == "") {
                txtLine1.setText(info);
                return true;
            } else if (txtLine2.getText() == "") {
                txtLine2.setText(info);
                return true;
            } else if (txtLine3.getText() == "") {
                txtLine3.setText(info);
                return true;
            } else if (txtLine4.getText() == "") {
                txtLine4.setText(info);
                return true;
            } else if (txtLine5.getText() == "") {
                txtLine5.setText(info);
                return true;
            } else if (txtLine6.getText() == "") {
                txtLine6.setText(info);
                return true;
            } else if (txtLine7.getText() == "") {
                txtLine7.setText(info);
                return true;
            } else if (txtLine8.getText() == "") {
                txtLine8.setText(info);
                return true;
            } else if (txtLine9.getText() == "") {
                txtLine9.setText(info);
                return true;
            } else if (txtLine10.getText() == "") {
                txtLine10.setText(info);
                return true;
            } else if (txtLine11.getText() == "") {
                txtLine11.setText(info);
                return true;
            } else if (txtLine12.getText() == "") {
                txtLine12.setText(info);
                return true;
            } else {
                if (countCommandLine > 13) {
                    txtLine1.setText(txtLine2.getText());
                    txtLine2.setText(txtLine3.getText());
                    txtLine3.setText(txtLine4.getText());
                    txtLine4.setText(txtLine5.getText());
                    txtLine5.setText(txtLine6.getText());
                    txtLine6.setText(txtLine7.getText());
                    txtLine7.setText(txtLine8.getText());
                    txtLine8.setText(txtLine9.getText());
                    txtLine9.setText(txtLine10.getText());
                    txtLine10.setText(txtLine11.getText());
                    txtLine11.setText(txtLine12.getText());
                    txtLine12.setText(info);
                    return true;
                }
            }

            return false;
        }
    }


}
