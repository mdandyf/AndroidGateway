package com.uni.stuttgart.ipvs.androidgateway.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by marwanamrh on 5/29/2018.
 */

public class ManufacturerDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "BleManufacturerData.db";
    public static final String TABLE_NAME = "BleManufacturerData";
    public static final String MANUFACTURER_ID = "mfr_id";
    public static final String MANUFACTURER_NAME = "mfr_name";
    public static final String SERVICE_UUID = "service_uuid";


    public ManufacturerDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleManufacturerData " +
                        "(id integer primary key, mfr_id text, mfr_name text, service_uuid text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS BleManufacturerData");
        onCreate(db);
    }

    // ======================================================================================================================== //
    // Insert Database Section
    // ======================================================================================================================== //

    public boolean insertData(String mfrId, String mfrName, String serviceUUID) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MANUFACTURER_ID, mfrId);
            contentValues.put(MANUFACTURER_NAME, mfrName);
            contentValues.put(SERVICE_UUID, serviceUUID);
            if(!(isManufacturerExist(mfrId) && isManufacturerServiceExist(mfrId, serviceUUID))) {
                db.insert(TABLE_NAME, null, contentValues);
                status = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = false;
        }
        return status;
    }

    // ======================================================================================================================== //
    // Delete Database Section
    // ======================================================================================================================== //

    public boolean deleteAllData() {
        boolean status;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("delete from BleManufacturerData ");
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

    public boolean isManufacturerExist(String mfrId) {
        Cursor cursor = null;
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT mfr_id from BleManufacturerData WHERE mfr_id=?", new String[] {mfrId
                    + ""});
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


    public boolean isManufacturerServiceExist(String mfrId, String serviceUUID) {
        Cursor cursor = null;
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT mfr_id from BleManufacturerData WHERE mfr_id=? AND service_uuid=?", new String[] {mfrId
                    + "", serviceUUID + ""});
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


    // ======================================================================================================================== //
    // fetch data from Database Section
    // ======================================================================================================================== //

    public List<String> getListManufacturers() {
        List<String> list = new ArrayList<>();
        Cursor cursor = getQuery("SELECT mfr_id from BleManufacturerData", null);
        if (cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String mfrID = cursor.getString(cursor.getColumnIndex(MANUFACTURER_ID));
                list.add(mfrID);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return list;
    }

    public String getManufacturerName(String mfrID) {
        Cursor cursor = getQuery("SELECT mfr_name from BleManufacturerData WHERE mfr_id=?", new String[] {mfrID});
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getString(cursor.getColumnIndex(MANUFACTURER_NAME));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public List<ParcelUuid> getManufacturerServices(String mfrId) {
        List<ParcelUuid> listUUIDs = new ArrayList<>();
        Cursor cursor = getQuery("SELECT service_uuid from BleManufacturerData WHERE mfr_id=?", new String[] {mfrId});
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String uuid = cursor.getString(cursor.getColumnIndex(SERVICE_UUID));
                listUUIDs.add(ParcelUuid.fromString(uuid));
                cursor.moveToNext();
            }
        }
        cursor.close();
        return listUUIDs;
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