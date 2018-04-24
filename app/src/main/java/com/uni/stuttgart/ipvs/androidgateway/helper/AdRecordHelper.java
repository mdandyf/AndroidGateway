package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.util.Log;

import com.neovisionaries.bluetooth.ble.advertising.ADManufacturerSpecific;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneEID;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneTLM;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneURL;
import com.neovisionaries.bluetooth.ble.advertising.IBeacon;
import com.neovisionaries.bluetooth.ble.advertising.UUIDs;
import com.neovisionaries.bluetooth.ble.advertising.Ucode;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdRecordHelper {

    private static final String TAG = "Bluetooth Advertisement";

    public static List<ADStructure> decodeAdvertisement(byte[] input) {
        return ADPayloadParser.getInstance().parse(input);
    }

    public static Map<String, Object> parseAdvertisement(List<ADStructure> structures) {

        Map<String, Object> mapListAdvertisement = new HashMap<>();

        for (ADStructure structure : structures) {
            if (structure instanceof IBeacon) {
                mapListAdvertisement.put("IBeacon", true);
                Log.d(TAG, "Device is advertising IBeacon");
                // An iBeacon was found.
                IBeacon iBeacon = (IBeacon) structure;
                // (1) Proximity UUID
                UUID uuid = iBeacon.getUUID();
                mapListAdvertisement.put("IBeaconUUID", uuid);
                Log.d(TAG, "UUID : " + uuid);
                // (2) Major number
                int major = iBeacon.getMajor();
                Log.d(TAG, "major : " + major);
                mapListAdvertisement.put("IBeaconMajor", major);
                // (3) Minor number
                int minor = iBeacon.getMinor();
                Log.d(TAG, "minor : " + minor);
                mapListAdvertisement.put("IBeaconMinor", minor);
                // (4) Tx Power
                int power = iBeacon.getPower();
                Log.d(TAG, "Tx power : " + power);
                mapListAdvertisement.put("IBeaconPower", power);

            } else if (structure instanceof EddystoneEID) {
                mapListAdvertisement.put("EddystoneEID", true);
                Log.d(TAG, "Device is advertising EddystoneEID");
                // Eddystone EID
                EddystoneEID es = (EddystoneEID)structure;

                // (1) Calibrated Tx power at 0 m.
                int power = es.getTxPower();
                Log.d(TAG, "Tx Power: " + power);
                mapListAdvertisement.put("EddystoneEIDPower", power);

                // (2) 8-byte EID
                byte[] eid = es.getEID();
                String eidAsString = es.getEIDAsString();
                Log.d(TAG, "EID: " + eidAsString);
                mapListAdvertisement.put("EddystoneEID", eidAsString);
            } else if (structure instanceof EddystoneTLM) {
                mapListAdvertisement.put("EddystoneTLM", true);
                Log.d(TAG, "Device is advertising EddystoneTLM");
                // Eddystone TLM
                EddystoneTLM es = (EddystoneTLM)structure;

                // (1) TLM Version
                int version = es.getTLMVersion();
                Log.d(TAG, "TLM Version: " + version);
                mapListAdvertisement.put("EddystoneTLMVersion", version);

                // (2) Battery Voltage
                int voltage = es.getBatteryVoltage();
                Log.d(TAG, "Battery: " + voltage + " V");
                mapListAdvertisement.put("EddystoneTLMVoltage", voltage);

                // (3) Beacon Temperature
                float temperature = es.getBeaconTemperature();
                Log.d(TAG, "Temperature: " + temperature + " C");
                mapListAdvertisement.put("EddystoneTLMTemperature", temperature);

                // (4) Advertisement count since power-on or reboot.
                long count = es.getAdvertisementCount();
                Log.d(TAG, "Adv Count: " + count);
                mapListAdvertisement.put("EddystoneTLMCount", count);

                // (5) Elapsed time in milliseconds since power-on or reboot.
                long elapsed = es.getElapsedTime();
                Log.d(TAG, "Elapsed time: " + elapsed);
                mapListAdvertisement.put("EddystoneTLMElapsed", elapsed);
            } else if(structure instanceof EddystoneURL) {
                mapListAdvertisement.put("EddystoneURL", true);
                Log.d(TAG, "Device is advertising EddystoneURL");
                EddystoneURL es = (EddystoneURL)structure;

                // (1) Calibrated Tx power at 0 m.
                int power = es.getTxPower();
                Log.d(TAG, "Power: " + power);
                mapListAdvertisement.put("EddystoneURLPower", power);

                // (2) URL
                URL url = es.getURL();
                Log.d(TAG, "Url: " + url);
                mapListAdvertisement.put("EddystoneURL", url);
            } else if(structure instanceof Ucode) {
                mapListAdvertisement.put("UCode", true);
                Log.d(TAG, "Device is advertising UCode");
                Ucode ucode = (Ucode)structure;

                // (1) Version
                int version = ucode.getVersion();
                Log.d(TAG, "Version: " + version);
                mapListAdvertisement.put("UCodeVersion", version);

                // (2) Ucode (32 upper-case hex letters)
                String ucodeString = ucode.getUcode();
                Log.d(TAG, "UCode: " + ucodeString);
                mapListAdvertisement.put("UCode", ucodeString);

                // (3) Status
                int status = ucode.getStatus();
                Log.d(TAG, "Status: " + status);
                mapListAdvertisement.put("UCodeStatus", status);

                // (4) The state of the battery
                boolean low = ucode.isBatteryLow();
                Log.d(TAG, "Battery Low: " + low);
                mapListAdvertisement.put("UCodeLow", low);

                // (5) Transmission interval
                int interval = ucode.getInterval();
                Log.d(TAG, "Transmission Interval: " + interval);
                mapListAdvertisement.put("UCodeTransInterval", interval);

                // (6) Transmission power
                int power = ucode.getPower();
                Log.d(TAG, "Transmission Power: " + power);
                mapListAdvertisement.put("UCodeTransPower", power);

                // (7) Transmission count
                int count = ucode.getCount();
                Log.d(TAG, "Transmission Count: " + count);
                mapListAdvertisement.put("UCodeTransCount", count);
            } else if(structure instanceof UUIDs) {
                mapListAdvertisement.put("UUIDs", true);
                UUIDs uuids = (UUIDs) structure;
                Log.d(TAG, "UUID: " + uuids.getUUIDs());
                mapListAdvertisement.put("DeviceUUID", uuids.getUUIDs());
            } else if(structure instanceof ADManufacturerSpecific) {
                mapListAdvertisement.put("Manufacturer", true);
                ADManufacturerSpecific manufacturerSpecific = (ADManufacturerSpecific) structure;
                Log.d(TAG, "Company Id: " + manufacturerSpecific.getCompanyId());
                mapListAdvertisement.put("CompanyId", manufacturerSpecific.getCompanyId());
                Log.d(TAG, "Type: " + manufacturerSpecific.getType());
                mapListAdvertisement.put("CompanyType", manufacturerSpecific.getType());
            }
        }

        return mapListAdvertisement;
    }
}
