package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.location.LocationRequest;
import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.CharacteristicsDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.ServicesDatabase;
import com.uni.stuttgart.ipvs.androidgateway.helper.BroadcastReceiverHelper;

import java.util.Timer;
import java.util.TimerTask;

/**
 * This class is used to start and stop services of Gateway and also for UI in Gateway Program
 */

public class GatewayActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private LocationRequest mLocation;
    private Intent mGatewayService;
    private EditText textArea;
    private Menu menuBar;

    private boolean mProcessing = false;
    private Context context;
    private GatewayController mService;
    private boolean mBound;

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;

    private BleDeviceDatabase bleDeviceDatabase = new BleDeviceDatabase(this);
    private ServicesDatabase bleServicesDatabase = new ServicesDatabase(this);
    private CharacteristicsDatabase bleCharacteristicDatabase = new CharacteristicsDatabase(this);

    private BroadcastReceiverHelper mBReceiver = new BroadcastReceiverHelper();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_gateway, menu);
        menuBar = menu;

        setMenuVisibility();
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
            case R.id.action_start:
                startServiceGateway();
                setMenuVisibility();
                break;
            case R.id.action_stop:
                stopServiceGateway();
                //scheduler.shutdown();
                setMenuVisibility();
                break;
        }
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway);
        context = this;
        mLocation = LocationRequest.create();
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

        setWakeLock();

        registerBroadcastListener();
        checkingPermission();
        clearDatabase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServiceGateway();
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        if(mReceiver != null) {unregisterReceiver(mReceiver);}
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setWakeLock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        setWakeLock();
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
        setWakeLock();
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

    private void setWakeLock() {
        powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockActivity");
        if((wakeLock != null) && (!wakeLock.isHeld())) { wakeLock.acquire(); }
    }

    /**
     * Start and Stop Gateway Controller Section
     */

    private void startServiceGateway() {
        mGatewayService = new Intent(context, GatewayController.class);
        startService(mGatewayService);
        setCommandLine("\n");
        setCommandLine("Start Services...");
        mProcessing = true;
    }

    private void stopServiceGateway() {
        if(mGatewayService != null) {stopService(mGatewayService); }
        setCommandLine("\n");
        setCommandLine("Stop Services...");
        mProcessing = false;
    }

    private void setMenuVisibility() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProcessing) {
                    menuBar.findItem(R.id.menu_refresh_gateway).setActionView(R.layout.action_interdeminate_progress);
                    menuBar.findItem(R.id.action_start).setVisible(false);
                    menuBar.findItem(R.id.action_stop).setVisible(true);
                } else {
                    menuBar.findItem(R.id.menu_refresh_gateway).setActionView(null);
                    menuBar.findItem(R.id.action_start).setVisible(true);
                    menuBar.findItem(R.id.action_stop).setVisible(false);
                }
            }
        });
    }

    private void checkingPermission() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        setCommandLine("Start checking permissions...");
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
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // check if location is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** force user to turn on location service */
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Location Permission!", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please turn on Storage Permission!", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        setCommandLine("Checking permissions done...");
    }

    /**
     * Broadcast Listener & Receiver Section
     */

    private void registerBroadcastListener() {
        IntentFilter filter1 = new IntentFilter(GatewayService.MESSAGE_COMMAND);
        registerReceiver(mReceiver, filter1);

        IntentFilter filter2 = new IntentFilter(GatewayService.TERMINATE_COMMAND);
        registerReceiver(mReceiver, filter2);

        IntentFilter filter3 = new IntentFilter(GatewayService.START_COMMAND);
        registerReceiver(mReceiver, filter3);

        IntentFilter filter4 = new IntentFilter(GatewayService.START_SERVICE_INTERFACE);
        registerReceiver(mReceiver, filter4);

        IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        registerReceiver(mBReceiver, pairingRequestFilter);

        setCommandLine("Registering Broadcast Listener");
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
                //scheduler.shutdown();
                setMenuVisibility();
            } else if (action.equals(GatewayService.START_COMMAND)) {
                String message = intent.getStringExtra("command");
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                    startServiceGateway();
                }
            } else if(action.equals(GatewayService.START_SERVICE_INTERFACE)) {
                String message = intent.getStringExtra("command");
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                setOpenDialog();
            }
        }

    };

    /**
     * Other Routines Section
     */

    private void clearDatabase() {
        bleDeviceDatabase.deleteAllData();
        bleServicesDatabase.deleteAllData();
        bleCharacteristicDatabase.deleteAllData();
    }

    private void waitThread(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * View Related Routine Section
     */

    private void setCommandLine(final String info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textArea.append("\n");
                textArea.append(info);
            }
        });
    }

    private void setOpenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View mView = getLayoutInflater().inflate(R.layout.action_dialog_service_interface, null);
        builder.setTitle("Active Devices");
        final Spinner mSpinner = (Spinner) mView.findViewById(R.id.spinner);
        String[] stringArray = bleDeviceDatabase.getListActiveDevices().toArray(new String[0]);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_item, stringArray);

        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(arrayAdapter);

        builder.setPositiveButton("Launch Interface", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, int i) {
                if (!mSpinner.getSelectedItem().toString().equalsIgnoreCase("Choose a device")) {
                    Toast.makeText(context, "Loading " + mSpinner.getSelectedItem().toString() + " Interface...",
                            Toast.LENGTH_SHORT).show() ;

                    dialogInterface.dismiss();
                }
            }
        });

        builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        builder.setView(mView);
        final AlertDialog dialog = builder.create();
        dialog.show();
        final Timer t = new Timer();

        t.schedule(new TimerTask() {
            public void run() {
                dialog.dismiss(); // when the task active then close the dialog
                t.cancel(); // also just top the timer thread, otherwise, you may receive a crash report
            }
        }, 8000); // after 2 second (or 2000 miliseconds), the task will be active.

    }


}
