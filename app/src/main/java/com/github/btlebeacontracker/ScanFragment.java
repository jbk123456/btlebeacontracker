package com.github.btlebeacontracker;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;

import com.github.btlebeacontracker.widget.Item;
import com.github.btlebeacontracker.widget.ItemListAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class ScanFragment extends ListFragment
        implements OnClickListener, ScanHelper.ScanObserver, IServiceConnectionListener {

    private List<Item> data;
    private ScanHelper scanHelper;
    private DeviceHelper deviceHelper;
    private boolean connected;

    private final ScanCallbacks EMPTY_CALLBACKS = new ScanCallbacks() {
        @Override
        public void addBeaconItem(Item item) {

        }

        @Override
        public void removeBeaconItem(Item item) {

        }

        @Override
        public void showFragmentForDevice(String id, String caption) {

        }

        @Override
        public void showScanFragment() {

        }

        @Override
        public BluetoothAdapter getBluetoothAdapter() {
            return null;
        }

        @Override
        public BluetoothLEService getService() {
            return null;
        }

        @Override
        public void setTitle(String title) {

        }
    };

    private ScanCallbacks getScanCallbacks() {
        ScanCallbacks callbacks = (ScanCallbacks) getActivity();
        return callbacks == null ? EMPTY_CALLBACKS : callbacks;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        scanHelper = new ScanHelper(getContext());
        deviceHelper = new DeviceHelper(getContext());

        View rootView = inflater.inflate(R.layout.fragment_scan, container, false);

        Button scanStart = rootView.findViewById(R.id.scanStart);
        Button ok = rootView.findViewById(R.id.ok);
        scanStart.setOnClickListener(this);
        ok.setOnClickListener(this);

        showDevices(scanHelper.getDevices());
        connected = true;

        getScanCallbacks().setTitle(getResources().getString(R.string.overview));

        return rootView;
    }

    @Override
    public void onDestroyView() {
        connected = false;
        super.onDestroyView();
    }


    private void showDevices(Set<Item> devices) {

        data = new ArrayList<>();
        data.addAll(devices);

        ItemListAdapter adapter = new ItemListAdapter(getContext(), data);
        setListAdapter(adapter);
    }

    private void finishView() {
        final SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        deviceHelper.removeAllPersistedDevices();
        Item hasDevices = null;

        final int checkedItemsCount = checkedItems.size();
        for (int i = 0; i < checkedItemsCount; ++i) {
            final int position = checkedItems.keyAt(i);
            final boolean isChecked = checkedItems.valueAt(i);

            Item item = data.get(position);
            deviceHelper.addPersistedDevice(item.getId(), item.getCaption(), isChecked);
            if (isChecked && !item.getCaption().trim().isEmpty()) {
                hasDevices = item;
            }
        }


        if (getScanCallbacks().getService() != null) {
            getScanCallbacks().getService().connect();
        }

        for (int i = 0; i < checkedItemsCount; ++i) {
            final int position = checkedItems.keyAt(i);
            final boolean isChecked = checkedItems.valueAt(i);

            Item item = data.get(position);
            if (isChecked && !item.getCaption().trim().isEmpty()) {
                hasDevices = item;
                getScanCallbacks().addBeaconItem(item);
            } else {
                getScanCallbacks().removeBeaconItem(item);
            }
        }

        removeNonSelectedFromDatabase(checkedItems, checkedItemsCount);
        if (hasDevices != null) {
            getScanCallbacks().showFragmentForDevice(hasDevices.getId(), hasDevices.getCaption());
        } else {
            getScanCallbacks().showScanFragment();
        }
    }

    private void removeNonSelectedFromDatabase(SparseBooleanArray checkedItems, int checkedItemsCount) {
        deviceHelper.removeAllPersistedDevices();
        for (int i = 0; i < checkedItemsCount; ++i) {
            final int position = checkedItems.keyAt(i);
            final boolean isChecked = checkedItems.valueAt(i);

            Item item = data.get(position);
            if (isChecked) {
                deviceHelper.addPersistedDevice(item.getId(), item.getCaption(), isChecked);
            }
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ok:
                finishView();
                break;
            case R.id.scanStart:
                if (getScanCallbacks().getService() != null && getScanCallbacks().getBluetoothAdapter() != null) {
                    scanHelper.scan(getScanCallbacks().getBluetoothAdapter(), this);
                }
                break;
        }
    }

    @Override
    public void finishScan(Set<Item> devices) {
        if (connected) {
            showDevices(devices);
        }
    }

    @Override
    public void serviceConnected() {
        // nothing to do
    }

    public interface ScanCallbacks {
        void addBeaconItem(Item item);

        void removeBeaconItem(Item item);

        void showFragmentForDevice(String id, String caption);

        void showScanFragment();

        BluetoothAdapter getBluetoothAdapter();

        BluetoothLEService getService();

        void setTitle(String title);
    }

}
