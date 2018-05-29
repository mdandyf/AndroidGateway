package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
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
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback.BluetoothLeGattCallback;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.GatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PMessageHandler;
import com.uni.stuttgart.ipvs.androidgateway.helper.BroadcastReceiverHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.DeviceListAdapter;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.ImageViewConnectListener;
import com.uni.stuttgart.ipvs.androidgateway.helper.view.ImageViewDisconnectListener;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.content.Context.POWER_SERVICE;

public class ScannerFragment extends Fragment implements ImageViewConnectListener, ImageViewDisconnectListener {

    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int SCAN_PERIOD = 10;
    private static final String TAG = "Scanner Fragment";

    private PowerManager.WakeLock wakeLock;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private List<BluetoothDevice> listDevice;
    private BluetoothGatt mBluetoothGatt;
    private List<BluetoothGatt> listConnectedGatt;
    private List<BluetoothGatt> listBondedGatt;
    private List<BluetoothGattCharacteristic> listCharBondRead;
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
    private ConcurrentLinkedQueue<BluetoothLeGatt> queue;
    private HandlerThread mThread;
    private Handler mHandlerMessage;

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

        listDataHeader = new ArrayList<String>();
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

        mThread = new HandlerThread("mThreadScannerCallback");
        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new
                BluetoothLeScanProcess(getContext(), mBluetoothAdapter);
        mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
        listDevice = new ArrayList<>();
        listConnectedGatt = new ArrayList<>();
        listBondedGatt = new ArrayList<>();
        listCharBondRead = new ArrayList<>();

        mScanning = false;
        mProcessing = false;

        final IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        getActivity().registerReceiver(mReceiver, pairingRequestFilter);

        getActivity().bindService(new Intent(context, GatewayService.class), mConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    @Override
    public void onStart() { super.onStart(); }

    @Override
    public void onResume() { super.onResume(); }

    @Override
    public void onStop() { super.onStop(); }

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
        if(listBondedGatt != null && listBondedGatt.size() > 0) {
            for(BluetoothGatt gatt : listBondedGatt) {
                deleteBondInformation(gatt.getDevice());
            }
        }
        wakeLock.release();
        getActivity().unregisterReceiver(mReceiver);
        getActivity().unbindService(mConnection);
        mProcessing = false;
        getActivity().finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mBluetoothLeScanProcess.scanLeDevice(true);
                } else {
                    Toast.makeText(getContext(), "Location access is required to scan for Bluetooth devices.", Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
            }
        }
    }

    @Override
    public void imageViewConnectClicked(View v) {
        if(!mScanning) {
            final String macAddress = (String) v.getTag();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    connectLeDevice(macAddress);
                    Toast.makeText(context, "Connecting to " + macAddress, Toast.LENGTH_SHORT).show();
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
        if(listBondedGatt != null && listBondedGatt.size() != 0) {
            for(BluetoothGatt gatt : listBondedGatt) {
                if(gatt.getDevice().getAddress().equals(macAddress)) {
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

        if (mBluetoothLeScanProcess != null) {
            mProcessing = true;
            mBluetoothLeScanProcess.scanLeDevice(true);
            setMenuVisibility();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mScanning) {
                        stopLeDevice();
                    }
                }
            }, SCAN_PERIOD * 1000);
        }
    }

    private void stopLeDevice() {
        mScanning = false;

        if (mBluetoothLeScanProcess != null) {
            mBluetoothLeScanProcess.scanLeDevice(false);
            setMenuVisibility();
        }
    }

    private void connectLeDevice(final String macAddress) {
        try {
            PMessageHandler parcelMessageHandler = new PMessageHandler();
            parcelMessageHandler.setHandlerMessage(mHandlerMessage);
            iGatewayService.setMessageHandler(parcelMessageHandler);
            iGatewayService.doConnecting(macAddress);
            mProcessing = true;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void disconnectLeDevice(String macAddress) {
        try {
            PMessageHandler parcelMessageHandler = new PMessageHandler();
            parcelMessageHandler.setHandlerMessage(mHandlerMessage);
            iGatewayService.setMessageHandler(parcelMessageHandler);
            PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
            parcelBluetoothGatt.setGatt(findConnectedGatt(macAddress));
            iGatewayService.doDisconnected(parcelBluetoothGatt, "ScannerFragment");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private BluetoothGatt findConnectedGatt(String macAddress) {
        for(BluetoothGatt gatt : listConnectedGatt) {
            if(gatt.getDevice().getAddress().equals(macAddress)) {
                return gatt;
            }
        }

        return null;
    }

    /**
     * Handling callback messages
     */
    private Handler.Callback mHandlerCallback = new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {

            boolean processBle = false;

            try {

                if (msg.what == 0) {
                    // getting results from scanning
                    if (msg.arg1 == 0) {

                    } else if (msg.arg1 == 1) {

                    } else if (msg.arg1 == 2) {
                        // Next, do connecting
                    } else if (msg.arg1 == 3) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ScanResult result = ((ScanResult) msg.obj);
                            updateUIScan(result);
                            queue = new ConcurrentLinkedQueue<>();
                        }
                    } else if (msg.arg1 == 4) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            List<ScanResult> results = ((List<ScanResult>) msg.obj);
                            for (ScanResult result : results) {
                                updateUIScan(result);
                            }
                            queue = new ConcurrentLinkedQueue<>();
                        }
                    } else if (msg.arg1 == 5) {
                        final Map<BluetoothDevice, GattDataJson> mapDevice = ((Map<BluetoothDevice, GattDataJson>) msg.obj);
                        updateUIScan(mapDevice);
                        queue = new ConcurrentLinkedQueue<>();
                    }

                } else if (msg.what == 1) {
                    //getting results from BluetoothLeGattCallback for connecting
                    if (msg.arg1 == 0) {
                        // read all bluetoothGatt servers
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        processBle = false;
                    } else if (msg.arg1 == 1) {
                        // read all bluetoothGatt connected servers
                        final BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                        if(gatt != null) {
                            mBluetoothGatt = gatt;
                            queueCommand(gatt, null, null, null, null, BluetoothLeDevice.CONNECTED);
                            processBle = true;
                        }
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        final BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                        if(gatt != null) {
                            mBluetoothGatt = gatt;
                            queueCommand(gatt, null, null, null, null, BluetoothLeDevice.DISCONNECTED);
                            processBle = true;
                        }
                    } else if (msg.arg1 == 3) {
                        // read all bluetoothGatt rssi
                        // do nothing
                    } else if (msg.arg1 == 4) {
                        //discovered services and read all characteristics
                        BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                        mBluetoothGatt = gatt;
                        queueSubscribeOrReadGatt(gatt);
                        processBle = true;
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                        for(Map.Entry entry : mapGatt.entrySet()) {
                            mBluetoothGatt = (BluetoothGatt) entry.getKey();
                            updateUIConnected(mBluetoothGatt, (BluetoothGattCharacteristic) entry.getValue());
                        }
                        processBle = true;
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                        for(Map.Entry entry : mapGatt.entrySet()) {
                            mBluetoothGatt = (BluetoothGatt) entry.getKey();
                            updateUIConnected(mBluetoothGatt, (BluetoothGattCharacteristic) entry.getValue());
                        }
                        processBle = true;
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                        for(Map.Entry entry : mapGatt.entrySet()) {
                            mBluetoothGatt = (BluetoothGatt) entry.getKey();
                            updateUIConnected(mBluetoothGatt, (BluetoothGattCharacteristic) entry.getValue());
                        }
                        processBle = true;
                    } else if (msg.arg1 == 8) {
                        //onDescriptorRead
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        processBle = true;
                    } else if (msg.arg1 == 9) {
                        //onDescriptorWrite
                    } else if(msg.arg1 == 10) {
                        Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                        for(Map.Entry entry : mapGatt.entrySet()) {
                            mBluetoothGatt = (BluetoothGatt) entry.getKey();
                            BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                            if(!listCharBondRead.contains(characteristic)) {listCharBondRead.add(characteristic);}
                            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                            getActivity().registerReceiver(mReceiver, filter);
                        }
                        processBle = false;
                    }

                    // processing next queue
                    if (processBle & mProcessing) {
                        processQueue();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }


    };

    private synchronized void queueCommand(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID, byte[] data, int typeCommand) {
        BluetoothLeGatt ble = new BluetoothLeGatt(gatt, serviceUUID, characteristicUUID, descriptorUUID, data, typeCommand);
        queue.add(ble);
    }

    private synchronized void queueSubscribeOrReadGatt(BluetoothGatt gatt) {
        BluetoothLeGatt ble = new BluetoothLeGatt();

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, null, BluetoothLeGatt.READ);
                            queue.add(ble);
                        } else if (property.equals("Write")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, null, BluetoothLeGatt.WRITE);
                            queue.add(ble);
                        } else if (property.equals("Notify")) {
                            for(BluetoothGattDescriptor descriptor:characteristic.getDescriptors()) {
                                ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), descriptor.getUuid(), null, BluetoothLeGatt.REGISTER_NOTIFY);
                                queue.add(ble);
                            }
                        } else if (property.equals("Indicate")) {
                            for(BluetoothGattDescriptor descriptor:characteristic.getDescriptors()) {
                                ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), descriptor.getUuid(), null, BluetoothLeGatt.REGISTER_INDICATE);
                                queue.add(ble);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    private synchronized void processQueue() {
        if (!queue.isEmpty()) {
            for (BluetoothLeGatt bleQueue = queue.poll(); bleQueue != null; bleQueue = queue.poll()) {
                if (bleQueue != null) {
                    synchronized (bleQueue) {
                        BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(bleQueue.getGatt());
                        if (bleQueue.getTypeCommand() == BluetoothLeDevice.CONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            Toast.makeText(context, "Connected to " + gatt.getDevice().getAddress(), Toast.LENGTH_SHORT).show();
                            gatt.readRemoteRssi();
                            gatt.discoverServices();
                            updateUIConnected(gatt);
                            listConnectedGatt.add(gatt);
                        } else if (bleQueue.getTypeCommand() == BluetoothLeDevice.DISCONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            updateUIDisonnected(gatt);
                            Toast.makeText(context, "Disconnected from " + gatt.getDevice().getAddress(), Toast.LENGTH_SHORT).show();
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.READ) {
                            gattCallback.readCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID());
                        }  else if (bleQueue.getTypeCommand() == BluetoothLeGatt.REGISTER_NOTIFY) {
                            gattCallback.writeDescriptorNotify(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), bleQueue.getDescriptorUUID());
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.REGISTER_INDICATE) {
                            gattCallback.writeDescriptorIndication(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), bleQueue.getDescriptorUUID());
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.WRITE) {
                            //gattCallback.writeCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), bleQueue.getData());
                        } else if(bleQueue.getTypeCommand() == BluetoothLeGatt.READ_DESCRIPTOR) {
                            gattCallback.readDescriptor(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), bleQueue.getDescriptorUUID());
                        } else if(bleQueue.getTypeCommand() == BluetoothLeDevice.UPDATE_UI_CONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            updateUIConnected(gatt);
                        }
                    }
                } else {
                    Log.d(TAG, "Command Queue is empty.");
                }
            }

        }
    }

    private synchronized void updateUIScan(final ScanResult result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!listDevice.contains(result.getDevice())) {
                listDevice.add(result.getDevice());
            }
            final BluetoothDevice device = result.getDevice();
            final int rssi = result.getRssi();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GattDataJson json = new GattDataJson(device, rssi, null);
                    if (!listDataHeader.contains(device.getAddress())) {
                        listDataHeader.add(device.getAddress());
                        json.setJsonData(json.getJsonAdvertising().toString());
                    } else {
                        json.setJsonData(json.getJsonAdvertising().toString());
                    }
                    listDataChild.put(device.getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(device.getAddress(), "Disconnected");
                    listAdapter.notifyDataSetChanged();
                }
            });
        }

    }

    private synchronized void updateUIScan(Map<BluetoothDevice, GattDataJson> map) {
        for (final Map.Entry entry : map.entrySet()) {
            final BluetoothDevice device = (BluetoothDevice) entry.getKey();
            if (!listDevice.contains(device)) {
                listDevice.add(device);
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GattDataJson json = (GattDataJson) entry.getValue();
                    if (!listDataHeader.contains(device.getAddress())) {
                        listDataHeader.add(device.getAddress());
                        json.setJsonData(json.getJsonAdvertising().toString());
                    } else {
                        json.setJsonData(json.getJsonAdvertising().toString());
                    }
                    listDataChild.put(device.getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(device.getAddress(), "Disconnected");
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private synchronized void updateUIConnected(final BluetoothGatt gatt) {
        Log.d("Fragment", "fragment thread accessing UI = " + Thread.currentThread().getName());
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    json.setJsonData(json.getJsonData().toString());
                    listDataChild.put(gatt.getDevice().getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Connected");
                    listAdapter.setConnectionListener(true, gatt.getDevice().getAddress());
                    listAdapter.setTextAppearanceHeader("Large");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private synchronized void updateUIConnected(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        Log.d("Fragment", "fragment thread accessing UI = " + Thread.currentThread().getName());
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    json.setJsonData(json.getJsonData().toString());
                    json.updateJsonData(json.getJsonData(), characteristic);
                    listDataChild.put(gatt.getDevice().getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Connected");
                    listAdapter.setConnectionListener(true, gatt.getDevice().getAddress());
                    listAdapter.setTextAppearanceHeader("Large");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private synchronized void updateUIDisonnected(final BluetoothGatt gatt) {
        Log.d("Fragment", "fragment thread accessing UI = " + Thread.currentThread().getName());
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Disconnected");
                    listAdapter.setTextAppearanceHeader("Medium");
                    listAdapter.setConnectionListener(false, gatt.getDevice().getAddress());
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
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



    public static void deleteBondInformation(BluetoothDevice device)
    {
        try
        {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
