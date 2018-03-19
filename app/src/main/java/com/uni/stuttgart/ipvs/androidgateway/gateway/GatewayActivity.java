package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.uni.stuttgart.ipvs.androidgateway.R;

public class GatewayActivity extends AppCompatActivity {

    private int countCommandLine;

    private boolean mBound = false;
    private GatewayService mService;
    private Intent mBleService;
    private EditText textArea;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway);

        countCommandLine = 0;

        textArea = (EditText) findViewById(R.id.textArea);
        textArea.setFocusable(false);
        textArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                switch (event.getAction() & MotionEvent.ACTION_MASK){
                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });

        registerBroadcastListener();

        mBleService = new Intent(this, GatewayService.class);
        startService(mBleService);

        setCommandLine("Starting Gateway Service...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(mBleService);
        unregisterReceiver(mReceiver);
        finish();
    }

    private void registerBroadcastListener() {
        //Set a filter to only receive bluetooth state changed events.
        IntentFilter filter1 = new IntentFilter(GatewayService.MESSAGE_COMMAND);
        registerReceiver(mReceiver, filter1);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(action.equals(GatewayService.MESSAGE_COMMAND)) {
                String message = intent.getStringExtra("command");
                setCommandLine(message);
            }

        }

    };

    /**
     * View Related Routine
     */

    private void setCommandLine(String info) {
        textArea.append("\n");
        textArea.append(info);
    }


}
