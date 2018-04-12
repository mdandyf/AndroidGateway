package com.uni.stuttgart.ipvs.androidgateway.database;

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
    public static final String BLE_TIMESTAMP = "timestamp";

    public BleDeviceDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleDeviceData " +
                        "(mac_address text primary key, device_name text, device_rssi integer, device_state text, timestamp text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        db.execSQL("DROP TABLE IF EXISTS BleDeviceData");
        onCreate(db);
    }

    public boolean insertData(String data, String device_name, int rssi, String state) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(!isDeviceExist(data)) {
                contentValues.put("mac_address", data);
                contentValues.put("device_name", device_name);
                contentValues.put("device_rssi", rssi);
                contentValues.put("device_state", state);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("timestamp", date);
                db.insert("BleDeviceData", null, contentValues);
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
                contentValues.put("timestamp", date);
                db.update("BleDeviceData", contentValues, "mac_address=?", new String[] {key + ""});
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }

        return status;
    }



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
