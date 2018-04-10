package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.helper.ExpandableListAdapter;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattLookUp;
import com.uni.stuttgart.ipvs.androidgateway.helper.ImageViewClickListener;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothLeScannerActivity extends AppCompatActivity implements ImageViewClickListener {

    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int SCAN_PERIOD = 10;
    private static final String TAG = "Bluetooth Activity";

    private PowerManager.WakeLock wakeLock;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private List<BluetoothDevice> listDevice;
    private BluetoothGatt mBluetoothGatt;
    private List<BluetoothGatt> listConnectedGatt;
    private int counter;
    private boolean mProcessing; // flag to track processing
    private boolean mScanning; // flag to track scanning process
    private boolean mConnecting;
    private boolean isCOnnectAllDevices; // flag to track if connection is done to all nearby devices

    private Context context;
    private ExpandableListView listView;
    private ExpandableListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, String> listDataHeaderSmall;
    private HashMap<String, List<String>> listDataChild;

    private ConcurrentLinkedQueue<BluetoothLeGatt> queue;
    private Menu menuBar;

    private HandlerThread mThread = new HandlerThread("mThreadCallback");
    private Handler mHandlerMessage;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
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
            case R.id.action_scan:
                scanLeDevice();
                break;
            case R.id.action_stop:
                stopLeDevice();
                break;
            case R.id.action_connect:
                connectLeDevice();
            case R.id.action_disconnect:
                disconnectLeDevice();
                break;
        }
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //set of List view command
        listView = (ExpandableListView) findViewById(R.id.listViewBle);

        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<>();
        listDataHeaderSmall = new HashMap<>();
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
        listAdapter.setDataHeaderSmall(listDataHeaderSmall);
        listAdapter.setImageClickListener(this);
        listView.setAdapter(listAdapter);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);

        context = this;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new
                BluetoothLeScanProcess(this, mBluetoothAdapter);
        mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
        listDevice = new ArrayList<>();
        listConnectedGatt = new ArrayList<>();

        mScanning = false;
        mProcessing = false;
        mConnecting = false;
        isCOnnectAllDevices = false;

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
        if (listConnectedGatt.size() > 0) {
            for (BluetoothGatt gatt : listConnectedGatt) {
                BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(gatt);
                gattCallback.disconnect();
            }
        }
        wakeLock.release();
        mProcessing = false;
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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

    @Override
    public void imageViewListClicked(View v) {
        String macAddress = (String) v.getTag();
        Toast.makeText(context, "Connecting to " + macAddress, Toast.LENGTH_SHORT).show();
        connectLeDevice(macAddress);
    }

    private void scanLeDevice() {
        if (mBluetoothLeScanProcess != null) {
            mProcessing = true;
            counter = 0;
            mBluetoothLeScanProcess.scanLeDevice(true);
            mScanning = true;
            setMenuVisibility();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopLeDevice();
                }
            }, SCAN_PERIOD * 1000);
        }
    }

    private void stopLeDevice() {
        if (mBluetoothLeScanProcess != null) {
            mBluetoothLeScanProcess.scanLeDevice(false);
            mScanning = false;
            setMenuVisibility();
        }
    }

    private void connectLeDevice() {
        mProcessing = true;
        mConnecting = true;
        isCOnnectAllDevices = true;
        setMenuVisibility();

        for (final BluetoothDevice device : listDevice) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                    gattCallback.setHandlerMessage(mHandlerMessage);
                    gattCallback.connect();
                }
            }).start();
        }
    }

    private void connectLeDevice(final String macAddress) {
        mProcessing = true;
        mConnecting = true;
        isCOnnectAllDevices = false;
        setMenuVisibility();

        for (final BluetoothDevice device : listDevice) {
            if (macAddress.equals(device.getAddress())) {
                BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context, device);
                gattCallback.setHandlerMessage(mHandlerMessage);
                gattCallback.connect();
            }
        }
    }

    private void disconnectLeDevice() {
        mProcessing = false;
        mConnecting = false;
        setMenuVisibility();

        for(BluetoothGatt gatt : listConnectedGatt) {
            gatt.disconnect();
            gatt.close();
        }
    }

    /**
     * Handling callback messages
     */
    Handler.Callback mHandlerCallback = new Handler.Callback() {

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
                            queueCommand(gatt, null, null, null, BluetoothLeDevice.CONNECTED);
                            processBle = true;
                        }
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        final BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                        if(gatt != null) {
                            mBluetoothGatt = gatt;
                            queueCommand(gatt, null, null, null, BluetoothLeDevice.DISCONNECTED);
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
                        queueUI(gatt);
                        queueCommand(gatt, null, null, null, BluetoothLeDevice.STOP_SEQUENCE);
                        processBle = true;
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        queueReadWriteGatt(mBluetoothGatt, null);
                        queueUI(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        queueReadWriteGatt(mBluetoothGatt, null);
                        queueUI(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        queueReadWriteGatt(mBluetoothGatt, null);
                        queueUI(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 8) {
                        //onDescriptorRead

                    } else if (msg.arg1 == 9) {
                        //onDescriptorWrite

                    }

                    // signalling a device or all devices have been connected
                    if (isCOnnectAllDevices && listDevice != null && counter == listDevice.size()) {
                        postConnected();
                    } else if (!isCOnnectAllDevices && counter == 1) {
                        postConnected();
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

    private void postConnected() {
        mConnecting = false;
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mProcessing = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMenuVisibility();
                    }
                });
            }
        }, SCAN_PERIOD * 1000);

    }

    private synchronized void queueCommand(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data, int typeCommand) {
        BluetoothLeGatt ble = new BluetoothLeGatt(gatt, serviceUUID, characteristicUUID, data, typeCommand);
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
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.READ);
                            queue.add(ble);
                        } else if (property.equals("Write")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.WRITE);
                            queue.add(ble);
                        } else if (property.equals("Notify")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.REGISTER_NOTIFY);
                            queue.add(ble);
                        } else if (property.equals("Indicate")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.REGISTER_INDICATE);
                            queue.add(ble);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    private synchronized void queueReadWriteGatt(BluetoothGatt gatt, byte[] data) {
        BluetoothLeGatt ble = new BluetoothLeGatt();

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLeGatt.READ);
                            queue.add(ble);
                        } else if (property.equals("Write")) {
                            ble = new BluetoothLeGatt(gatt, service.getUuid(), characteristic.getUuid(), data, BluetoothLeGatt.WRITE);
                            queue.add(ble);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private synchronized void queueUI(BluetoothGatt gatt) {
        BluetoothLeGatt ble = new BluetoothLeGatt(gatt, null, null, null, BluetoothLeDevice.UPDATE_UI_CONNECTED);
        queue.add(ble);
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
                            counter++;
                        } else if (bleQueue.getTypeCommand() == BluetoothLeDevice.DISCONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            updateUIDisonnected(gatt);
                            counter++;
                            Toast.makeText(context, "Disconnected from " + gatt.getDevice().getAddress(), Toast.LENGTH_SHORT).show();
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.READ) {
                            sleepThread(100);
                            gattCallback.readCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID());
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.REGISTER_NOTIFY) {
                            sleepThread(100);
                            gattCallback.writeDescriptorNotify(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.REGISTER_INDICATE) {
                            sleepThread(100);
                            gattCallback.writeDescriptorIndication(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLeGatt.WRITE) {
                            sleepThread(100);
                            //gattCallback.writeCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), bleQueue.getData());
                        } else if(bleQueue.getTypeCommand() == BluetoothLeDevice.UPDATE_UI_CONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            updateUIConnected(gatt);
                        } else if(bleQueue.getTypeCommand() == BluetoothLeDevice.STOP_SEQUENCE) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            mProcessing = false;
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (gatt != null) {
                                        updateUIDisonnected(gatt);
                                        gatt.disconnect();
                                        gatt.close();
                                    }
                                }
                            }, SCAN_PERIOD * 10000);
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
            runOnUiThread(new Runnable() {
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
            runOnUiThread(new Runnable() {
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
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    json.setJsonData(json.getJsonData().toString());
                    listDataChild.put(gatt.getDevice().getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Connected");
                    listAdapter.setChildDataWriteable(true, findPositionWriteable(json.getPreparedChildData()));
                    listAdapter.setTextAppearanceHeader("Large");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private synchronized void updateUIRssiUpdate(final BluetoothGatt gatt, int rssi) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt, rssi);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    json.setJsonData(json.getJsonData().toString());
                    listDataChild.put(gatt.getDevice().getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Connected");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private synchronized void updateUIDisonnected(final BluetoothGatt gatt) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Disconnected");
                    listAdapter.setTextAppearanceHeader("Medium");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private int findPositionWriteable(List<String> inputs) {
        int counter = 0;
        for (String input : inputs) {
            if (input.contains("Write")) {
                return counter;
            }
            counter++;
        }

        return 0;
    }

    private void setMenuVisibility() {
        if (!mScanning) {
            // when scanning is stop
            if(listDataHeader != null && listDataHeader.size() != 0) {
                if(mConnecting) {
                    menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
                    menuBar.findItem(R.id.action_scan).setVisible(false);
                    menuBar.findItem(R.id.action_stop).setVisible(false);
                    menuBar.findItem(R.id.action_connect).setVisible(false);
                    menuBar.findItem(R.id.action_disconnect).setVisible(true);
                } else {
                    menuBar.findItem(R.id.menu_refresh).setActionView(null);
                    menuBar.findItem(R.id.action_scan).setVisible(false);
                    menuBar.findItem(R.id.action_stop).setVisible(false);
                    menuBar.findItem(R.id.action_connect).setVisible(true);
                    menuBar.findItem(R.id.action_disconnect).setVisible(false);
                }
            } else {
                menuBar.findItem(R.id.menu_refresh).setActionView(null);
                menuBar.findItem(R.id.action_scan).setVisible(true);
                menuBar.findItem(R.id.action_stop).setVisible(false);
                menuBar.findItem(R.id.action_connect).setVisible(false);
                menuBar.findItem(R.id.action_disconnect).setVisible(false);
            }
        } else {
            // when scanning is running
            menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
            menuBar.findItem(R.id.action_scan).setVisible(false);
            menuBar.findItem(R.id.action_stop).setVisible(true);
            menuBar.findItem(R.id.action_connect).setVisible(false);
            menuBar.findItem(R.id.action_disconnect).setVisible(false);
        }
    }

    private void sleepThread(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
