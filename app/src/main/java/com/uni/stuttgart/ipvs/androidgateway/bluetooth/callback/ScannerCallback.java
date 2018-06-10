package com.uni.stuttgart.ipvs.androidgateway.bluetooth.callback;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeDevice;
import com.uni.stuttgart.ipvs.androidgateway.bluetooth.peripheral.BluetoothLeGatt;
import com.uni.stuttgart.ipvs.androidgateway.gateway.IGatewayService;
import com.uni.stuttgart.ipvs.androidgateway.gateway.PBluetoothGatt;
import com.uni.stuttgart.ipvs.androidgateway.helper.BroadcastReceiverHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataHelper;
import com.uni.stuttgart.ipvs.androidgateway.helper.GattDataJson;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ScannerCallback implements Handler.Callback {
    private final static String TAG = "ScannerCallback";
    private final static int WHAT_SCAN_CALLBACK = 0;
    private final static int WHAT_GATT_CALLBACK = 1;

    private Context context;
    private boolean mProcessing;
    private IGatewayService.Stub mBinder;
    private ConcurrentLinkedQueue<BluetoothLeGatt> queue;
    private BroadcastReceiverHelper mReceiver = new BroadcastReceiverHelper();

    private List<BluetoothDevice> listDevice = new ArrayList<>();
    private List<BluetoothGattCharacteristic> listCharBondRead = new ArrayList<>();

    private BluetoothGatt mBluetoothGatt;
    private boolean processBle = false;

    public ScannerCallback(Context context, boolean mProcessing, IGatewayService.Stub mBinder) {
        this.context = context;
        this.mProcessing = mProcessing;
        this.mBinder = mBinder;
    }

    @Override
    public boolean handleMessage(Message msg) {

        try {
            if (msg.what == WHAT_SCAN_CALLBACK) {
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

            } else if (msg.what == WHAT_GATT_CALLBACK) {
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
                        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                        queueCommand(mBluetoothGatt, characteristic.getService().getUuid(), characteristic.getUuid(), null, null, BluetoothLeDevice.UPDATE_UI_CONNECTED);
                    }
                    processBle = true;
                } else if (msg.arg1 == 6) {
                    // onWriteCharacteristic
                    Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                    for(Map.Entry entry : mapGatt.entrySet()) {
                        mBluetoothGatt = (BluetoothGatt) entry.getKey();
                        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                        queueCommand(mBluetoothGatt, characteristic.getService().getUuid(), characteristic.getUuid(), null, null, BluetoothLeDevice.UPDATE_UI_CONNECTED);
                    }
                    processBle = true;
                } else if (msg.arg1 == 7) {
                    //onCharacteristicChanged
                    Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                    for(Map.Entry entry : mapGatt.entrySet()) {
                        mBluetoothGatt = (BluetoothGatt) entry.getKey();
                        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                        queueCommand(mBluetoothGatt, characteristic.getService().getUuid(), characteristic.getUuid(), null, null, BluetoothLeDevice.UPDATE_UI_CONNECTED);
                    }
                    processBle = true;
                } else if (msg.arg1 == 8) {
                    //onDescriptorRead
                    processBle = false;
                } else if (msg.arg1 == 9) {
                    //onDescriptorWrite
                    processBle = false;
                } else if(msg.arg1 == 10) {
                    Map<BluetoothGatt, BluetoothGattCharacteristic> mapGatt = ((Map<BluetoothGatt, BluetoothGattCharacteristic>) msg.obj);
                    for(Map.Entry entry : mapGatt.entrySet()) {
                        mBluetoothGatt = (BluetoothGatt) entry.getKey();
                        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) entry.getValue();
                        if(!listCharBondRead.contains(characteristic)) {listCharBondRead.add(characteristic);}
                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        context.registerReceiver(mReceiver, filter);
                        Thread.sleep(1000);
                        context.unregisterReceiver(mReceiver);
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

    private void queueCommand(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID, byte[] data, int typeCommand) {
        BluetoothLeGatt ble = new BluetoothLeGatt(gatt, serviceUUID, characteristicUUID, descriptorUUID, data, typeCommand);
        queue.add(ble);
    }

    private void queueSubscribeOrReadGatt(BluetoothGatt gatt) {
        BluetoothLeGatt ble;

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

    private void processQueue() {
        if (!queue.isEmpty()) {
            for (BluetoothLeGatt bleQueue = queue.poll(); bleQueue != null; bleQueue = queue.poll()) {
                if (bleQueue != null) {
                    synchronized (bleQueue) {
                        BluetoothLeGattCallback gattCallback = new BluetoothLeGattCallback(bleQueue.getGatt());
                        if (bleQueue.getTypeCommand() == BluetoothLeDevice.CONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                            parcelBluetoothGatt.setGatt(gatt);
                            try {
                                mBinder.doConnected(parcelBluetoothGatt);
                                updateUIConnected(parcelBluetoothGatt.getGatt());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        } else if (bleQueue.getTypeCommand() == BluetoothLeDevice.DISCONNECTED) {
                            final BluetoothGatt gatt = bleQueue.getGatt();
                            PBluetoothGatt parcelBluetoothGatt = new PBluetoothGatt();
                            parcelBluetoothGatt.setGatt(gatt);
                            try {
                                mBinder.doDisconnected(parcelBluetoothGatt, "ScannerCallback");
                                updateUIDisonnected(parcelBluetoothGatt.getGatt());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
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
                            final BluetoothGattCharacteristic characteristic = gatt.getService(bleQueue.getServiceUUID()).getCharacteristic(bleQueue.getCharacteristicUUID());
                            updateUIConnected(gatt, characteristic);
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
            GattDataJson json = new GattDataJson(device, rssi, null);
            json.setJsonData(json.getJsonAdvertising().toString());

            List<String> jsonData = json.getPreparedChildData();
            String[] dataResult = new String[jsonData.size()];
            int i = 0;
            for(String data : jsonData) {
                dataResult[i] = data;
                i++;
            }

            broadcastResult(BluetoothLeDevice.UI_SCAN, device, dataResult);
        }

    }

    private void updateUIScan(Map<BluetoothDevice, GattDataJson> map) {
        for (final Map.Entry entry : map.entrySet()) {
            final BluetoothDevice device = (BluetoothDevice) entry.getKey();
            if (!listDevice.contains(device)) {
                listDevice.add(device);
            }
            GattDataJson json = (GattDataJson) entry.getValue();
            json.setJsonData(json.getJsonAdvertising().toString());


            List<String> jsonData = json.getPreparedChildData();
            String[] dataResult = new String[jsonData.size()];
            int i = 0;
            for(String data : jsonData) {
                dataResult[i] = data;
                i++;
            }

            broadcastResult(BluetoothLeDevice.UI_SCAN, device, dataResult);
        }
    }

    private void updateUIConnected(final BluetoothGatt gatt) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        json.setJsonData(json.getJsonData().toString());

        List<String> jsonData = json.getPreparedChildData();
        String[] dataResult = new String[jsonData.size()];
        int i = 0;
        for(String data : jsonData) {
            dataResult[i] = data;
            i++;
        }

        broadcastResult(BluetoothLeDevice.UI_CONNECTED, gatt, dataResult);
    }

    private void updateUIConnected(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        json.setJsonData(json.getJsonData().toString());
        json.updateJsonData(json.getJsonData(), characteristic);

        List<String> jsonData = json.getPreparedChildData();
        String[] dataResult = new String[jsonData.size()];
        int i = 0;
        for(String data : jsonData) {
            dataResult[i] = data;
            i++;
        }

        broadcastResult(BluetoothLeDevice.UI_CONNECTED, gatt, dataResult);
    }

    private void updateUIDisonnected(final BluetoothGatt gatt) {
        final GattDataJson json = new GattDataJson(gatt.getDevice(), gatt);
        json.setJsonData(json.getJsonData().toString());

        List<String> jsonData = json.getPreparedChildData();
        String[] dataResult = new String[jsonData.size()];
        int i = 0;
        for(String data : jsonData) {
            dataResult[i] = data;
            i++;
        }

        broadcastResult(BluetoothLeDevice.UI_DISCONNECTED, gatt, dataResult);
    }

    private void broadcastResult(String action, BluetoothDevice device, String[] dataResult){
        Intent intent = new Intent(action);
        intent.putExtra("DeviceAddress", device.getAddress());
        intent.putExtra("DeviceData", dataResult);
        context.sendBroadcast(intent);
    }

    private void broadcastResult(String action, BluetoothGatt gatt, String[] dataResult){
        Intent intent = new Intent(action);
        intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
        intent.putExtra("DeviceData", dataResult);
        context.sendBroadcast(intent);
    }
}
