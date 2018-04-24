package com.uni.stuttgart.ipvs.androidgateway.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mdand on 3/23/2018.
 */

public class ServicesDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "BleServicesData.db";
    public static final String BLE_ID = "id";
    public static final String BLE_DATA = "mac_address";
    public static final String SERVICE_UUID = "service_uuid";
    public static final String BLE_TIMESTAMP = "create_date";
    public static final String BLE_MOD_DATE = "modified_date";

    public ServicesDatabase(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleServicesData " +
                        "(id integer primary key, mac_address text, service_uuid text, create_date text, modified_date text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS BleServicesData");
        onCreate(db);
    }

    public boolean insertData(String data, String serviceUUID) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("mac_address", data);
            contentValues.put("service_uuid", serviceUUID);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("modified_date", date);

            if (isMacExist(data) && isServiceExist(serviceUUID)) {
                db.update("BleServicesData", contentValues, "mac_address = ? AND service_uuid = ?", new String[]{data, serviceUUID});
            } else {
                contentValues.put("create_date", date);
                db.insert("BleServicesData", null, contentValues);
            }
            status = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return status;
    }

    public boolean deleteAllData() {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("delete from BleServicesData ");
            db.close();
            status = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return status;
    }

    public boolean isMacExist(String key) {
        Cursor cursor = null;
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT mac_address from BleServicesData WHERE mac_address=?", new String[]{key + ""});
            if (cursor.getCount() > 0) {
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return status;
    }

    public boolean isServiceExist(String key) {
        Cursor cursor = null;
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT service_uuid from BleServicesData WHERE service_uuid=?", new String[]{key + ""});
            if (cursor.getCount() > 0) {
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return status;
    }

    public Map<Integer, Map<String, Date>> getAllData() {
        Map<Integer, Map<String, Date>> mapResult = new HashMap<>();
        Map<String, Date> mapData = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery("select * from BleServicesData", null);
        res.moveToFirst();

        while (res.isAfterLast() == false) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            Date date = null;
            try {
                date = sdf.parse(res.getString(res.getColumnIndex(BLE_TIMESTAMP)));
                String data = res.getString(res.getColumnIndex(BLE_DATA));
                String action = res.getString(res.getColumnIndex(SERVICE_UUID));
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
