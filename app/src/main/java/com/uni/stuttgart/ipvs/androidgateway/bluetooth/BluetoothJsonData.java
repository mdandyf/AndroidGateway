package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.Characteristics;
import android.support.v7.widget.ThemedSpinnerAdapter;
import android.util.Base64;

import com.uni.stuttgart.ipvs.androidgateway.helper.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mdand on 2/24/2018.
 */

public class BluetoothJsonData extends JsonParser {

    private String jsonData;
    private JSONObject jsonObject;

    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private int rssi;
    private byte[] advertisingData;

    public BluetoothJsonData(BluetoothDevice device, BluetoothGatt gatt) {
        this.device = device;
        this.gatt = gatt;
    }

    public JSONObject getJsonAdvertising()  {

        JSONObject json = new JSONObject();

        try {
            json.put("name", device.getName());
            json.put("id", device.getAddress()); // mac address
            json.put("advertising", byteArrayToJSON(advertisingData));
            json.put("rssi", rssi);
        } catch (JSONException e) { // this shouldn't happen
            e.printStackTrace();
        }

        return json;
    }

    public void setJsonData() {
        jsonObject = getJsonAdvertising();

        try {
            JSONArray servicesArray = new JSONArray();
            JSONArray characteristicsArray = new JSONArray();
            jsonObject.put("services", servicesArray);
            jsonObject.put("characteristics", characteristicsArray);

            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    servicesArray.put(uuidToString(service.getUuid()));

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        JSONObject characteristicsJSON = new JSONObject();
                        characteristicsArray.put(characteristicsJSON);

                        characteristicsJSON.put("service", uuidToString(service.getUuid()));
                        characteristicsJSON.put("characteristic", uuidToString(characteristic.getUuid()));
                        //characteristicsJSON.put("instanceId", characteristic.getInstanceId());

                        characteristicsJSON.put("properties", BluetoothGattHelper.decodeProperties(characteristic));
                        // characteristicsJSON.put("propertiesValue", characteristic.getProperties());

                        if (characteristic.getPermissions() > 0) {
                            characteristicsJSON.put("permissions", BluetoothGattHelper.decodePermissions(characteristic));
                            // characteristicsJSON.put("permissionsValue", characteristic.getPermissions());
                        }

                        JSONArray descriptorsArray = new JSONArray();

                        for (BluetoothGattDescriptor descriptor: characteristic.getDescriptors()) {
                            JSONObject descriptorJSON = new JSONObject();
                            descriptorJSON.put("uuid", uuidToString(descriptor.getUuid()));
                            descriptorJSON.put("value", descriptor.getValue()); // always blank

                            if (descriptor.getPermissions() > 0) {
                                descriptorJSON.put("permissions", BluetoothGattHelper.decodePermissions(descriptor));
                                // descriptorJSON.put("permissionsValue", descriptor.getPermissions());
                            }
                            descriptorsArray.put(descriptorJSON);
                        }
                        if (descriptorsArray.length() > 0) {
                            characteristicsJSON.put("descriptors", descriptorsArray);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public JSONObject getJsonData() {
        return this.jsonObject;
    }

    public String getJsonDataString() {
        return this.jsonData;
    }

    private static JSONObject byteArrayToJSON(byte[] bytes) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("CDVType", "ArrayBuffer");
        object.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        return object;
    }

    // return 16 bit UUIDs where possible
    private static String uuidToString(UUID uuid) {
        String longUUID = uuid.toString();
        Pattern pattern = Pattern.compile("0000(.{4})-0000-1000-8000-00805f9b34fb", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(longUUID);
        if (matcher.matches()) {
            // 16 bit UUID
            return matcher.group(1);
        } else {
            return longUUID;
        }
    }

}
