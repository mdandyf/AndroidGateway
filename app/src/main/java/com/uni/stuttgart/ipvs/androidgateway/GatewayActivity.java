package com.uni.stuttgart.ipvs.androidgateway;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothJsonDataProcess;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLe;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeScanProcess;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.BluetoothLeService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private List<BluetoothDevice> scanResults;
    private Map<BluetoothDevice, BluetoothJsonDataProcess> mapScanResults;
    private boolean mBound = false;
    private Handler mHandler;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private BluetoothLeService mService;
    private BluetoothLe ble = new BluetoothLe();

    private ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();

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

        execQueue();
        if(!execCommands()) {
            return;
        }

    }

    private void execQueue() {
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.CHECK_PERMISSION);
        queue.add(ble);
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.SCANNING);
        queue.add(ble);
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.CONNECTING);
        queue.add(ble);
        ble = new BluetoothLe(null, null, null, null, BluetoothLe.SUBSCRIBING);
        queue.add(ble);
    }

    private boolean execCommands() {
        if (!queue.isEmpty()) {
            for (BluetoothLe bluetoothLe = (BluetoothLe) queue.poll(); bluetoothLe != null; bluetoothLe = (BluetoothLe) queue.poll()) {
                synchronized (bluetoothLe) {
                   boolean status = commandCall(bluetoothLe.getTypeCommand());
                   if(!status) {
                       return false;
                   }
                }
            }
        }

        return true;
    }

    private boolean checkBluetoothState() {

        /** force user to turn on bluetooth */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            turnOn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(turnOn);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(mBluetoothAdapter == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
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
     * Connect to the Gatt Server
     */
    private void connectGatt() {
        if (mService != null && mBound) {
            for (BluetoothDevice device : scanResults) {
                setCommandLine("connecting to " + device.getAddress());
                mService.connect(device);
            }
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth Service is not connected", Toast.LENGTH_SHORT).show();
        }
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

            // TODO action for next state
        }

    };

    private boolean commandCall(int type) {

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
            setCommandLine("Scanning bluetooth...");
            mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanResults = new ArrayList<>();
                    mapScanResults = new HashMap<>();
                    scanResults = mBluetoothLeScanProcess.getScanResult();
                    mapScanResults = mBluetoothLeScanProcess.getScanProperties();
                    setCommandLine("Stop scanning bluetooth...");
                    setCommandLine("Found " + scanResults.size() + " device(s)");
                }
            }, SCAN_PERIOD * 1000);
            mBluetoothLeScanProcess.scanLeDevice(true);
        } else if (type == BluetoothLe.CONNECTING) {
            //step connect BLE
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(scanResults == null) {
                        try {
                            mHandler.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "wait for scanning process failed");
                        }
                    }
                    setCommandLine("Connecting to device(s)...");
                    if (scanResults != null && !mBluetoothLeScanProcess.getScanState()) {
                        connectGatt();
                    } else if (scanResults.size() == 0) {
                        setCommandLine("No bluetooth device found");
                    }
                }
            });
        } else if (type == BluetoothLe.SUBSCRIBING) {
            mReceiver.goAsync();
        }


        //step disconnected to BLE Gatt Device

        // step subsribed to read characteristic

        // step register to get notified

        // step register to get indicated

        // step reading data

        // step getting data from notification

        // step getting data from identification

        // step saving database

        // step post data to server

        return true;
    }

    /**
     * View Related Routine
     */

    private boolean setCommandLine(String info) {

        countCommandLine++;

        while(true) {
            if(txtLine1.getText() == "") {
                txtLine1.setText(info);
                return true;
            } else if(txtLine2.getText() == "") {
                txtLine2.setText(info);
                return true;
            } else if(txtLine3.getText() == "") {
                txtLine3 .setText(info);
                return true;
            } else if(txtLine4.getText() == "") {
                txtLine4 .setText(info);
                return true;
            } else if(txtLine5.getText() == "") {
                txtLine5.setText(info);
                return true;
            } else if(txtLine6.getText() == "") {
                txtLine6 .setText(info);
                return true;
            } else if(txtLine7.getText() == "") {
                txtLine7 .setText(info);
                return true;
            } else if(txtLine8.getText() == "") {
                txtLine8 .setText(info);
                return true;
            } else if(txtLine9.getText()== "") {
                txtLine9 .setText(info);
                return true;
            } else if(txtLine10.getText()== "") {
                txtLine10.setText(info);
                return true;
            } else {
                if(countCommandLine > 11) {
                    txtLine10 = txtLine9;
                    txtLine9 = txtLine8;
                    txtLine8 = txtLine7;
                    txtLine7 = txtLine6;
                    txtLine6 = txtLine5;
                    txtLine5 = txtLine4;
                    txtLine4 = txtLine3;
                    txtLine3 = txtLine2;
                    txtLine2 = txtLine1;
                    txtLine1.setText(info);
                    return true;
                }
            }

            return false;
        }
    }




}
