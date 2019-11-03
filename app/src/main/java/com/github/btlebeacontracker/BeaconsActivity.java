package com.github.btlebeacontracker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.github.btlebeacontracker.database.Devices;
import com.github.btlebeacontracker.widget.Item;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BeaconsActivity extends AppCompatActivity
        implements BluetoothLEService.BluetoothServiceCallbacks, NavigationView.OnNavigationItemSelectedListener, ScanFragment.ScanCallbacks, BeaconFragment.BeaconCallbacks {

    // --Commented out by Inspection (02.11.2019 15:32):public static final String TAG = BeaconsActivity.class.toString();
    private static final int REQUEST_ENABLE_BT = 1;
    private final Set<Item> devices = new HashSet<>();
    private BluetoothLEService service;

    private final Handler handler = new Handler();
    private NavigationView navigationView;

    private final Set<Item> items = new HashSet<>();
    private BluetoothAdapter mBluetoothAdapter;

    private DrawerLayout drawer;

    private Fragment mContent;

    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            if (iBinder instanceof BluetoothLEService.BackgroundBluetoothLEBinder) {
                service = ((BluetoothLEService.BackgroundBluetoothLEBinder) iBinder).service();
                service.registerClient(BeaconsActivity.this);
                HashMap<String, Boolean> devicesConnected = service.connect();

                initializeDrawerConnectedIcons(devicesConnected);

                setMainFragment();
            }
        }

        private void setMainFragment() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Fragment visibleFragment;
                    if (devices.isEmpty()) {
                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        transaction.replace(R.id.fragment_container, new ScanFragment());
                        transaction.commit();
                    } else if ((visibleFragment = getVisibleFragment()) == null) {
                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        Item item = devices.iterator().next();
                        transaction.replace(R.id.fragment_container, new BeaconFragment().init(item.getId(), item.getCaption()));
                        transaction.commit();
                    } else { // restored instance state, re-attach service and update view from service
                        ((IServiceConnectionListener) visibleFragment).serviceConnected();
                    }
                }
            });

        }

        private Fragment getVisibleFragment() {
            FragmentManager fragmentManager = getSupportFragmentManager();
            List<Fragment> fragments = fragmentManager.getFragments();
            for (Fragment fragment : fragments) {
                if (fragment != null && fragment.isVisible())
                    return fragment;
            }
            return null;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service.unregisterClient(BeaconsActivity.this);
            Log.d(BluetoothLEService.TAG, "onServiceDisconnected()");
            service = null;
        }
    };

    private void initializeDrawerConnectedIcons(HashMap<String, Boolean> devicesConnected) {
        for (Map.Entry<String, Boolean> entry : devicesConnected.entrySet()) {
            if (Boolean.TRUE == entry.getValue()) {
                gattConnected(entry.getKey());
            } else if (Boolean.FALSE == entry.getValue()) {
                gattDisconnected(entry.getKey());
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        bindService(new Intent(this, BluetoothLEService.class), serviceConnection, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.service = null;
        unbindService(serviceConnection);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        //Save the fragment's instance
        if (mContent != null) {
            getSupportFragmentManager().putFragment(outState, BeaconFragment.class.getName(), mContent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            //Restore the fragment's instance
            mContent = getSupportFragmentManager().getFragment(savedInstanceState, BeaconFragment.class.getName());
        }

        setContentView(R.layout.activity_beacons);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }


        // detect Bluetooth enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        navigationView.setNavigationItemSelectedListener(this);
        drawer.addDrawerListener(toggle);

        toggle.syncState();


        initializeManagedBeaconItems();

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = Objects.requireNonNull(bluetoothManager).getAdapter();

        startService(new Intent(this, BluetoothLEService.class));
    }

    private void initializeManagedBeaconItems() {
        final Cursor cursor = Devices.findDevices(this);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String address = cursor.getString(0);
                final String caption = cursor.getString(1);
                boolean isActive = Devices.isEnabled(this, address);
                if (isActive) {
                    devices.add(new Item(address, caption, isActive));
                }
            } while (cursor.moveToNext());
        }

        for (Item device : devices) {
            addBeaconItem(device);
        }
    }

    private void updateBeaconIcon(final String address, final int icon) {
        for (int drawerMenuId = 0; drawerMenuId < navigationView.getMenu().getItem(1).getSubMenu().size(); drawerMenuId++) {
            if (address.equals(navigationView.getMenu().getItem(1).getSubMenu().getItem(drawerMenuId).getTooltipText().toString())) {
                final MenuItem i = navigationView.getMenu().getItem(1).getSubMenu().getItem(drawerMenuId);
                i.setIcon(icon);
            }
        }
    }

    public void addBeaconItem(Item v) {
        for (int drawerMenuId = 0; drawerMenuId < navigationView.getMenu().getItem(1).getSubMenu().size(); drawerMenuId++) {
            if (v.getId().equals(navigationView.getMenu().getItem(1).getSubMenu().getItem(drawerMenuId).getTooltipText().toString())) {
                return;
            }
        }
        int itemId = navigationView.getMenu().getItem(1).getSubMenu().size();
        MenuItem i = navigationView.getMenu().getItem(1).getSubMenu().add(0, itemId, 0, v.getCaption());
        items.add(v);
        i.setTooltipText(v.getId());
        i.setIcon(R.drawable.ic_setup_phonelink);
    }


    public void removeBeaconItem(Item v) {
        Menu m = navigationView.getMenu().getItem(1).getSubMenu();
        for (int drawerMenuId = 0; drawerMenuId < m.size(); drawerMenuId++) {
            if (v.getId().equals(m.getItem(drawerMenuId).getTooltipText().toString())) {
                items.remove(v);
            }
        }
        List<MenuItem> menuItems = new ArrayList<>();

        for (int drawerMenuId = 0; drawerMenuId < m.size(); drawerMenuId++) {
            if (!v.getId().equals(m.getItem(drawerMenuId).getTooltipText().toString())) {
                menuItems.add(m.getItem(drawerMenuId));
            }
        }
        m.clear();
        for (MenuItem menuItem : menuItems) {
            MenuItem mItem = m.add(0, menuItem.getItemId(), 0, menuItem.getTitle());
            mItem.setTooltipText(menuItem.getTooltipText());
            mItem.setIcon(menuItem.getIcon());
        }
    }

    @Override
    public void showFragmentForDevice(String id, String caption) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new BeaconFragment().init(id, caption));
        transaction.commit();
    }

    @Override
    public void showScanFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new ScanFragment());
        transaction.commit();
    }

    @Override
    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    @Override
    public BluetoothLEService getService() {
        return service;
    }

    @Override
    public void updateBeaconItem(String id, String caption) {
        Devices.updateDevice(this, id, caption);

        for (int drawerMenuId = 0; drawerMenuId < navigationView.getMenu().getItem(1).getSubMenu().size(); drawerMenuId++) {
            if (id.equals(navigationView.getMenu().getItem(1).getSubMenu().getItem(drawerMenuId).getTooltipText().toString())) {
                MenuItem i = navigationView.getMenu().getItem(1).getSubMenu().getItem(drawerMenuId);
                i.setTitle(caption);
                for (Item item : items) {
                    if (item.getId().equals(id)) {
                        item.setCaption(caption);
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        if (service == null) {
            return false;
        }
        int id = menuItem.getItemId();

        if (id == R.id.nav_scan) {
            showScanFragment();
        } else {
            for (Item item : items) {
                if (item.getId().equals(menuItem.getTooltipText().toString())) {
                    String address = item.getId();
                    showFragmentForDevice(address, item.getCaption());
                }
            }
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void gattConnected(final String address) {
        handler.post(new Runnable() {

            @Override
            public void run() {
                updateBeaconIcon(address, R.drawable.ic_phonelink);
            }
        });
    }

    @Override
    public void gattDisconnected(final String address) {
        handler.post(new Runnable() {

            @Override
            public void run() {
                updateBeaconIcon(address, R.drawable.ic_no_phonelink);
            }
        });
    }

    @Override
    public void onImmediateAlertAvailable(String address) {

    }

    @Override
    public void onRssi(String address, int rssi) {

    }

    @Override
    public void onBatteryLevel(String address, Integer valueOf) {

    }

    @Override
    public void onservicesDiscovered(String address) {

    }

}
