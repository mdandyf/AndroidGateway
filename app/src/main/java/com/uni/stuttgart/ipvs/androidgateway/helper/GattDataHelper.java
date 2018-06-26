package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Xml;

import com.uni.stuttgart.ipvs.androidgateway.gateway.PManufacturer;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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

    public static String decodeCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        String value = "Unknown";

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            return "0x " + stringBuilder.toString();
        } else {
            //value = String.valueOf(Hex.encodeHex(characteristic.getValue()));
            //if (value.equals(null)) { value = "Unknown"; }
        }

        return value;
    }

    //Convert Dec to Hex
    private static final int sizeOfIntInHalfBytes = 8;
    private static final int numberOfBitsInAHalfByte = 4;
    private static final int halfByte = 0x0F;
    private static final char[] hexDigits = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static String decToHex(int dec) {
        StringBuilder hexBuilder = new StringBuilder(sizeOfIntInHalfBytes);
        hexBuilder.setLength(sizeOfIntInHalfBytes);
        for (int i = sizeOfIntInHalfBytes - 1; i >= 0; --i)
        {
            int j = dec & halfByte;
            hexBuilder.setCharAt(i, hexDigits[j]);
            dec >>= numberOfBitsInAHalfByte;
        }
        return hexBuilder.toString();
    }

    public static Document parseXML(InputSource source) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(source);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static XmlPullParser parseXML(InputStream in) throws XmlPullParserException, IOException {
       //try {
            XmlPullParserFactory parserFactory;
            parserFactory = XmlPullParserFactory.newInstance();

            XmlPullParser parser = parserFactory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return parser;
       /*}
        finally {
            in.close();
        }*/
    }

    public static ArrayList<PManufacturer> processParsing(XmlPullParser parser) throws XmlPullParserException, IOException {
        ArrayList<PManufacturer> manufacturers = new ArrayList<>();
        int eventType = parser.getEventType();
        PManufacturer currentManufacturer = null;

        while(eventType != XmlPullParser.END_DOCUMENT){
            String eltName = null;

            switch (eventType){
                case XmlPullParser.START_TAG:
                    eltName = parser.getName();

                    if("manufacturer".equalsIgnoreCase(eltName)){
                        currentManufacturer = new PManufacturer();
                        manufacturers.add(currentManufacturer);
                    } else if(currentManufacturer != null) {
                        if("id".equalsIgnoreCase(eltName)){
                            currentManufacturer.id = parser.nextText();
                        } else if ("name".equalsIgnoreCase(eltName)){
                            currentManufacturer.name = parser.nextText();
                        } else if ("service".equalsIgnoreCase(eltName)){
                            currentManufacturer.service = parser.nextText();
                        }
                    }
                    break;
            }

            eventType = parser.next();
        }

        return manufacturers;
    }

}
