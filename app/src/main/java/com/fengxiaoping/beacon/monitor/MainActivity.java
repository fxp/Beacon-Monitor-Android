package com.fengxiaoping.beacon.monitor;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import com.estimote.coresdk.common.config.EstimoteSDK;
import com.estimote.coresdk.observation.region.RegionUtils;
import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
import com.estimote.coresdk.recognition.packets.Beacon;
import com.estimote.coresdk.recognition.packets.EstimoteTelemetry;
import com.estimote.coresdk.recognition.packets.Nearable;
import com.estimote.coresdk.service.BeaconManager;
import com.estimote.sdk.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "Monitor";

    private TextView mTextMessage;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }

    };

    private BeaconManager beaconManager;

    public String getBeaconId(Beacon beacon) {
        return "" + beacon.getProximityUUID() + beacon.getMajor() + beacon.getMinor();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        final Socket socket;
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            socket = IO.socket("http://192.168.81.121:3000", opts);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
//                    socket.emit("foo", "hi");
//                    socket.disconnect();

                    beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                        @Override
                        public void onServiceReady() {
//                beaconManager.startMonitoring();
                            beaconManager.startRanging(new BeaconRegion(
                                    "monitored region",
                                    UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D"),
                                    null, null));
                            beaconManager.startMonitoring(new BeaconRegion(
                                    "monitored region",
                                    UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D"),
                                    null, null));
                            beaconManager.startTelemetryDiscovery();
                            beaconManager.startNearableDiscovery();

                            beaconManager.setMonitoringListener(new BeaconManager.BeaconMonitoringListener() {
                                @Override
                                public void onEnteredRegion(BeaconRegion beaconRegion, List<Beacon> beacons) {
                                    Log.i(TAG, "onEnteredRegion," + beacons.toString());
                                }

                                @Override
                                public void onExitedRegion(BeaconRegion beaconRegion) {
                                    Log.i(TAG, "onExitedRegion," + beaconRegion.toString());
                                }
                            });

                            beaconManager.setRangingListener(new BeaconManager.BeaconRangingListener() {

                                Gson gson = new Gson();

                                @Override
                                public void onBeaconsDiscovered(BeaconRegion region, List<Beacon> list) {
//                        Log.i(TAG, "beacon," + list.size());
                                    for (Beacon b : list) {
//                                        socket.emit("beacons_discovered", gson.toJson(b));
                                        Log.i(TAG, "beacon," + b.getMajor() + "," + RegionUtils.computeAccuracy(b));
                                    }

                                }

                            });

                            beaconManager.setTelemetryListener(new BeaconManager.TelemetryListener() {
                                Gson gson = new Gson();

                                @Override
                                public void onTelemetriesFound(List<EstimoteTelemetry> telemetries) {
                                    for (EstimoteTelemetry tlm : telemetries) {
                                        Log.d("TELEMETRY", "minor: " + tlm.deviceId +
                                                ",info," + tlm);
                                        JsonObject obj = gson.toJsonTree(tlm).getAsJsonObject();
                                        obj.addProperty("timestamp", tlm.timestamp.getTime());
                                        socket.emit("telemetries_found", obj);
//                                    ", temperature: " + tlm.temperature + " °C");
                                    }
                                }
                            });

                            beaconManager.setNearableListener(new BeaconManager.NearableListener() {
                                @Override
                                public void onNearablesDiscovered(List<Nearable> nearables) {
                                    for (Nearable n : nearables) {
                                        Log.i(TAG, "nearable," + n);
                                    }
                                }
                            });

                        }
                    });

                }

            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


        EstimoteSDK.initialize(this, "app_0ctzc7z5yt", "45bcef1e2225b5723b8249063bef27aa");
        beaconManager = new BeaconManager(getApplicationContext());
        beaconManager.setForegroundScanPeriod(1000, 0);
        beaconManager.setBackgroundScanPeriod(1000, 0);


    }

}
