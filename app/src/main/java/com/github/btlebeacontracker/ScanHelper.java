package com.github.btlebeacontracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.github.btlebeacontracker.database.Devices;
import com.github.btlebeacontracker.widget.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ScanHelper {

    private static final String TAG = ScanHelper.class.toString();
    private static final int SCAN_PERIOD = 7000;
    private final Set<Item> devices = new HashSet<>();
    private final Handler mHandler = new Handler();
    private Runnable stopScan;

    private final Context context;
    private boolean noDevices;

    private final ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            onScanResult(result.getDevice());
        }

        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                onScanResult(result.getDevice());
            }
        }

        private void onScanResult(BluetoothDevice device) {
            final String address = device.getAddress();
            final String name = device.getName();
            final boolean isActive = Devices.containsDevice(context, address);

            Log.d(TAG, "device " + name + " with address " + address + " found");
            Item item = new Item(address, name, isActive);
            if (item.getCaption() != null && !item.getCaption().trim().isEmpty()) {
                devices.remove(item);
                devices.add(item);

                noDevices = false;
            }
        }
    };

    ScanHelper(Context context) {
        this.context = context;
    }

    void scan(final BluetoothAdapter mBluetoothAdapter, final ScanObserver scanObserver) {
        devices.clear();

        final Cursor cursor = Devices.findDevices(context);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                final String caption = cursor.getString(1);
                boolean isActive = Devices.isEnabled(context, address);
                devices.add(new Item(address, caption, isActive));
            } while (cursor.moveToNext());
        }


        final Toast text = Toast.makeText(context, context.getResources().getString(R.string.scanning), Toast.LENGTH_LONG);
        stopScan = new Runnable() {
            @Override
            public void run() {
                mHandler.removeCallbacks(stopScan);
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
                text.cancel();
                scanObserver.finishScan(devices);
                if (noDevices)
                    Toast.makeText(context, context.getResources().getString(R.string.no_beacons_found), Toast.LENGTH_SHORT).show();

            }
        };
        mHandler.postDelayed(stopScan, SCAN_PERIOD);
        noDevices = true;
        mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanCallback);
        text.show();

    }

    Set<Item> getDevices() {
        final Cursor cursor = Devices.findDevices(context);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                final String caption = cursor.getString(1);
                boolean isActive = Devices.isEnabled(context, address);
                devices.add(new Item(address, caption, isActive));
            } while (cursor.moveToNext());
        }
        return devices;
    }

    public interface ScanObserver {
        void finishScan(Set<Item> devices);
    }
}
