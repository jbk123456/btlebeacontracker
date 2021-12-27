package com.github.btlebeacontracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.github.btlebeacontracker.database.Devices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class BluetoothLEService extends Service {

    // --Commented out by Inspection (02.11.2019 15:33):public static final int NO_ALERT = 0x00;
    // --Commented out by Inspection (02.11.2019 15:33):public static final int MEDIUM_ALERT = 0x01;
    public static final int HIGH_ALERT = 0x02;

    private static final UUID IMMEDIATE_ALERT_SERVICE = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    private static final UUID FIND_ME_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    // --Commented out by Inspection (02.11.2019 15:33):public static final UUID LINK_LOSS_SERVICE = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID ALERT_LEVEL_CHARACTERISTIC = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    private static final UUID FIND_ME_CHARACTERISTIC = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // --Commented out by Inspection (02.11.2019 15:30):public static final UUID LINK_LOSS_CHARACTERISTIC = ALERT_LEVEL_CHARACTERISTIC;

    public static final String TAG = BluetoothLEService.class.toString();
    private static final int FOREGROUND_ID = 1667;
    private static final String CHANNEL_ID = "BluetoothLEService@" + FOREGROUND_ID;

    private final HashMap<String, BluetoothGatt> bluetoothGatt = new HashMap<>();
    private final HashMap<String, Boolean> connected = new HashMap<>();

    private BluetoothGattCharacteristic bluetoothGattCharacteristic;

    private Vibrator vib;
    private AudioManager audio;
    private int countOld = -1;
    private int failedCountOld = -1;
    private int gattCountOld = -1;

    private NotificationManager notificationManager;
    private final List<BluetoothServiceCallbacks> activities = new ArrayList<>();
    private final BackgroundBluetoothLEBinder myBinder = new BackgroundBluetoothLEBinder();
    private final Map<String, MediaPlayer> mediaMap = new HashMap<>();
    private final Handler handler = new Handler();

    private void stopSound(MediaPlayer mp) {
        if (mp != null) {
            mp.stop();
        }
    }

    private void playSound(final String address) {
        int duration = Settings.getAlarmDuration(this);
        if (duration == 0) {
            return;
        }
        switch (audio.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                final MediaPlayer mp = MediaPlayer.create(this, R.raw.pre_alarm);
                mediaMap.put(address, mp);

                mp.setLooping(true);
                mp.start();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        stopSound(mediaMap.remove(address));
                    }
                }, duration);
                Log.d(TAG, "onConnectionStateChange() address: ");

                // fall through
            case AudioManager.RINGER_MODE_VIBRATE:
                vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                break;
            case AudioManager.RINGER_MODE_SILENT:
                break;
        }
    }

    private void setCharacteristicNotification(BluetoothGatt bluetoothgatt,
                                               BluetoothGattCharacteristic bluetoothgattcharacteristic, boolean flag) {
        bluetoothgatt.setCharacteristicNotification(bluetoothgattcharacteristic, flag);
        if (FIND_ME_CHARACTERISTIC.equals(bluetoothgattcharacteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = bluetoothgattcharacteristic
                    .getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothgatt.writeDescriptor(descriptor);
            }
        }
    }

    private void enablePeerDeviceNotifyMe(BluetoothGatt bluetoothgatt, boolean flag) {
        BluetoothGattCharacteristic bluetoothgattcharacteristic = getCharacteristic(bluetoothgatt, FIND_ME_SERVICE,
                FIND_ME_CHARACTERISTIC);
        if (bluetoothgattcharacteristic != null && (bluetoothgattcharacteristic.getProperties() | 0x10) > 0) {
            setCharacteristicNotification(bluetoothgatt, bluetoothgattcharacteristic, flag);
        }
    }

    private BluetoothGattCharacteristic getCharacteristic(BluetoothGatt bluetoothgatt, UUID serviceUuid,
                                                          UUID characteristicUuid) {
        if (bluetoothgatt != null) {
            BluetoothGattService service = bluetoothgatt.getService(serviceUuid);
            if (service != null) {
                return service.getCharacteristic(characteristicUuid);
            }
        }

        return null;
    }

    public void registerClient(BluetoothServiceCallbacks activity) {
        synchronized (activities) {
            activities.add(activity);
        }
    }

    public void unregisterClient(BluetoothServiceCallbacks activity) {
        synchronized (activities) {
            activities.remove(activity);
        }
    }

    public void unregisterClients() {
        synchronized (activities) {
            activities.clear();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        serviceChannel.setShowBadge(false);

        notificationManager.createNotificationChannel(serviceChannel);
    }

    private void setForegroundEnabled() {
        createNotificationChannel();

        final Notification notification = getNotificationBuilder().build();
        this.startForeground(FOREGROUND_ID, notification);
        connect(); //FIXME: needed for autostart
    }

    private NotificationCompat.Builder getNotificationBuilder() {
        Intent notificationIntent = new Intent(this, BeaconsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                //.setContentTitle(getText(R.string.app_name))
                //.setContentText(getText(R.string.foreground_started))
                .setContentTitle(getText(R.string.foreground_started))
                .setSmallIcon(R.drawable.ic_launcher).setShowWhen(true).setOngoing(true)
                .setLargeIcon(null)
                .setContentIntent(pendingIntent);
    }

    private void updateNotification() {
        String health = getHelthStatus();
        if (health == null) {
            return;
        }
        NotificationCompat.Builder notificationBuilder = getNotificationBuilder();
        //notificationBuilder.setContentText(health);
        notificationBuilder.setContentTitle(health);
        if (failedCountOld > 0) {
            notificationBuilder.setColorized(true);
            notificationBuilder.setColor(ContextCompat.getColor(this, R.color.colorAccent));
        }
        notificationBuilder.setNumber(countOld - failedCountOld);
        notificationManager.notify(FOREGROUND_ID, notificationBuilder.build());
    }

    private String getHelthStatus() {

        int gattcount = bluetoothGatt.values().size();
        int count = connected.values().size();
        int failedCount = 0;
        for (Boolean b : connected.values()) {
            if (Boolean.FALSE == b) {
                failedCount++;
            }
        }

        if ((failedCount == failedCountOld) && (count == countOld) && (gattcount == gattCountOld)) {
            return null; // nothing changed
        }
        failedCountOld = failedCount;
        countOld = count;
        gattCountOld = gattcount;

        return count - failedCount + " out of " + gattcount + " beacons connected!";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        notificationManager = getSystemService(NotificationManager.class);
        this.setForegroundEnabled();

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    public void readRemoteRssi(final String address) {
        if (bluetoothGatt.get(address) == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        Objects.requireNonNull(bluetoothGatt.get(address)).readRemoteRssi();
    }

    public void immediateAlert(String address, int alertType) {
        Log.d(TAG, "immediateAlert() - the device " + address);
        if (bluetoothGatt.get(address) == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }

        final BluetoothGatt gatt = bluetoothGatt.get(address);
        for (BluetoothGattService service : Objects.requireNonNull(gatt).getServices()) {
            if (IMMEDIATE_ALERT_SERVICE.equals(service.getUuid())) {
                final BluetoothGattCharacteristic characteristic = service.getCharacteristics().get(0);
                characteristic.setValue(alertType, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Objects.requireNonNull(this.bluetoothGatt.get(address)).writeCharacteristic(characteristic);
                characteristic.setValue(new byte[]{0x2});
                Objects.requireNonNull(this.bluetoothGatt.get(address)).writeCharacteristic(characteristic);
            }
        }
    }

    public HashMap<String, Boolean> connect() {
        Log.d(TAG, "connect()");
        final Cursor cursor = Devices.findDevices(this);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                try {
                    if (Devices.isEnabled(this, address)) {
                        connect(address);
                    } else {
                        connected.remove(address); // may be reverted by callback, if device is still alive at this point
                        disconnect(address);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        updateNotification();
        return connected;
    }

    public void connect(final String address) {
        Log.d(TAG, "connect " + address);

        if (!this.bluetoothGatt.containsKey(address) || this.bluetoothGatt.get(address) == null) {
            if (!Devices.isEnabled(this, address))
                return;
            Log.d(TAG, "connect() - (new link) to device " + address);
            BluetoothDevice mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
            this.bluetoothGatt.put(address, mDevice.connectGatt(this, true, new CustomBluetoothGattCallback(address)));
        } else {
            Log.d(TAG, "reconnect() - discovering services for " + address);
            Objects.requireNonNull(this.bluetoothGatt.get(address)).discoverServices();
        }
    }

    public void disconnect(final String address) {
        if (this.bluetoothGatt.containsKey(address)) {
            if (Devices.isEnabled(this, address))
                return;
            Log.d(TAG, "disconnect() - from device " + address);
            if (this.bluetoothGatt.get(address) != null) {
                BluetoothGatt gatt = this.bluetoothGatt.get(address);
                gatt.disconnect();
                gatt.close();
            }
            this.bluetoothGatt.remove(address);
        }
    }

// --Commented out by Inspection START (02.11.2019 15:32):
//    public Boolean isDeviceConnected(String id) {
//        return connected.get(id);
//    }
// --Commented out by Inspection STOP (02.11.2019 15:32)

    public interface BluetoothServiceCallbacks {
        void gattConnected(String address);

        void gattDisconnected(String address);

        void onImmediateAlertAvailable(String address);

        void onRssi(String address, int rssi);

        void onBatteryLevel(String address, Integer valueOf);

        void onservicesDiscovered(String address);
    }

    private class CustomBluetoothGattCallback extends BluetoothGattCallback {

        private final String address;

        CustomBluetoothGattCallback(final String address) {
            this.address = address;
        }


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange() address: " + address + " status => " + status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.d(TAG, "onConnectionStateChange() address: " + address + " newState => " + newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    synchronized (activities) {
                        for (BluetoothServiceCallbacks activity : activities) {
                            activity.gattConnected(address);
                        }
                    }
                    connected.put(address, true);
                    updateNotification();
                    stopSound(mediaMap.remove(address));
                    gatt.discoverServices();
                }
            }

            Log.d(TAG, "onConnectionStateChange() address: " + address + " newState => " + newState);
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //connected.put(address, false);
                if (!Devices.isEnabled(BluetoothLEService.this, address)) { // user removed beacon from list
                    connected.remove(address);
                } else { // lost contact to beacon
                    connected.put(address, false);
                    playSound(address);
                }
                updateNotification();
                enablePeerDeviceNotifyMe(gatt, false);
                synchronized (activities) {
                    for (BluetoothServiceCallbacks activity : activities) {
                        activity.gattDisconnected(address);
                    }
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "onReadRemoteRssi() address: " + rssi + " status => " + status);

            // final Intent rssiIntent = new Intent(RSSI_RECEIVED);
            //rssiIntent.putExtra(RSSI_RECEIVED, rssi);
            synchronized (activities) {
                for (BluetoothServiceCallbacks activity : activities) {
                    activity.onRssi(address, rssi);
                }
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered()");
            synchronized (activities) {
                for (BluetoothServiceCallbacks activity : activities) {
                    activity.onservicesDiscovered(address);
                }
            }
            if (BluetoothGatt.GATT_SUCCESS == status) {

                for (BluetoothGattService service : gatt.getServices()) {
                    if (IMMEDIATE_ALERT_SERVICE.equals(service.getUuid())) {

                        synchronized (activities) {
                            for (BluetoothServiceCallbacks activity : activities) {
                                activity.onImmediateAlertAvailable(address);
                            }
                        }
                        gatt.readCharacteristic(
                                getCharacteristic(gatt, IMMEDIATE_ALERT_SERVICE, ALERT_LEVEL_CHARACTERISTIC));
                    }

                    // BUG! This code will cause a pairing with the beacon!
//                    if (LINK_LOSS_SERVICE.equals(service.getUuid())) {
//                        linkLossService = service;
//                        gatt.readCharacteristic(getCharacteristic(gatt, LINK_LOSS_SERVICE, LINK_LOSS_CHARACTERISTIC));
//                    }

                    if (BATTERY_SERVICE.equals(service.getUuid())) {
                        bluetoothGattCharacteristic = service.getCharacteristics().get(0);
                        gatt.readCharacteristic(bluetoothGattCharacteristic);
                    }

                    if (FIND_ME_SERVICE.equals(service.getUuid())) {
                        if (!service.getCharacteristics().isEmpty()) {
                            BluetoothGattCharacteristic buttonCharacteristic = service.getCharacteristics().get(0);
                            setCharacteristicNotification(gatt, buttonCharacteristic, true);
                        }
                    }
                }
                enablePeerDeviceNotifyMe(gatt, true);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite() descriptor: " + descriptor + " status => " + status);

            gatt.readCharacteristic(bluetoothGattCharacteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged()");
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead() address: " + characteristic + " status => " + status);
            if (characteristic.getValue() != null && characteristic.getValue().length > 0) {
                final byte level = characteristic.getValue()[0];
                synchronized (activities) {
                    for (BluetoothServiceCallbacks activity : activities) {
                        activity.onBatteryLevel(address, (int) level);
                    }
                }
            }
        }
    }

    class BackgroundBluetoothLEBinder extends Binder {
        BluetoothLEService service() {
            return BluetoothLEService.this;
        }
    }
}
