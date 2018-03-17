package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by mdand on 2/18/2018.
 */

public class JsonParser {
    private static final String LOG = "JsonParser";

    public static JSONObject readJsonObjectFromUrl(String url) {
        JSONObject json = null;
        try {
            String jsonString = readUrl(url);
            json = new JSONObject(jsonString);
        } catch (Exception e) {
            Log.d(LOG, "Error Parsing Json Object");
        }
        return json;
    }

    public static JSONObject readJsonObjectFromFile(File file) {
        JSONObject json = null;
        try {
            String jsonString = readFile(file);
            json = new JSONObject(jsonString);
        } catch (Exception e) {
            Log.d(LOG, "Error Parsing Json Object");
        }
        return json;
    }

    public static JSONObject readJsonObjectFromString(String jsonString) {
        JSONObject json = null;
        try {
            json = new JSONObject(jsonString);
        } catch (Exception e) {
            Log.d(LOG, "Error Parsing Json Object");
        }
        return json;
    }

    public static JSONArray readJsonArrayFromUrl(String url) {
        JSONArray json = null;
        try {
            String jsonString = readUrl(url);
            json = new JSONArray(jsonString);
        } catch (Exception e) {
            Log.d(LOG, "Error Parsing Json Object");
        }
        return json;
    }

    public static JSONArray readJsonArrayFromFile(File file) {
        JSONArray json = null;
        try {
            String jsonString = readFile(file);
            json = new JSONArray(jsonString);
        } catch (Exception e) {
            Log.d(LOG, "Error Parsing Json Object");
        }
        return json;
    }

    public static String readJsonString(JSONObject json) {
        return json.toString();
    }

    private static String readUrl(String url) throws IOException {
        URL content = new URL(url);
        URLConnection yc = content.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                yc.getInputStream()));
        String inputLine = null;
        while ((inputLine = in.readLine()) != null)
            Log.d(LOG, "reading from url");
        in.close();
        return inputLine;
    }

    private static String readFile(File file) throws IOException {
        String content = null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars);
            reader.close();
        } catch (IOException e) {
            Log.d(LOG, "error reading file");
            e.printStackTrace();
        } finally {
            if(reader !=null){
                reader.close();
            }
        }
        return content;
    }


}
