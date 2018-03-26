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

public class CharacteristicsDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "BleCharacteristicsData.db";
    public static final String ID = "id";
    public static final String SERVICE_UUID = "service_uuid";
    public static final String CHARACRERISTIC_UUID = "characteristic_uuid";
    public static final String TIMESTAMP = "timestamp";

    public CharacteristicsDatabase(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists BleCharacteristicsData " +
                        "(id integer primary key, service_uuid text,characteristic_uuid text,timestamp text)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS BleCharacteristicsData");
        onCreate(db);
    }

    public boolean insertData(String serviceUUID, String characteristicUUID) {
        boolean status = false;
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put("characteristic_uuid", characteristicUUID);
            contentValues.put("service_uuid", serviceUUID);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            String date = sdf.format(new Date());
            contentValues.put("timestamp", date);
            db.insert("BleCharacteristicsData", null, contentValues);
            status = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return status;
    }

    public boolean deleteAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from BleCharacteristicsData ");
        db.close();
        return true;
    }

    public Map<Integer, Map<String, Date>>  getAllData() {
        Map<Integer, Map<String, Date>> mapResult = new HashMap<>();
        Map<String, Date> mapData = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from BleCharacteristicsData", null );
        res.moveToFirst();

        while(res.isAfterLast() == false){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
            Date date = null;
            try {
                date = sdf.parse(res.getString(res.getColumnIndex(TIMESTAMP)));
                String data = res.getString(res.getColumnIndex(CHARACRERISTIC_UUID));
                String action = res.getString(res.getColumnIndex(SERVICE_UUID));
                int key = res.getInt(res.getColumnIndex(ID));
                mapData.put(data, date);
                mapResult.put(key, mapData);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return mapResult;
    }
}
