package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;

public class GatewayActivity extends AppCompatActivity {

    private int countCommandLine;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mBound = false;
    private GatewayService mService;
    private Intent mGatewayService;
    private EditText textArea;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway);

        countCommandLine = 0;

        textArea = (EditText) findViewById(R.id.textArea);
        textArea.setFocusable(false);
        textArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });

        registerBroadcastListener();
        checkBluetoothState();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServiceGateway();
        unregisterReceiver(mReceiver);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == 1 && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        startServiceGateway();

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startServiceGateway() {
        mGatewayService = new Intent(this, GatewayService.class);
        startService(mGatewayService);
        setCommandLine("Starting Gateway Service...");
    }

    private void stopServiceGateway() {
        if (mGatewayService != null) {
            stopService(mGatewayService);
        }
    }

    private void checkBluetoothState() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothManager.getAdapter() == null) {
            Toast.makeText(this, "bluetooth is not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void registerBroadcastListener() {
        IntentFilter filter1 = new IntentFilter(GatewayService.MESSAGE_COMMAND);
        registerReceiver(mReceiver, filter1);

        IntentFilter filter2 = new IntentFilter(GatewayService.TERMINATE_COMMAND);
        registerReceiver(mReceiver, filter2);

        IntentFilter filter3 = new IntentFilter(GatewayService.START_COMMAND);
        registerReceiver(mReceiver, filter3);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.MESSAGE_COMMAND)) {
                String message = intent.getStringExtra("command");
                setCommandLine(message);
            } else if (action.equals(GatewayService.TERMINATE_COMMAND)) {
                String message = intent.getStringExtra("command");
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                stopServiceGateway();
            } else if(action.equals(GatewayService.START_COMMAND)) {
                String message = intent.getStringExtra("command");
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                    startServiceGateway();
                }
            } else if(action.equals(GatewayService.STOP_COMMAND)) {
                String message = intent.getStringExtra("command");
                setCommandLine(message);
                stopServiceGateway();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                            startServiceGateway();
                        }
                    }
                }, 10000);
            }

        }

    };

    /**
     * View Related Routine
     */

    private void setCommandLine(String info) {
        textArea.append("\n");
        textArea.append(info);
    }


}
