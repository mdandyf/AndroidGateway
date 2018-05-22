package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.location.LocationRequest;
import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.CharacteristicsDatabase;
import com.uni.stuttgart.ipvs.androidgateway.database.ServicesDatabase;
import com.uni.stuttgart.ipvs.androidgateway.helper.BroadcastReceiverHelper;

public class GatewayFragment extends Fragment {
    private BluetoothAdapter mBluetoothAdapter;
    private LocationRequest mLocation;
    private EditText textArea;
    private Menu menuBar;
    private Context context;

    private boolean mProcessing = false;
    private Intent mIntentGatewayController;
    private GatewayController mService;
    private boolean mBound;
    private int screenCounter;

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;

    private BroadcastReceiverHelper mBReceiver = new BroadcastReceiverHelper();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gateway, container, false);
        setHasOptionsMenu(true);

        mLocation = LocationRequest.create();
        textArea = (EditText) view.findViewById(R.id.textArea);
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

        powerManager = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockGateway");
        screenCounter = 0;

        setWakeLock();
        checkingPermission();
        registerBroadcastListener();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            startGatewayService();
        }

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_gateway, menu);
        menuBar = menu;
        setMenuVisibility();
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
                startGatewayService();
                setMenuVisibility();
                break;
            case R.id.action_stop:
                stopGatewayService();
                setMenuVisibility();
                break;
        }
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        setWakeLock();
    }

    @Override
    public void onResume() {
        super.onResume();
        setWakeLock();
    }

    @Override
    public void onPause() {
        super.onPause();
        setWakeLock();
    }

    @Override
    public void onStop() {
        super.onStop();
        setWakeLock();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopGatewayService();
        if(wakeLock != null && wakeLock.isHeld()) {wakeLock.release();}
        if(mReceiver != null) {getActivity().unregisterReceiver(mReceiver);}
        if(mBReceiver != null) {getActivity().unregisterReceiver(mBReceiver);}
        getActivity().finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == 1 && resultCode == Activity.RESULT_CANCELED) {
            getActivity().finish();
            return;
        }

        startGatewayService();

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Binding to GatewayController
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            GatewayController.LocalBinder binder = (GatewayController.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    /**
     * Start and Stop Gateway Controller Section
     */

    private void startGatewayService() {
        mIntentGatewayController = new Intent(context, GatewayController.class);
        getActivity().startService(mIntentGatewayController);
        setCommandLine("\n", false);
        setCommandLine("Start Services...", false);
        getActivity().bindService(mIntentGatewayController, mConnection, Context.BIND_AUTO_CREATE);
        mProcessing = true;
    }

    private void stopGatewayService() {
        if(mConnection != null && mProcessing) {getActivity().unbindService(mConnection);}
        if(mIntentGatewayController != null) {getActivity().stopService(mIntentGatewayController); }
        setCommandLine("\n", false);
        setCommandLine("Stop Services...", false);
        mProcessing = false;
    }

    private void setMenuVisibility() {
        getActivity().runOnUiThread(new Runnable() {
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
        setCommandLine("Start checking permissions...", false);
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(context, "Ble is not supported", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothManager.getAdapter() == null) {
            Toast.makeText(context, "Bluetooth is not supported", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // check if location is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** force user to turn on location service */
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Please turn on Location Permission!", Toast.LENGTH_LONG).show();
                getActivity().finish();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Please turn on Storage Permission!", Toast.LENGTH_LONG).show();
                getActivity().finish();
            }
        }

        setCommandLine("Checking permissions done...", false);
    }

    /**
     * Broadcast Listener & Receiver Section
     */

    private void registerBroadcastListener() {
        IntentFilter filter1 = new IntentFilter(GatewayService.MESSAGE_COMMAND);
        getActivity().registerReceiver(mReceiver, filter1);

        IntentFilter filter2 = new IntentFilter(GatewayService.TERMINATE_COMMAND);
        getActivity().registerReceiver(mReceiver, filter2);

        IntentFilter filter3 = new IntentFilter(GatewayService.START_COMMAND);
        getActivity().registerReceiver(mReceiver, filter3);

        IntentFilter filter4 = new IntentFilter(GatewayService.USER_CHOICE_SERVICE);
        getActivity().registerReceiver(mReceiver, filter4);

        IntentFilter filter5 = new IntentFilter(GatewayService.START_NEW_CYCLE);
        getActivity().registerReceiver(mReceiver, filter5);

        IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        getActivity().registerReceiver(mBReceiver, pairingRequestFilter);

        setCommandLine("Start Broadcast Listener...", false);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(GatewayService.MESSAGE_COMMAND)) {
                String message = intent.getStringExtra("command");
                setCommandLine(message, false);
            } else if (action.equals(GatewayService.TERMINATE_COMMAND)) {
                String message = intent.getStringExtra("command");
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                stopGatewayService();
                setMenuVisibility();
            } else if (action.equals(GatewayService.START_COMMAND)) {
                if(!mProcessing) {
                    String message = intent.getStringExtra("command");
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) { startGatewayService(); }
                }
            } else if(action.equals(GatewayService.USER_CHOICE_SERVICE)) {
                String message = intent.getStringExtra("message");
                String macAddress = intent.getStringExtra("macAddress");
                alertDialog("Service Interface", message, "Yes", "No", macAddress);
            } else if(action.equals(GatewayService.START_NEW_CYCLE)) {
                setCommandLine("", true);
            }
        }

    };

    /**
     * Other Routines Section
     */

    private void setWakeLock() {
        if((wakeLock != null) && (!wakeLock.isHeld())) { wakeLock.acquire(); }
    }

    /**
     * View Related Routine Section
     */

    private void alertDialog(final String title, final String message, final String positive, final String negative, final String args) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                final boolean[] hasAnswered = {false};
                final AlertDialog.Builder dialog = new AlertDialog.Builder(context);
                dialog.setTitle(title)
                        .setIcon(R.drawable.ic_info_black_24dp)
                        .setMessage(message)
                        .setNegativeButton(negative, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialoginterface, int i) {
                                // update No to database
                                mService.updateDeviceUserChoice(args, "No");
                                hasAnswered[0] = true;
                            }
                        })
                        .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialoginterface, int i) {
                                //update Yes to database
                                mService.updateDeviceUserChoice(args, "Yes");
                                hasAnswered[0] = true;
                            }
                        });

                final AlertDialog ad = dialog.show();

                //if no answer within 5 seconds, then it is no
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(!hasAnswered[0]) {
                            mService.updateDeviceUserChoice(args, "No");
                            ad.dismiss();
                        }
                    }
                }, 5 * 1000);
            }
        });
    }

    private void setCommandLine(final String info, final boolean clearScreen) {
        screenCounter++;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!clearScreen) {
                    textArea.append("\n");
                    textArea.append(info);
                } else {
                    screenCounter = 0;
                    textArea.getText().clear();
                    textArea.append(info);
                }
            }
        });
    }


}
