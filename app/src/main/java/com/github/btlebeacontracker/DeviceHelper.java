package com.github.btlebeacontracker;

import android.content.Context;
import android.database.Cursor;

import com.github.btlebeacontracker.database.Devices;

import java.util.LinkedList;
import java.util.List;

class DeviceHelper {

    private final Context context;

    DeviceHelper(Context context) {
        this.context = context;
    }

    void addPersistedDevice(String address, String caption, boolean enabled) {
        if (caption == null) {
            return;
        }
        Devices.insert(context, caption, address);
        Devices.setEnabled(context, address, enabled);
    }

    private void removePersistedDevice(String address) {

        Devices.removeDevice(context, address);
    }


    void removeAllPersistedDevices() {
        List<String> names = new LinkedList<>();
        final Cursor cursor = Devices.findDevices(context);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                names.add(address);
            } while (cursor.moveToNext());
        }
        for (String name : names) {
            removePersistedDevice(name);
        }
    }

    public void disableAllPersistedDevices() {
        List<String> names = new LinkedList<>();
        final Cursor cursor = Devices.findDevices(context);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                names.add(address);
            } while (cursor.moveToNext());
        }
        for (String name : names) {
            Devices.setEnabled(context, name, false);
        }
    }

    public boolean isPersistedDevicesActive() {
        boolean active = false;
        final Cursor cursor = Devices.findDevices(context);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                if (Devices.containsDevice(context, address)) {
                    active = true;
                }
            } while (cursor.moveToNext());
        }

        return active;
    }

}
