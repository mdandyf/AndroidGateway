package com.uni.stuttgart.ipvs.androidgateway.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mdand on 2/26/2018.
 */

public class BluetoothGattHelper {

    private static final String CHARACTERISTIC_TEMPERATURE = "2a1c";
    private static final String CHARACTERISTIC_HUMIDITY = "2a6f";
    public final static String CHARACTERISTIC_HEART_RATE = "2a37";
    public final static String CHARACTERISTIC_BODY_SENSOR_LOCATION = "2a38";
    public final static String CLIENT_CHARACTERISTIC_CONFIGURATION = "2902";

    public static JSONArray decodeProperties(BluetoothGattCharacteristic characteristic) {

        // NOTE: props strings need to be consistent across iOS and Android
        JSONArray props = new JSONArray();
        int properties = characteristic.getProperties();

        if ((properties & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0x0) {
            props.put("Broadcast");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0x0) {
            props.put("Read");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0x0) {
            props.put("WriteWithoutResponse");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0x0) {
            props.put("Write");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x0) {
            props.put("Notify");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0x0) {
            props.put("Indicate");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0x0) {
            // Android calls this "write with signature", using iOS name for now
            props.put("AuthenticateSignedWrites");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0x0) {
            props.put("ExtendedProperties");
        }

        return props;
    }

    public static JSONArray decodePermissions(BluetoothGattCharacteristic characteristic) {

        // NOTE: props strings need to be consistent across iOS and Android
        JSONArray props = new JSONArray();
        int permissions = characteristic.getPermissions();

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ) != 0x0) {
            props.put("Read");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0x0) {
            props.put("Write");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED) != 0x0) {
            props.put("ReadEncrypted");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED) != 0x0) {
            props.put("WriteEncrypted");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM) != 0x0) {
            props.put("ReadEncryptedMITM");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM) != 0x0) {
            props.put("WriteEncryptedMITM");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED) != 0x0) {
            props.put("WriteSigned");
        }

        if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM) != 0x0) {
            props.put("WriteSignedMITM");
        }

        return props;
    }

    public static JSONArray decodePermissions(BluetoothGattDescriptor descriptor) {

        // NOTE: props strings need to be consistent across iOS and Android
        JSONArray props = new JSONArray();
        int permissions = descriptor.getPermissions();

        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ) != 0x0) {
            props.put("Read");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE) != 0x0) {
            props.put("Write");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) != 0x0) {
            props.put("ReadEncrypted");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) != 0x0) {
            props.put("WriteEncrypted");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM) != 0x0) {
            props.put("ReadEncryptedMITM");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) != 0x0) {
            props.put("WriteEncryptedMITM");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) != 0x0) {
            props.put("WriteSigned");
        }

        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM) != 0x0) {
            props.put("WriteSignedMITM");
        }

        return props;
    }

    public static void propertiesDescriptorWrite(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        JSONArray properties = decodeProperties(characteristic);
        for (int i = 0; i < properties.length(); i++) {
            try {
                String property = properties.getString(i);
                if (property.equals("Notify")) {
                    writeDescriptorNotify(characteristic, gatt, BluetoothGattLookUp.shortUUID(CLIENT_CHARACTERISTIC_CONFIGURATION));
                } else if (property.equals("Indication")) {
                    writeDescriptorIndication(characteristic, gatt, BluetoothGattLookUp.shortUUID(CLIENT_CHARACTERISTIC_CONFIGURATION));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static String decodeCharacteristicValue(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        String value = "unknown decoding value";

        if (characteristic.getUuid().toString().contains(CHARACTERISTIC_HUMIDITY)) {
            if (!characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0).equals(0)) {
                final float humidity = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) / 100f;
                final String humidityString = String.format(Locale.US, "%.1f %%", humidity);
                return humidityString;
            }
        } else if (characteristic.getUuid().toString().contains(CHARACTERISTIC_TEMPERATURE)) {
            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

            int offset = ((flags & 0x1) == 0) ? 1 : 5;
            String unit = ((flags & 0x1) == 0) ? "°C" : "°F";

            if (!characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, offset).equals(0)) {
                float temperature = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, offset);
                final String temperatureString = String.format(Locale.US, "%.1f %s", temperature, unit);
                return temperatureString;
            }
        } else if (characteristic.getUuid().toString().contains(CHARACTERISTIC_HEART_RATE)) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            final String heartRateString = String.format(Locale.US, "%d %s", heartRate, "bpm");
            return heartRateString;
        } /*else if (characteristic.getUuid().toString().contains(CHARACTERISTIC_BODY_SENSOR_LOCATION)) {
            final int bodyLocation = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
            return BluetoothGattLookUp.bodySensorLocationLookup(bodyLocation);
        }*/
        return value;
    }

    private static void writeDescriptorNotify(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt, UUID uuid) {
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
        gatt.setCharacteristicNotification(characteristic, true);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private static void writeDescriptorIndication(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt, UUID uuid) {
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

}