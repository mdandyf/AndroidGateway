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
import java.util.Date;
import java.util.HashMap;
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
    public static final String BLE_TIMESTAMP = "timestamp";

    public BleDeviceDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleDeviceData " +
                        "(mac_address text primary key, device_name text, device_rssi integer, timestamp text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        db.execSQL("DROP TABLE IF EXISTS BleDeviceData");
        onCreate(db);
    }

    public boolean insertData(String data, String device_name, int rssi) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(!isDeviceExist(data)) {
                contentValues.put("mac_address", data);
                contentValues.put("device_name", device_name);
                contentValues.put("device_rssi", rssi);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("timestamp", date);
                db.insert("BleDeviceData", null, contentValues);
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        }
        return status;
    }

    public boolean isDeviceExist(String key) {
        Cursor cursor = null;
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT mac_address from BleDeviceData WHERE mac_address=?", new String[] {key + ""});
            if(cursor.getCount() > 0) {
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }

        return status;
    }

    public Map<Integer, Map<String, Date>>  getAllData() {
        Map<Integer, Map<String, Date>> mapResult = new HashMap<>();
        Map<String, Date> mapData = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from BleDeviceData", null );
        res.moveToFirst();

        while(res.isAfterLast() == false){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            Date date = null;
            try {
                date = sdf.parse(res.getString(res.getColumnIndex(BLE_TIMESTAMP)));
                String data = res.getString(res.getColumnIndex(BLE_DATA));
                String action = res.getString(res.getColumnIndex(BLE_NAME));
                String rssi = res.getString(res.getColumnIndex(BLE_RSSI));
                int key = res.getInt(res.getColumnIndex(BLE_ID));
                mapData.put(data, date);
                mapResult.put(key, mapData);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return mapResult;
    }

}
