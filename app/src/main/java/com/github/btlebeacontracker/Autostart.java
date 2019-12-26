package com.github.btlebeacontracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class Autostart extends BroadcastReceiver {
    public static final Class<BluetoothLEService> AUTOSTART_SERVICE =  BluetoothLEService.class;
    public static final String TAG = Autostart.class.toString();

    public void onReceive(Context context, Intent arg1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(new Intent(context, AUTOSTART_SERVICE));
        } else {
            context.startService(new Intent(context, AUTOSTART_SERVICE));
        }

        Log.i(TAG, "started");
    }
}