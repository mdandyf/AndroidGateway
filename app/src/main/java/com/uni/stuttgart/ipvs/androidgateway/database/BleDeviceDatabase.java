package com.uni.stuttgart.ipvs.androidgateway.database;

import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.ContactsContract;

import java.sql.SQLClientInfoException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mdand on 2/17/2018.
 */

public class BleDeviceDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "BleDeviceData.db";
    public static final String BLE_ID = "id";
    public static final String BLE_DATA = "mac_address";
    public static final String BLE_NAME = "device_name";
    public static final String BLE_RSSI = "device_rssi";
    public static final String BLE_STATE = "device_state";
    public static final String BLE_CRT_DATE = "create_date";
    public static final String BLE_MDF_DATE = "modified_date";
    public static final String BLE_ADV_RECORD = "adv_record";
    public static final String BLE_POWER_USAGE = "power_usage";

    public BleDeviceDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleDeviceData " +
                        "(mac_address text primary key, device_name text, device_rssi integer, device_state text, adv_record blob, power_usage real, create_date text, modified_date text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS BleDeviceData");
        onCreate(db);
    }

    // ======================================================================================================================== //
    // Insert Database Section
    // ======================================================================================================================== //

    public boolean insertData(String data, String device_name, int rssi, String state) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("mac_address", data);
            contentValues.put("device_name", device_name);
            contentValues.put("device_rssi", rssi);
            contentValues.put("device_state", state);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("modified_date", date);
            if(!isDeviceExist(data)) {
                contentValues.put("create_date", date);
                db.insert("BleDeviceData", null, contentValues);
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    // ======================================================================================================================== //
    // Update Database Section
    // ======================================================================================================================== //


    public boolean updateData(String data, String device_name, int rssi, String state, byte[] scanRecord) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("mac_address", data);
            contentValues.put("device_name", device_name);
            contentValues.put("device_rssi", rssi);
            contentValues.put("adv_record", scanRecord);
            if(state != null) {contentValues.put("device_state", state);}
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("modified_date", date);
            if(isDeviceExist(data)) {
                db.update("BleDeviceData", contentValues, "mac_address = ?", new String[] {data});
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    public boolean updateDeviceAdvData(String key, byte[] scanRecord) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(isDeviceExist(key)) {
                contentValues.put("adv_record", scanRecord);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("modified_date", date);
                db.update("BleDeviceData", contentValues, "mac_address=?", new String[] {key + ""});
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }

        return status;
    }

    public boolean updateDeviceState(String key, String state) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(isDeviceExist(key)) {
                contentValues.put("device_state", state);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("modified_date", date);
                db.update("BleDeviceData", contentValues, "mac_address=?", new String[] {key + ""});
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    public boolean updateDevicePowerUsage(String key, long power_usage) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(isDeviceExist(key)) {
                contentValues.put("power_usage", power_usage);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("modified_date", date);
                db.update("BleDeviceData", contentValues, "mac_address=?", new String[] {key + ""});
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    public boolean updateAllDevicesState(List<String> listNearbyDevices, String deviceState) {
        boolean status = false;
        if(listNearbyDevices == null) {
            List<String> listDevices = getListDevices();
            for(String device : listDevices) { status = updateDeviceState(device, deviceState); }
        } else {
            for(String device : listNearbyDevices) { status = updateDeviceState(device, deviceState); }
        }
        return status;
    }

    // ======================================================================================================================== //
    // Delete Database Section
    // ======================================================================================================================== //


    public boolean deleteAllData() {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("delete from BleDeviceData ");
            db.close();
            status = true;
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    // ======================================================================================================================== //
    // Check Database Section
    // ======================================================================================================================== //


    public boolean isDeviceExist(String key) {
        boolean status = false;
        Cursor cursor = getQuery("SELECT mac_address from BleDeviceData WHERE mac_address=?", new String[] {key + ""});
        if(cursor.getCount() > 0) {
            status = true;
        }
        cursor.close();
        return status;
    }

    public boolean isDeviceExist() {
        boolean status = false;
        Cursor cursor = getQuery("SELECT mac_address from BleDeviceData", null);
        if(cursor.getCount() > 0) {
            status = true;
        }
        cursor.close();
        return status;
    }

    // ======================================================================================================================== //
    // select list data from Database Section
    // ======================================================================================================================== //

    public List<String> getListDevices() {
        List<String> list = new ArrayList<>();
        Cursor cursor = getQuery("SELECT mac_address from BleDeviceData", null);
        if (cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String macAddress = cursor.getString(cursor.getColumnIndex(BLE_DATA));
                list.add(macAddress);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return list;
    }

    public List<String> getListActiveDevices() {
        List<String> list = new ArrayList<>();
        String key = "active";
        Cursor cursor = getQuery("SELECT mac_address from BleDeviceData WHERE device_state=?", new String[] {key + ""});
        if (cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String macAddress = cursor.getString(cursor.getColumnIndex(BLE_DATA));
                list.add(macAddress);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return list;
    }

    // ======================================================================================================================== //
    // fetch data from Database Section
    // ======================================================================================================================== //

    public int getDeviceRssi(String macAddress) {
        Cursor cursor = getQuery("SELECT device_rssi from BleDeviceData WHERE mac_address=?", new String[] {macAddress});
        int result = 0;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getInt(cursor.getColumnIndex(BLE_RSSI));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public byte[] getDeviceScanRecord(String macAddress) {
        Cursor cursor = getQuery("SELECT adv_record from BleDeviceData WHERE mac_address=?", new String[] {macAddress});
        byte[] result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getBlob(cursor.getColumnIndex(BLE_ADV_RECORD));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public String getDeviceName(String macAddress) {
        Cursor cursor = getQuery("SELECT device_name from BleDeviceData WHERE mac_address=?", new String[] {macAddress});
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getString(cursor.getColumnIndex(BLE_NAME));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public String getDeviceState(String macAddress) {
        Cursor cursor = getQuery("SELECT device_state from BleDeviceData WHERE mac_address=?", new String[] {macAddress});
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getString(cursor.getColumnIndex(BLE_STATE));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public long getDevicePowerUsage(String macAddress) {
        Cursor cursor = getQuery("SELECT power_usage from BleDeviceData WHERE mac_address=?", new String[] {macAddress});
        long result = 0;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getLong(cursor.getColumnIndex(BLE_POWER_USAGE));
                cursor.close();
                break;
            }
        }
        return result;
    }

    // ======================================================================================================================== //
    // Some Routines Section
    // ======================================================================================================================== //

    private Cursor getQuery(String query, String[] argument) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery(query, argument);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cursor;
    }
}
