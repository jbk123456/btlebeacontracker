package com.github.btlebeacontracker;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class BeaconFragment extends Fragment implements BluetoothLEService.BluetoothServiceCallbacks, IServiceConnectionListener {

    private static final long TRACK_REMOTE_RSSI_DELAY_MILLIS = 1000L;
    private final Object READ_RSSI_CALLBACK_TOKEN = new Object();
    private String address;
    private String beaconName;

    private TextView tRssi;
    private TextView tSectionLabel;
    private TextView tBat;
    private EditText tBeaconName;
    private ToggleButton bConnected;
    private Button bAlert;
    private final Handler handler = new Handler();
    private boolean connected;
    private final BeaconFragment.BeaconCallbacks EMPTY_CALLBACKS = new BeaconFragment.BeaconCallbacks() {

        @Override
        public void updateBeaconItem(String id, String caption) {

        }

        @Override
        public BluetoothLEService getService() {
            return null;
        }
    };

    BeaconFragment init(String address, String beaconName) {
        this.address = address;
        this.beaconName = beaconName;
        return this;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            address = savedInstanceState.getString("address");
            beaconName = savedInstanceState.getString("beaconName");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        //Save the fragment's state here
        savedInstanceState.putString("address", address);
        savedInstanceState.putString("beaconName", beaconName);

    }

    private BeaconFragment.BeaconCallbacks getBeaconCallbacks() {
        BeaconCallbacks callbacks = (BeaconCallbacks) getActivity();

        return callbacks == null ? EMPTY_CALLBACKS : callbacks;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_beacon, container, false);
        tRssi = rootView.findViewById(R.id.rssi);
        tBat = rootView.findViewById(R.id.bat);
        tSectionLabel = rootView.findViewById(R.id.section_label);
        tSectionLabel.setText(address);
        tBeaconName = rootView.findViewById(R.id.beaconName);
        tBeaconName.setText(beaconName);
        tBeaconName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String val = s.toString().trim();
                if (!val.isEmpty()) {
                    beaconName = val;
                    getBeaconCallbacks().updateBeaconItem(address, beaconName);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        bConnected = rootView.findViewById(R.id.connected);
        bAlert = rootView.findViewById(R.id.alert);
        bAlert.setVisibility(View.GONE);
        bAlert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getBeaconCallbacks().getService() != null) {
                    getBeaconCallbacks().getService().immediateAlert(address, BluetoothLEService.HIGH_ALERT);
                }
            }
        });
        if (getBeaconCallbacks().getService() != null) {
            serviceConnected();
        }

        
        return rootView;

    }

    @Override
    public void onResume() {
        super.onResume();
        connected = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(READ_RSSI_CALLBACK_TOKEN);
        connected = false;

    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(READ_RSSI_CALLBACK_TOKEN);

        super.onDestroyView();
        if (getBeaconCallbacks().getService() != null) {
            getBeaconCallbacks().getService().unregisterClient(this);
        }
        connected = false;
        if (getBeaconCallbacks().getService() != null) {
            getBeaconCallbacks().getService().disconnect(address);
        }
    }

    private void setImmediateAlertAvailable() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                bAlert.setVisibility(View.VISIBLE);
            }

        });
    }

    private void setBeaconName(final String name) {
        handler.post(new Runnable() {
            @Override
            public void run() {

                tSectionLabel.setText(name);
                tBeaconName.setText(beaconName);

            }
        });
    }

    private void setConnected(final boolean flag) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                bConnected.setChecked(flag);
                bAlert.setEnabled(flag);

            }
        });
    }

    private void setRssiValue(final float value) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                bConnected.setChecked(true);
                tRssi.setText(String.valueOf(value));
            }
        });

    }

    private void setBatteryPercent(final float value) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                bConnected.setChecked(true);
                tBat.setText(String.valueOf(value));
            }
        });

    }


    @Override
    public void gattConnected(String address) {
        if (!address.equals(this.address)) {
            return;
        }
        setConnected(true);
    }

    @Override
    public void gattDisconnected(String address) {
        if (!address.equals(this.address)) {
            return;
        }
        setConnected(false);

    }

    @Override
    public void onImmediateAlertAvailable(String address) {
        if (!connected || !address.equals(this.address)) {
            return;
        }
        setImmediateAlertAvailable();

    }

    @Override
    public void onRssi(final String address, int rssi) {
        if (!connected || !address.equals(this.address)) {
            return;
        }

        setRssiValue(rssi);

        Runnable trackRemoteRssi = new Runnable() {
            public void run() {
                if (getBeaconCallbacks().getService() != null) {
                    getBeaconCallbacks().getService().readRemoteRssi(address);
                }
            }
        };

        handler.postDelayed(trackRemoteRssi, READ_RSSI_CALLBACK_TOKEN, TRACK_REMOTE_RSSI_DELAY_MILLIS);

    }

    @Override
    public void onBatteryLevel(String address, Integer level) {
        if (!connected || !address.equals(this.address)) {
            return;
        }
        setBatteryPercent(level);
    }

    @Override
    public void onservicesDiscovered(String address) {
        if (!connected || !address.equals(this.address)) {
            return;
        }

        setBeaconName(address);
        if (getBeaconCallbacks().getService() != null) {
            getBeaconCallbacks().getService().readRemoteRssi(address);
        }

    }

    @Override
    public void serviceConnected() {
        connected = true;
        getBeaconCallbacks().getService().registerClient(this);
        getBeaconCallbacks().getService().connect(address); // refresh
    }

    public interface BeaconCallbacks {
        void updateBeaconItem(String id, String caption);

        BluetoothLEService getService();
    }
}
