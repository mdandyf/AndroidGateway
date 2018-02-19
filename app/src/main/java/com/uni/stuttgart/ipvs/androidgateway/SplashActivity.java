package com.uni.stuttgart.ipvs.androidgateway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by mdand on 2/18/2018.
 */

public class SplashActivity extends Activity {

    private final int STR_SPLASH_TIME = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        ImageView image = (ImageView) findViewById(R.id.imageViewBluetooth);
        startSplashTimer();
    }

    private void startSplashTimer() {
        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivity(intent);
                    finish();
                }
            }, STR_SPLASH_TIME);
        } catch (Exception e) {
            Log.d("Splash Activity", "Unhandled Exception", e);
        }
    }
}