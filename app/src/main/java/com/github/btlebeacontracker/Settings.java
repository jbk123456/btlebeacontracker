package com.github.btlebeacontracker;

import android.content.Context;
import android.preference.PreferenceManager;

class Settings {

    private static final String ALARM_DURATION = "alarm_duration";

    static int getAlarmDuration(Context context) {
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(ALARM_DURATION, "0"));
    }
}
