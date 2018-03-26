package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mdand on 2/24/2018.
 */

public class GattDataJson extends JsonParser {

    private String jsonData;
    private JSONObject jsonObject;

    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private int rssi;
    private byte[] advertisingData;
    private int txPowerData;

    public GattDataJson(BluetoothDevice device, BluetoothGatt gatt) {
        this.device = device;
        this.gatt = gatt;
    }

    public GattDataJson(BluetoothDevice device, BluetoothGatt gatt, int rssi) {
        this.device = device;
        this.gatt = gatt;
        this.rssi = rssi;
    }

    public GattDataJson(BluetoothDevice device, int rssi, byte[] advertisingData) {
        this.device = device;
        this.rssi = rssi;
        this.advertisingData = advertisingData;
    }

    public GattDataJson(BluetoothDevice device, int rssi, int txPowerData) {
        this.device = device;
        this.rssi = rssi;
        this.txPowerData = txPowerData;
    }

    public void setGatt(BluetoothGatt gatt) {this.gatt = gatt;}

    public void setRssi(int rssi) {this.rssi = rssi;}

    public void setAdvertisingData(byte[] advertisingData) {this.advertisingData = advertisingData;}

    public void setTxPowerData(int txPowerData) {this.txPowerData = txPowerData;}

    public void setJsonData(String jsonData) {this.jsonData = jsonData;}

    public GattDataJson(BluetoothDevice device, int rssi, byte[] advertisingData, BluetoothGatt gatt) {
        this.device = device;
        this.rssi = rssi;
        this.advertisingData = advertisingData;
        this.gatt = gatt;
    }

    public JSONObject getJsonAdvertising() {

        JSONObject json = new JSONObject();

        try {
            String name = (device.getName() != null) ? device.getName() : "Unknown";
            json.put("name", name);
            json.put("id", device.getAddress()); // mac address
            json.put("rssi", rssi);
            if (advertisingData != null) {
                json.put("advertising", byteArrayToJSON(advertisingData));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                json.put("tx Power", txPowerData);
            }
            json.put("status", "disconnected");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json;
    }

    public JSONObject getJsonData() {
        jsonObject = getJsonAdvertising();

        try {
            JSONArray servicesArray = new JSONArray();
            JSONArray characteristicsArray = new JSONArray();
            jsonObject.put("services", servicesArray);
            jsonObject.put("characteristics", characteristicsArray);

            if (gatt != null) {
                jsonObject.remove("status");
                jsonObject.put("status", "connected");
                for (BluetoothGattService service : gatt.getServices()) {
                    servicesArray.put(uuidToString(service.getUuid()));

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        JSONObject characteristicsJSON = new JSONObject();
                        characteristicsArray.put(characteristicsJSON);

                        characteristicsJSON.put("serviceName", GattLookUp.serviceNameLookup(service.getUuid()));
                        characteristicsJSON.put("serviceUUID", uuidToString(service.getUuid()));
                        characteristicsJSON.put("characteristicName", GattLookUp.characteristicNameLookup(characteristic.getUuid()));
                        characteristicsJSON.put("characteristicUUID", uuidToString(characteristic.getUuid()));
                        characteristicsJSON.put("characteristicValue", GattDataHelper.decodeCharacteristicValue(characteristic, gatt));

                        characteristicsJSON.put("properties", GattDataHelper.decodeProperties(characteristic));
                        characteristicsJSON.put("propertiesValue", characteristic.getProperties());

                        if (characteristic.getPermissions() > 0) {
                            characteristicsJSON.put("permissions", GattDataHelper.decodePermissions(characteristic));
                        }

                        JSONArray descriptorsArray = new JSONArray();

                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            JSONObject descriptorJSON = new JSONObject();
                            descriptorJSON.put("descriptorName", GattLookUp.descriptorNameLookup(descriptor.getUuid()));
                            descriptorJSON.put("descriptorUuid", uuidToString(descriptor.getUuid()));
                            descriptorJSON.put("descriptorValue", descriptor.getValue());

                            if (descriptor.getPermissions() > 0) {
                                descriptorJSON.put("permissions", GattDataHelper.decodePermissions(descriptor));
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

        return jsonObject;
    }

    public List<String> getPreparedChildData() {
        List<String> result = new ArrayList<>();
        if (jsonData != null) {
            JSONObject json = readJsonObjectFromString(jsonData);
            try {
                result.add("Name: " + json.getString("name"));
                result.add("Rssi: " + String.valueOf(json.getInt("rssi")) + " dBm");
                if (advertisingData != null) {
                    result.add("Advertising: " + json.getString("data"));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.add("tx Power :" + String.valueOf(json.getInt("tx power")) + " dBm");
                }

                if (!json.isNull("services")) {
                    JSONArray services = json.getJSONArray("services");
                    result.add("Services: " + services.toString());
                    if (!json.isNull("characteristics")) {
                        JSONArray characteristics = json.getJSONArray("characteristics");
                        for (int i = 0; i < characteristics.length(); i++) {
                            JSONObject obj = characteristics.getJSONObject(i);
                            result.add(" Service Name: " + obj.get("serviceName"));
                            result.add(" Service UUID: " + obj.get("serviceUUID"));
                            result.add("    Characteristic Name: " + obj.get("characteristicName"));
                            result.add("    Characteristic UUID: " + obj.get("characteristicUUID"));
                            result.add("    Characteristic Property: " + obj.get("properties"));
                            if(obj.has("characteristicValue")) {
                                result.add("    Characteristic Value: " + obj.get("characteristicValue"));
                            }
                            if (!json.isNull("descriptors")) {
                                JSONArray descriptors = json.getJSONArray("descriptors");
                                for (int j = 0; j < descriptors.length(); j++) {
                                    JSONObject objDesc = characteristics.getJSONObject(i);
                                    result.add("       Descriptor Name: " + objDesc.get("descriptorName"));
                                    result.add("       Descriptor UUID: " + objDesc.get("descriptorUUID"));
                                    result.add("       Descriptor Value: " + objDesc.get("descriptorValue"));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return result;
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
