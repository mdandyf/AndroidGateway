package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

/**
 * Created by mdand on 2/26/2018.
 */

public class GattDataHelper {

    private static final String CHARACTERISTIC_TEMPERATURE = "2a1c";
    private static final String CHARACTERISTIC_HUMIDITY = "2a6f";
    public final static String CHARACTERISTIC_HEART_RATE = "2a37";
    private final static String CHARACTERISTIC_BODY_SENSOR_LOCATION = "2a38";

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
            props.put("AuthenticateSignedWrites");
        }

        if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0x0) {
            props.put("ExtendedProperties");
        }

        return props;
    }

    public static JSONArray decodePermissions(BluetoothGattCharacteristic characteristic) {

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

    public static String decodeCharacteristicValue(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        String value = "unknown decoding value";

        if (characteristic.getUuid().toString().contains(CHARACTERISTIC_HUMIDITY)) {
            if (!characteristic.getIntValue(FORMAT_UINT16, 0).equals(0)) {
                final float humidity = characteristic.getIntValue(FORMAT_UINT16, 0) / 100f;
                final String humidityString = String.format(Locale.US, "%.1f %%", humidity);
                return humidityString;
            }
        } else if (characteristic.getUuid().toString().contains(CHARACTERISTIC_TEMPERATURE)) {
            byte[] buf = characteristic.getValue();
            if (buf != null && buf.length > 1) {
                int flags = characteristic.getIntValue(FORMAT_UINT8, 0);

                int offset = ((flags & 0x1) == 0) ? 1 : 5;
                String unit = ((flags & 0x1) == 0) ? "°C" : "°F";

                float temperature = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, offset);
                final String temperatureString = String.format(Locale.US, "%.1f %s", temperature, unit);
                return temperatureString;
            }
        } else  {

            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                return "0x " + stringBuilder.toString();
            }
        }
        return value;
    }

}
