package com.uni.stuttgart.ipvs.androidgateway.gateway;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.security.Provider;

/**
 * Created by mdand on 3/17/2018.
 */

public class GatewayService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
