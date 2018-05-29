package com.uni.stuttgart.ipvs.androidgateway.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PowerUsageDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "PowerUsageData.db";
    public static final String TABLE_NAME = "PowerUsageData";
    public static final String ID_CASE = "id_case";
    public static final String BATTERY_LEVEL = "battery_level";
    public static final String BATTERY_LEVEL_UPPER = "battery_level_upper";
    public static final String POWER_CONSTRAINT1 = "power_usage1";
    public static final String POWER_CONSTRAINT2 = "power_usage2";
    public static final String POWER_CONSTRAINT3 = "power_usage3";

    public PowerUsageDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists PowerUsageData " +
                        "(id integer primary key, id_case text, battery_level real, battery_level_upper real, power_usage1 real, power_usage2 real, power_usage3 real, create_date text, modified_date text)"
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

    public boolean insertData(String idCase, double batteryLevel, double batteryLevelUpper, long powerUsage1, long powerUsage2, long powerUsage3) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put(ID_CASE, idCase);
            contentValues.put(BATTERY_LEVEL, batteryLevel);
            contentValues.put(BATTERY_LEVEL_UPPER, batteryLevelUpper);
            contentValues.put(POWER_CONSTRAINT1, powerUsage1);
            contentValues.put(POWER_CONSTRAINT2, powerUsage2);
            contentValues.put(POWER_CONSTRAINT3, powerUsage3);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("modified_date", date);
            if(!isDataExist(idCase)) {
                contentValues.put("create_date", date);
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
    // Fetch Database Section
    // ======================================================================================================================== //

    public long getPowerConstraint1(double batteryLevel) {
        String idCase = getIdCase(batteryLevel);
        Cursor cursor = getQuery("SELECT " + POWER_CONSTRAINT1 + " from PowerUsageData WHERE  "+ ID_CASE + " =?", new String[] {idCase});
        long result = 0;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getLong(cursor.getColumnIndex(POWER_CONSTRAINT1));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public long getPowerConstraint2(double batteryLevel) {
        String idCase = getIdCase(batteryLevel);
        Cursor cursor = getQuery("SELECT " + POWER_CONSTRAINT2 + " from PowerUsageData WHERE  "+ ID_CASE + " =?", new String[] {idCase});
        long result = 0;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getLong(cursor.getColumnIndex(POWER_CONSTRAINT2));
                cursor.close();
                break;
            }
        }
        return result;
    }

    public long getPowerConstraint3(double batteryLevel) {
        String idCase = getIdCase(batteryLevel);
        Cursor cursor = getQuery("SELECT " + POWER_CONSTRAINT2 + " from PowerUsageData WHERE  "+ ID_CASE + " =?", new String[] {idCase});
        long result = 0;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result = cursor.getLong(cursor.getColumnIndex(POWER_CONSTRAINT2));
                cursor.close();
                break;
            }
        }
        return result;
    }

    // ======================================================================================================================== //
    // Check Database Section
    // ======================================================================================================================== //


    public boolean isDataExist(String key) {
        boolean status = false;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT power_usage1 from PowerUsageData WHERE id_case=?", new String[] {key + ""});
        if(cursor.getCount() > 0) {
            status = true;
        }
        cursor.close();
        return status;
    }

    // ======================================================================================================================== //
    // Delete Database Section
    // ======================================================================================================================== //

    public boolean deleteAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from " + TABLE_NAME);
        db.close();
        return true;
    }

    // ======================================================================================================================== //
    // Some Routines Section
    // ======================================================================================================================== //

    private String getIdCase(double batteryLevel) {
        Cursor cursor = getQuery(TABLE_NAME, new String[] {ID_CASE, BATTERY_LEVEL, BATTERY_LEVEL_UPPER}, null, null, null, null, null);
        String result = null;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                double battLevelLower = cursor.getDouble(cursor.getColumnIndex(BATTERY_LEVEL));
                double battLevelUpper = cursor.getDouble(cursor.getColumnIndex(BATTERY_LEVEL_UPPER));
                if((batteryLevel > battLevelLower) && (batteryLevel <= battLevelUpper) ) {
                    result = cursor.getString(cursor.getColumnIndex(ID_CASE));
                    cursor.close();
                    break;
                }
            }
        }
        return result;
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

    private Cursor getQuery(String table, String[] columns, String selection,
                            String[] selectionArgs, String groupBy, String having,
                            String orderBy) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            cursor = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return  cursor;
    }
}
