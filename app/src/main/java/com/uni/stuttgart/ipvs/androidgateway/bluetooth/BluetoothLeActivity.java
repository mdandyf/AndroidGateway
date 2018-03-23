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
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.R;
import com.uni.stuttgart.ipvs.androidgateway.database.BleDeviceDatabase;
import com.uni.stuttgart.ipvs.androidgateway.helper.ExpandableListAdapter;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattLookUp;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
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
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanProcess mBluetoothLeScanProcess;
    private List<BluetoothDevice> listDevice;
    private BluetoothGatt mBluetoothGatt;
    private int mGattRssi;
    private List<BluetoothGatt> listConnectedGatt;
    private int counter;
    private boolean mProcessing;

    private Context context;
    private ExpandableListView listView;
    private ExpandableListAdapter listAdapter;
    private List<String> listDataHeader;
    private HashMap<String, String> listDataHeaderSmall;
    private HashMap<String, List<String>> listDataChild;

    private BleDeviceDatabase bleDeviceDatabase;
    private ConcurrentLinkedQueue<BluetoothLe> queue;
    private Menu menuBar;
    private int callbackCounter;

    private HandlerThread mThread = new HandlerThread("mThreadCallback");
    private Handler mHandlerMessage;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
        menuBar = menu;
        menuBar.findItem(R.id.menu_refresh).setActionView(null);
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
            case R.id.action_connect:
                if (!mBluetoothLeScanProcess.getScanState()) {
                    connectLeDevice();
                } else {
                    Toast.makeText(this, "Scanning is running, please wait!", Toast.LENGTH_SHORT).show();
                }
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
        listView.setAdapter(listAdapter);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        mThread.start();
        mHandlerMessage = new Handler(mThread.getLooper(), mHandlerCallback);

        context = this;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanProcess = new BluetoothLeScanProcess(this, mBluetoothAdapter);
        mBluetoothLeScanProcess.setHandlerMessage(mHandlerMessage);
        listDevice = new ArrayList<>();
        listConnectedGatt = new ArrayList<>();

        bleDeviceDatabase = new BleDeviceDatabase(this);
        bleDeviceDatabase.deleteAllData();

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

    private void scanLeDevice() {
        mProcessing = true;
        counter = 0;
        mBluetoothLeScanProcess.scanLeDevice(true);
        menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothLeScanProcess.scanLeDevice(false);
                menuBar.findItem(R.id.menu_refresh).setActionView(null);
                mProcessing = false;
            }
        }, SCAN_PERIOD * 1000);
    }

    private void connectLeDevice() {
        mProcessing = true;
        menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
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
        menuBar.findItem(R.id.menu_refresh).setActionView(R.layout.action_interdeminate_progress);
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(context);
                gattCallback.setHandlerMessage(mHandlerMessage);
                gattCallback.connect(mBluetoothAdapter, macAddress);
            }
        }).start();
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
                        }
                    } else if (msg.arg1 == 4) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            List<ScanResult> results = ((List<ScanResult>) msg.obj);
                            for (ScanResult result : results) {
                                updateUIScan(result);
                            }
                        }
                    } else if (msg.arg1 == 5) {
                        final Map<BluetoothDevice, GattDataJson> mapDevice = ((Map<BluetoothDevice, GattDataJson>) msg.obj);
                        updateUIScan(mapDevice);
                    }

                } else if (msg.what == 1) {
                    //getting results from BluetoothLeGattCallback for connecting
                    if (msg.arg1 == 0) {
                        // read all bluetoothGatt servers
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                    } else if (msg.arg1 == 1) {
                        // read all bluetoothGatt connected servers
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        synchronized (mBluetoothGatt) {
                            mBluetoothGatt.readRemoteRssi();
                            mBluetoothGatt.discoverServices();
                            listConnectedGatt.add(mBluetoothGatt);
                            updateUIConnected(mBluetoothGatt);
                            counter++;
                        }
                    } else if (msg.arg1 == 2) {
                        // read all bluetoothGatt disconnected servers
                        synchronized (mBluetoothGatt) {
                            mBluetoothGatt = ((BluetoothGatt) msg.obj);
                            mBluetoothGatt.readRemoteRssi();
                            updateUIDisonnected(mBluetoothGatt);
                            counter++;
                        }
                    } else if (msg.arg1 == 3) {
                        // read all bluetoothGatt rssi
                        synchronized (mBluetoothGatt) {
                            mGattRssi = ((Integer) msg.obj);
                            updateUIRssiUpdate(mBluetoothGatt, mGattRssi);
                        }
                    } else if (msg.arg1 == 4) {
                        //discovered services and read all characteristics
                        synchronized (mBluetoothGatt) {
                            mBluetoothGatt = ((BluetoothGatt) msg.obj);
                            queueSubscribeOrReadGatt(mBluetoothGatt);
                            mBluetoothGatt.readRemoteRssi();
                            updateUIConnected(mBluetoothGatt);
                            processBle = true;
                        }
                    } else if (msg.arg1 == 5) {
                        // onReadCharacteristic
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        mBluetoothGatt.readRemoteRssi();
                        updateUIConnected(mBluetoothGatt);
                        queueReadGatt(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 6) {
                        // onWriteCharacteristic
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        mBluetoothGatt.readRemoteRssi();
                        updateUIConnected(mBluetoothGatt);
                        queueReadGatt(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 7) {
                        //onCharacteristicChanged
                        mBluetoothGatt = ((BluetoothGatt) msg.obj);
                        mBluetoothGatt.readRemoteRssi();
                        updateUIConnected(mBluetoothGatt);
                        queueReadGatt(mBluetoothGatt);
                        processBle = true;
                    } else if (msg.arg1 == 8) {
                        //onDescriptorRead
                        synchronized (mBluetoothGatt) {
                            mBluetoothGatt.readRemoteRssi();
                            queueReadGatt(mBluetoothGatt);
                            updateUIConnected(mBluetoothGatt);
                        }
                    } else if (msg.arg1 == 9) {
                        //onDescriptorWrite
                        synchronized (mBluetoothGatt) {
                            mBluetoothGatt.readRemoteRssi();
                            queueReadGatt(mBluetoothGatt);
                            updateUIConnected(mBluetoothGatt);
                        }
                    }

                    if (listDevice != null && counter == listDevice.size()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                menuBar.findItem(R.id.menu_refresh).setActionView(null);
                            }
                        });
                    }

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

    private void queueSubscribeOrReadGatt(BluetoothGatt gatt) {
        queue = new ConcurrentLinkedQueue<>();
        BluetoothLe ble = new BluetoothLe();

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.READ);
                            queue.add(ble);
                        } else if (property.equals("Write")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.WRITE);
                            queue.add(ble);
                        } else if (property.equals("Notify")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_NOTIFY);
                            queue.add(ble);
                        } else if (property.equals("Indicate")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.REGISTER_INDICATE);
                            queue.add(ble);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    private void queueReadGatt(BluetoothGatt gatt) {
        queue = new ConcurrentLinkedQueue<>();
        BluetoothLe ble = new BluetoothLe();

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONArray properties = GattDataHelper.decodeProperties(characteristic);
                for (int i = 0; i < properties.length(); i++) {
                    try {
                        String property = properties.getString(i);
                        if (property.equals("Read")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.READ);
                            queue.add(ble);
                        } else if (property.equals("Write")) {
                            ble = new BluetoothLe(gatt, service.getUuid(), characteristic.getUuid(), null, BluetoothLe.WRITE);
                            queue.add(ble);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    private void processQueue() {
        if (!queue.isEmpty()) {
            for (BluetoothLe bleQueue = queue.poll(); bleQueue != null; bleQueue = queue.poll()) {
                if (bleQueue != null) {
                    synchronized (bleQueue) {
                        BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(bleQueue.getGatt());
                        if (bleQueue.getTypeCommand() == BluetoothLe.READ) {
                            sleepThread(100);
                            gattCallback.readCharacteristic(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID());
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.REGISTER_NOTIFY) {
                            sleepThread(100);
                            gattCallback.writeDescriptorNotify(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.REGISTER_INDICATE) {
                            sleepThread(100);
                            gattCallback.writeDescriptorIndication(bleQueue.getServiceUUID(), bleQueue.getCharacteristicUUID(), GattLookUp.shortUUID("2902"));
                        } else if (bleQueue.getTypeCommand() == BluetoothLe.WRITE) {
                            sleepThread(100);
                            //mService.writeCharacteristic();
                        }


                    }
                } else {
                    Log.d(TAG, "Command Queue is empty.");
                }
            }

        }
    }

    private void updateUIScan(final ScanResult result) {
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

    private void updateUIScan(Map<BluetoothDevice, GattDataJson> map) {
        for (Map.Entry entry : map.entrySet()) {
            final BluetoothDevice device = (BluetoothDevice) entry.getKey();
            if (!listDevice.contains(device)) {
                listDevice.add(device);
            }
            final GattDataJson json = (GattDataJson) entry.getValue();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
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

    private void updateUIConnected(final BluetoothGatt gatt) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
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

    private void updateUIRssiUpdate(final BluetoothGatt gatt, int rssi) {
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

    private void updateUIDisonnected(final BluetoothGatt gatt) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listDataHeader.contains(gatt.getDevice().getAddress())) {
                    json.setJsonData(json.getJsonAdvertising().toString());
                    listDataChild.put(gatt.getDevice().getAddress(), json.getPreparedChildData());
                    listDataHeaderSmall.put(gatt.getDevice().getAddress(), "Disonnected");
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void sleepThread(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
