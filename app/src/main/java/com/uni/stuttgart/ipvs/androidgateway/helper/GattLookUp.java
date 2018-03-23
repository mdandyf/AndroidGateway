package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Created by mdand on 2/20/2018.
 */

public class GattLookUp {
    public static final UUID CCC_DESCRIPTOR = shortUUID("2902");

    /** Known GATT Services */

    private static HashMap<UUID, String> services = new HashMap<UUID, String>();
    static {
        services.put(shortUUID("1811"), "Alert Notification Service");
        services.put(shortUUID("180F"), "Battery Service");
        services.put(shortUUID("1810"), "Blood Pressure");
        services.put(shortUUID("181B"), "Body Composition");
        services.put(shortUUID("181E"), "Bond Management");
        services.put(shortUUID("181F"), "Continuous Glucose Monitoring");
        services.put(shortUUID("1805"), "Current Time Service");
        services.put(shortUUID("1818"), "Cycling Power");
        services.put(shortUUID("1816"), "Cycling Speed and Cadence");
        services.put(shortUUID("180A"), "Device Information");
        services.put(shortUUID("181A"), "Environmental Sensing");
        services.put(shortUUID("1800"), "Generic Access");
        services.put(shortUUID("1801"), "Generic Attribute");
        services.put(shortUUID("1808"), "Glucose");
        services.put(shortUUID("1809"), "Health Thermometer");
        services.put(shortUUID("180D"), "Heart Rate");
        services.put(shortUUID("1812"), "Human Interface Device");
        services.put(shortUUID("1802"), "Immediate Alert");
        services.put(shortUUID("1820"), "Internet Protocol Support");
        services.put(shortUUID("1803"), "Link Loss");
        services.put(shortUUID("1819"), "Location and Navigation");
        services.put(shortUUID("1807"), "Next DST Change Service");
        services.put(shortUUID("180E"), "Phone Alert Status Service");
        services.put(shortUUID("1806"), "Reference Time Update Service");
        services.put(shortUUID("1814"), "Running Speed and Cadence");
        services.put(shortUUID("1813"), "Scan Parameters");
        services.put(shortUUID("1804"), "Tx Power");
        services.put(shortUUID("181C"), "User Data");
        services.put(shortUUID("181D"), "Weight Scale");
    }

    /** Known GATT Descriptor */
    private static HashMap<UUID, String> descriptors = new HashMap<UUID, String>();
    static {
        descriptors.put(shortUUID("2905"), "Characteristic Aggregate Format");
        descriptors.put(shortUUID("2900"), "Characteristic Extended Properties");
        descriptors.put(shortUUID("2904"), "Characteristic Presentation Format");
        descriptors.put(shortUUID("2901"), "Characteristic User Description");
        descriptors.put(shortUUID("2902"), "Client Characteristic Configuration");
        descriptors.put(shortUUID("290B"), "Environmental Sensing Configuration");
        descriptors.put(shortUUID("290C"), "Environmental Sensing Measurement");
        descriptors.put(shortUUID("290D"), "Environmental Sensing Trigger Setting");
        descriptors.put(shortUUID("2907"), "External Report Reference");
        descriptors.put(shortUUID("2909"), "Number of Digitals");
        descriptors.put(shortUUID("2908"), "Report Reference");
        descriptors.put(shortUUID("2903"), "Server Characteristic Configuration");
        descriptors.put(shortUUID("290E"), "Time Trigger Setting");
        descriptors.put(shortUUID("2906"), "Valid Range");
        descriptors.put(shortUUID("290A"), "Value Trigger Setting");
    }

    /** Known GATT Characteristic */

    private static HashMap<UUID, String> characteristics = new HashMap<UUID, String>();
    static {
        characteristics.put(shortUUID("2A43"), "Alert Category ID");
        characteristics.put(shortUUID("2A42"), "Alert Category ID Bit Mask");
        characteristics.put(shortUUID("2A06"), "Alert Level");
        characteristics.put(shortUUID("2A44"), "Alert Notification Control Point");
        characteristics.put(shortUUID("2A3F"), "Alert Status");
        characteristics.put(shortUUID("2A01"), "Appearance");
        characteristics.put(shortUUID("2A19"), "Battery Level");
        characteristics.put(shortUUID("2A49"), "Blood Pressure Feature");
        characteristics.put(shortUUID("2A35"), "Blood Pressure Measurement");
        characteristics.put(shortUUID("2A38"), "Body Sensor Location");
        characteristics.put(shortUUID("2A22"), "Boot Keyboard Input Report");
        characteristics.put(shortUUID("2A32"), "Boot Keyboard Output Report");
        characteristics.put(shortUUID("2A33"), "Boot Mouse Input Report");
        characteristics.put(shortUUID("2A5C"), "CSC Feature");
        characteristics.put(shortUUID("2A5B"), "CSC Measurement");
        characteristics.put(shortUUID("2A2B"), "Current Time");
        characteristics.put(shortUUID("2A66"), "Cycling Power Control Point");
        characteristics.put(shortUUID("2A65"), "Cycling Power Feature");
        characteristics.put(shortUUID("2A63"), "Cycling Power Measurement");
        characteristics.put(shortUUID("2A64"), "Cycling Power Vector");
        characteristics.put(shortUUID("2A6F"), "Humidity Measurement");
        characteristics.put(shortUUID("2A08"), "Date Time");
        characteristics.put(shortUUID("2A0A"), "Day Date Time");
        characteristics.put(shortUUID("2A09"), "Day of Week");
        characteristics.put(shortUUID("2A00"), "Device Name");
        characteristics.put(shortUUID("2A0D"), "DST Offset");
        characteristics.put(shortUUID("2A0C"), "Exact Time 256");
        characteristics.put(shortUUID("2A26"), "Firmware Revision String");
        characteristics.put(shortUUID("2A51"), "Glucose Feature");
        characteristics.put(shortUUID("2A18"), "Glucose Measurement");
        characteristics.put(shortUUID("2A34"), "Glucose Measurement Context");
        characteristics.put(shortUUID("2A27"), "Hardware Revision String");
        characteristics.put(shortUUID("2A39"), "Heart Rate Control Point");
        characteristics.put(shortUUID("2A37"), "Heart Rate Measurement");
        characteristics.put(shortUUID("2A4C"), "HID Control Point");
        characteristics.put(shortUUID("2A4A"), "HID Information");
        characteristics.put(shortUUID("2A2A"), "IEEE 11073-20601 Regulatory Certification Data List");
        characteristics.put(shortUUID("2A36"), "Intermediate Cuff Pressure");
        characteristics.put(shortUUID("2A1E"), "Intermediate Temperature");
        characteristics.put(shortUUID("2A6B"), "LN Control Point");
        characteristics.put(shortUUID("2A6A"), "LN Feature");
        characteristics.put(shortUUID("2A0F"), "Local Time Information");
        characteristics.put(shortUUID("2A67"), "Location and Speed");
        characteristics.put(shortUUID("2A29"), "Manufacturer Name String");
        characteristics.put(shortUUID("2A21"), "Measurement Interval");
        characteristics.put(shortUUID("2A24"), "Model Number String");
        characteristics.put(shortUUID("2A68"), "Navigation");
        characteristics.put(shortUUID("2A46"), "New Alert");
        characteristics.put(shortUUID("2A04"), "Peripheral Preferred Connection Parameters");
        characteristics.put(shortUUID("2A02"), "Peripheral Privacy Flag");
        characteristics.put(shortUUID("2A50"), "PnP ID");
        characteristics.put(shortUUID("2A69"), "Position Quality");
        characteristics.put(shortUUID("2A4E"), "Protocol Mode");
        characteristics.put(shortUUID("2A03"), "Reconnection Address");
        characteristics.put(shortUUID("2A52"), "Record Access Control Point");
        characteristics.put(shortUUID("2A14"), "Reference Time Information");
        characteristics.put(shortUUID("2A4D"), "Report");
        characteristics.put(shortUUID("2A4B"), "Report Map");
        characteristics.put(shortUUID("2A40"), "Ringer Control Point");
        characteristics.put(shortUUID("2A41"), "Ringer Setting");
        characteristics.put(shortUUID("2A54"), "RSC Feature");
        characteristics.put(shortUUID("2A53"), "RSC Measurement");
        characteristics.put(shortUUID("2A55"), "SC Control Point");
        characteristics.put(shortUUID("2A4F"), "Scan Interval Window");
        characteristics.put(shortUUID("2A31"), "Scan Refresh");
        characteristics.put(shortUUID("2A5D"), "Sensor Location");
        characteristics.put(shortUUID("2A25"), "Serial Number String");
        characteristics.put(shortUUID("2A05"), "Service Changed");
        characteristics.put(shortUUID("2A28"), "Software Revision String");
        characteristics.put(shortUUID("2A47"), "Supported New Alert Category");
        characteristics.put(shortUUID("2A48"), "Supported Unread Alert Category");
        characteristics.put(shortUUID("2A23"), "System ID");
        characteristics.put(shortUUID("2A1C"), "Temperature Measurement");
        characteristics.put(shortUUID("2A1D"), "Temperature Type");
        characteristics.put(shortUUID("2A12"), "Time Accuracy");
        characteristics.put(shortUUID("2A13"), "Time Source");
        characteristics.put(shortUUID("2A16"), "Time Update Control Point");
        characteristics.put(shortUUID("2A17"), "Time Update State");
        characteristics.put(shortUUID("2A11"), "Time with DST");
        characteristics.put(shortUUID("2A0E"), "Time Zone");
        characteristics.put(shortUUID("2A07"), "Tx Power Level");
        characteristics.put(shortUUID("2A45"), "Unread Alert Status");
        characteristics.put(shortUUID("2A5A"), "Aggregate Input");
        characteristics.put(shortUUID("2A58"), "Analog Input");
        characteristics.put(shortUUID("2A59"), "Analog Output");
        characteristics.put(shortUUID("2A56"), "Digital Input");
        characteristics.put(shortUUID("2A57"), "Digital Output");
        characteristics.put(shortUUID("2A0B"), "Exact Time 100");
        characteristics.put(shortUUID("2A3E"), "Network Availability");
        characteristics.put(shortUUID("2A3C"), "Scientific Temperature in Celsius");
        characteristics.put(shortUUID("2A10"), "Secondary Time Zone");
        characteristics.put(shortUUID("2A3D"), "String");
        characteristics.put(shortUUID("2A1F"), "Temperature in Celsius");
        characteristics.put(shortUUID("2A20"), "Temperature in Fahrenheit");
        characteristics.put(shortUUID("2A15"), "Time Broadcast");
        characteristics.put(shortUUID("2A1B"), "Battery Level State");
        characteristics.put(shortUUID("2A1A"), "Battery Power State");
        characteristics.put(shortUUID("2A5F"), "Pulse Oximetry Continuous Measurement");
        characteristics.put(shortUUID("2A62"), "Pulse Oximetry Control Point");
        characteristics.put(shortUUID("2A61"), "Pulse Oximetry Features");
        characteristics.put(shortUUID("2A60"), "Pulse Oximetry Pulsatile Event");
        characteristics.put(shortUUID("2A5E"), "Pulse Oximetry Spot-Check Measurement");
        characteristics.put(shortUUID("2A52"), "Record Access Control point (Test Version)");
        characteristics.put(shortUUID("2A3A"), "Removable");
        characteristics.put(shortUUID("2A3B"), "Service Required");
        characteristics.put(shortUUID("2AA6"), "Central Address Resolution");
        characteristics.put(shortUUID("2AA8"), "CGM Feature");
        characteristics.put(shortUUID("2AA7"), "CGM Measurement");
    }

    // WRITE TYPES

    private static HashMap<String,Integer> characteristicFormats = new HashMap<String, Integer>();
    static {
        characteristicFormats.put("uint8" , BluetoothGattCharacteristic.FORMAT_UINT8);
        characteristicFormats.put("uint16", BluetoothGattCharacteristic.FORMAT_UINT16);
        characteristicFormats.put("uint32", BluetoothGattCharacteristic.FORMAT_UINT32);
        characteristicFormats.put("sint8" , BluetoothGattCharacteristic.FORMAT_SINT8);
        characteristicFormats.put("sint16", BluetoothGattCharacteristic.FORMAT_SINT16);
        characteristicFormats.put("sint32", BluetoothGattCharacteristic.FORMAT_SINT32);
        characteristicFormats.put("float" , BluetoothGattCharacteristic.FORMAT_FLOAT);
        characteristicFormats.put("sfloat", BluetoothGattCharacteristic.FORMAT_SFLOAT);
        characteristicFormats.put("string", -1);
        characteristicFormats.put("byte[]", -2);
    }

    private static HashMap<String, String> bodySensorLocations = new HashMap<>();
    static {
        bodySensorLocations.put("0x00", "Other");
        bodySensorLocations.put("0x01", "Chest");
        bodySensorLocations.put("0x02", "Wrist");
        bodySensorLocations.put("0x03", "Finger");
        bodySensorLocations.put("0x04", "Hand");
        bodySensorLocations.put("0x05", "Ear Lobe");
        bodySensorLocations.put("0x06", "Foot");
    }

    // PUBLIC LOOKUP FUNCTIONS

    public static String descriptorNameLookup(UUID uuid) {
        String descriptor = descriptors.get(uuid);
        return (descriptor==null) ? "unknown" : descriptor;
    }

    public static String serviceNameLookup(UUID uuid) {
        String service = services.get(uuid);
        return (service==null) ? "unknown" : service;
    }

    public static String characteristicNameLookup(UUID uuid) {
        String characteristic = characteristics.get(uuid);
        return (characteristic==null) ? "unknown" : characteristic;
    }

    public static String bodySensorLocationLookup(String i) {
        return (bodySensorLocations.get(i) == null) ? "unknown" : bodySensorLocations.get(i);
    }

    public static Integer formatLookup(String format) {
        return characteristicFormats.get(format);
    }

    // SHORT UUID GENERATOR (WHERE s IS 4-DIGIT HEX STRING)

    public static UUID shortUUID(String s) {
        return UUID.fromString("0000" + s + "-0000-1000-8000-00805f9b34fb");
    }

}
