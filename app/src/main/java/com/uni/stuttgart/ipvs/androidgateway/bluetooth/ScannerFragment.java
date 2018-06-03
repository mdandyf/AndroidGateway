package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.helper.BroadcastReceiverHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.DeviceListAdapter;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.ImageViewConnectListener;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.ImageViewDisconnectListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.content.Context.POWER_SERVICE;

public class ScannerFragment extends Fragment implements ImageViewConnectListener, ImageViewDisconnectListener {

    private static final int SCAN_PERIOD = 10000;
    private static final String TAG = "Scanner Fragment";

    private PowerManager.WakeLock wakeLock;

    private List<BluetoothGatt> listConnectedGatt;
    private List<BluetoothGatt> listBondedGatt;
    private IGatewayService iGatewayService;

    private boolean mProcessing; // flag to track processing
    private boolean mScanning; // flag to track scanning process
    private boolean mBound;

    private Context context;
    private ExpandableListView listView;
    private DeviceListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, String> listDataHeaderSmall;
    private HashMap<String, List<String>> listDataChild;

    private Menu menuBar;
    private BroadcastReceiverHelper mReceiver = new BroadcastReceiverHelper();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setHasOptionsMenu(true);

        //set of List view command
        listView = (ExpandableListView) view.findViewById(R.id.listViewBle);

        listDataHeader = new ArrayList<>();
        listDataChild = new HashMap<>();
        listDataHeaderSmall = new HashMap<>();

        listAdapter = new DeviceListAdapter(getContext(), listDataHeader, listDataChild);
        listAdapter.setDataHeaderSmall(listDataHeaderSmall);
        listAdapter.setImageConnectListener(this);
        listAdapter.setImageDisconnectListener(this);
        listAdapter.setConnectionListener(false, null);
        listView.setAdapter(listAdapter);

        PowerManager powerManager = (PowerManager) getActivity().getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        listConnectedGatt = new ArrayList<>();
        listBondedGatt = new ArrayList<>();

        mScanning = false;
        mProcessing = false;

        registerBroadcastListener();

        getActivity().bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_scanner, menu);
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
            case R.id.action_scan:
                scanLeDevice();
                break;
            case R.id.action_stop:
                stopLeDevice();
                break;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listConnectedGatt.size() > 0) {
            for (BluetoothGatt gatt : listConnectedGatt) {
                try {
                    iGatewayService.disconnectSpecificGatt(gatt.getDevice().getAddress());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        if (listBondedGatt != null && listBondedGatt.size() > 0) {
            for (BluetoothGatt gatt : listBondedGatt) {
                deleteBondInformation(gatt.getDevice());
            }
        }
        wakeLock.release();
        getActivity().unregisterReceiver(mScannerReceiver);
        getActivity().unregisterReceiver(mReceiver);
        getActivity().unbindService(mConnection);
        mProcessing = false;
        getActivity().finish();
    }

    @Override
    public void imageViewConnectClicked(View v) {
        if (!mScanning) {
            final String macAddress = (String) v.getTag();
            Toast.makeText(context, "Connecting to " + macAddress, Toast.LENGTH_SHORT).show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    connectLeDevice(macAddress);
                }
            }).start();
        } else {
            Toast.makeText(context, "Scanning is running, Please wait!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void imageViewDisconnectClicked(View v) {
        String macAddress = (String) v.getTag();
        disconnectLeDevice(macAddress);
        if (listBondedGatt != null && listBondedGatt.size() != 0) {
            for (BluetoothGatt gatt : listBondedGatt) {
                if (gatt.getDevice().getAddress().equals(macAddress)) {
                    deleteBondInformation(gatt.getDevice());
                }
            }
        }
        Toast.makeText(context, "Disconnecting from  " + macAddress, Toast.LENGTH_SHORT).show();
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            iGatewayService = IGatewayService.Stub.asInterface(service);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private void scanLeDevice() {
        mScanning = true;
        mProcessing = true;

        try {
            iGatewayService.setHandler(null, "mScannerHandler", "Scanner");
            iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.SCANNING, null, 0);
            iGatewayService.execScanningQueue();
            setMenuVisibility();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopLeDevice();
                }
            }, SCAN_PERIOD);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void stopLeDevice() {
        mScanning = false;
        try {
            iGatewayService.addQueueScanning(null, null, 0, BluetoothLeDevice.STOP_SCAN, null, 0);
            iGatewayService.execScanningQueue();
            setMenuVisibility();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void connectLeDevice(final String macAddress) {
        try {
            PBluetoothGatt parcellGatt = iGatewayService.doConnecting(macAddress);
            listConnectedGatt.add(parcellGatt.getGatt());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void disconnectLeDevice(String macAddress) {
        try {
            PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
            parcelBluetoothGatt.setGatt(findConnectedGatt(macAddress));
            iGatewayService.doDisconnected(parcelBluetoothGatt, "ScannerFragment");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private BluetoothGatt findConnectedGatt(String macAddress) {
        for (BluetoothGatt gatt : listConnectedGatt) {
            if (gatt.getDevice().getAddress().equals(macAddress)) {
                return gatt;
            }
        }

        return null;
    }

    private void registerBroadcastListener() {
        final IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        getActivity().registerReceiver(mReceiver, pairingRequestFilter);

        IntentFilter filter = new IntentFilter(BluetoothLeDevice.UI_CONNECTED);
        getActivity().registerReceiver(mScannerReceiver, filter);

        IntentFilter filter2 = new IntentFilter(BluetoothLeDevice.UI_DISCONNECTED);
        getActivity().registerReceiver(mScannerReceiver, filter2);

        IntentFilter filter3 = new IntentFilter(BluetoothLeDevice.UI_SCAN);
        getActivity().registerReceiver(mScannerReceiver, filter3);
    }

    private final BroadcastReceiver mScannerReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothLeDevice.UI_SCAN)) {
                String deviceAddress = intent.getStringExtra("DeviceAddress");
                String[] deviceData = intent.getStringArrayExtra("DeviceData");
                updateUIScan(deviceAddress, getListFromArray(deviceData));
            } else if (action.equals(BluetoothLeDevice.UI_CONNECTED)) {
                String deviceAddress = intent.getStringExtra("DeviceAddress");
                String[] deviceData = intent.getStringArrayExtra("DeviceData");
                updateUIConnected(deviceAddress, getListFromArray(deviceData));
            } else if (action.equals(BluetoothLeDevice.UI_DISCONNECTED)) {
                String deviceAddress = intent.getStringExtra("DeviceAddress");
                String[] deviceData = intent.getStringArrayExtra("DeviceData");
                updateUIDisonnected(deviceAddress, getListFromArray(deviceData));
            }
        }
    };

    private void updateUIScan(final String deviceAddress, final List<String> deviceData) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!listDataHeader.contains(deviceAddress)) { listDataHeader.add(deviceAddress); }
                listDataChild.put(deviceAddress, deviceData);
                listDataHeaderSmall.put(deviceAddress, "Disconnected");
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updateUIConnected(final String deviceAddress, final List<String> deviceData) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(deviceAddress)) {
                    listDataChild.put(deviceAddress, deviceData);
                    listDataHeaderSmall.put(deviceAddress, "Connected");
                    listAdapter.setConnectionListener(true, deviceAddress);
                    listAdapter.setTextAppearanceHeader("Large");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void updateUIDisonnected(final String deviceAddress, final List<String> deviceData) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(deviceAddress)) {
                    listDataHeaderSmall.put(deviceAddress, "Disconnected");
                    listAdapter.setTextAppearanceHeader("Medium");
                    listAdapter.setConnectionListener(false, deviceAddress);
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private List<String> getListFromArray(String[] input) {
        List<String> result = new ArrayList<>();
        for(int i = 0; i < input.length; i++) {
            result.add(input[i]);
        }
        return result;
    }

    private void setMenuVisibility() {
        if (!mScanning) {
            // when scanning is stop
            menuBar.findItem(R.id.menu_refresh).setActionView(null);
            menuBar.findItem(R.id.action_scan).setVisible(true);
            menuBar.findItem(R.id.action_stop).setVisible(false);
            menuBar.findItem(R.id.action_connect).setVisible(false);
            menuBar.findItem(R.id.action_disconnect).setVisible(false);
        } else {
            // when scanning is running
            menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
            menuBar.findItem(R.id.action_scan).setVisible(false);
            menuBar.findItem(R.id.action_stop).setVisible(true);
            menuBar.findItem(R.id.action_connect).setVisible(false);
            menuBar.findItem(R.id.action_disconnect).setVisible(false);
        }
    }


    public static void deleteBondInformation(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
