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
 * Created by marwanamrh on 8/9/2018.
 */

public class UploadsDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "BleUploadData.db";
    public static final String DEVICE_MAC = "mac_address";
    public static final String DEVICE_DATA = "device_data";
    public static final String UPLOAD_STATUS = "device_data";

    public UploadsDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleUploadData " +
                        "(id integer primary key, mac_address text, device_data text, upload_status text, create_date text, modified_date text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS BleUploadData");
        onCreate(db);
    }

    // ======================================================================================================================== //
    // Insert Database Section
    // ======================================================================================================================== //

    public boolean insertData(String mac_address, String device_data, String upload_status) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("mac_address", mac_address);
            contentValues.put("device_data", device_data);
            contentValues.put("upload_status", upload_status);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("modified_date", date);
            if(!isDeviceExist(mac_address)) {
                contentValues.put("create_date", date);
                db.insert("BleUploadData", null, contentValues);
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


    public boolean updateUploadStatus(String key, String state) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            if(isDeviceExist(key)) {
                contentValues.put("upload_status", state);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
                String date = sdf.format(new Date());
                contentValues.put("modified_date", date);
                db.update("BleUploadData", contentValues, "mac_address=?", new String[] {key + ""});
                status = true;
            }
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
        Cursor cursor = getQuery("SELECT mac_address from BleUploadData WHERE mac_address=?", new String[] {key + ""});
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
                String macAddress = cursor.getString(cursor.getColumnIndex(DEVICE_MAC));
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

    //Gets the data the needs to be uploaded
    public String getDeviceData(String macAddress) {
        Cursor cursor = getQuery("SELECT device_data from BleUploadData WHERE mac_address=?", new String[] {macAddress});
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getString(cursor.getColumnIndex(DEVICE_DATA));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public List<String> getAllDeviceData() {

        List<String> list = new ArrayList<>();
        Cursor cursor = getQuery("SELECT device_data from BleUploadData", null);
        if (cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String deviceData = cursor.getString(cursor.getColumnIndex(DEVICE_DATA));
                list.add(deviceData);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return list;

    }

    public List<String> getAllUploadStatus() {

        List<String> list = new ArrayList<>();
        Cursor cursor = getQuery("SELECT upload_status from BleUploadData", null);
        if (cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String uploadStatus = cursor.getString(cursor.getColumnIndex(UPLOAD_STATUS));
                list.add(uploadStatus);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return list;

    }

    public String getUploadStatus(String macAddress) {
        Cursor cursor = getQuery("SELECT upload_status from BleUploadData WHERE mac_address=?", new String[] {macAddress});
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getString(cursor.getColumnIndex(UPLOAD_STATUS));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public boolean deleteDeviceData() {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("delete from BleUploadData WHERE upload_status=Yes");
            db.close();
            status = true;
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
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
