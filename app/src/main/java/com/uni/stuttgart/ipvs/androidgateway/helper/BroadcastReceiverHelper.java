package com.uni.stuttgart.ipvs.androidgateway.helper;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BroadcastReceiverHelper extends BroadcastReceiver {

    private static final String TAG = "BluetoothBroadcast";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction()))
        {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

            if (type == BluetoothDevice.PAIRING_VARIANT_PIN)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    device.setPin(new byte[] {0x12, 0x34});
                }
                abortBroadcast();
            }
            else
            {
                Log.d(TAG, "Unexpected pairing type: " + type);
            }
        }
    }
}
