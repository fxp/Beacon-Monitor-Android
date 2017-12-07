package com.fengxiaoping.beacon.monitor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.estimote.cloud_plugin.common.SharedPreferencesProvider;
import com.estimote.coresdk.common.config.EstimoteSDK;
import com.estimote.coresdk.common.requirements.SystemRequirementsChecker;
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
    private TextView mStatusMessage;

    private String serverUrl = null;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_dashboard:
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Server URL");

                    final EditText input = new EditText(MainActivity.this);
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setText(serverUrl);
                    builder.setView(input);

                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            serverUrl = input.getText().toString();
                            mTextMessage.setText(serverUrl);
                            writeServerURL(MainActivity.this, serverUrl);
                            Toast.makeText(MainActivity.this, "Server URL:" + serverUrl + " saved", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();

                    return true;
            }
            return false;
        }

    };


    private BeaconManager beaconManager;

    private Socket socket = null;

    public void startMonitor(String url) throws URISyntaxException {
        if (socket != null) {
            stopMonitor();
        }
        IO.Options opts = new IO.Options();
        opts.forceNew = true;
        opts.reconnection = true;
        mStatusMessage.setText("connecting");
        socket = IO.socket(url, opts);
        beaconManager = new BeaconManager(getApplicationContext());
        beaconManager.setForegroundScanPeriod(1000, 0);
        beaconManager.setBackgroundScanPeriod(1000, 0);

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                    @Override
                    public void onServiceReady() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStatusMessage.setText("ble connected");
                            }
                        });
                        BeaconRegion region = new BeaconRegion(
                                "monitored region",
                                UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D"),
                                null, null);
                        beaconManager.startRanging(region);
                        beaconManager.startMonitoring(region);
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
                                for (Beacon b : list) {
                                    socket.emit("beacons_discovered", gson.toJson(b));
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
                                }
                            }
                        });

                        beaconManager.setNearableListener(new BeaconManager.NearableListener() {
                            @Override
                            public void onNearablesDiscovered(List<Nearable> nearables) {
                                Gson gson = new Gson();
                                for (Nearable n : nearables) {
                                    socket.emit("nearable_discovered", gson.toJson(n));
                                    Log.i(TAG, "nearable," + n);
                                }
                            }
                        });

                    }
                });

            }

        });
        socket.connect();
    }

    public void stopMonitor() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (beaconManager != null) {
            beaconManager.disconnect();
            beaconManager = null;
        }
        mStatusMessage.setText("stopped");
    }

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    public void writeServerURL(Context context, String url) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.server_url), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.server_url), serverUrl);
        editor.commit();
    }

    public void readServerURL(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.server_url), Context.MODE_PRIVATE);
        serverUrl = sharedPref.getString(getString(R.string.server_url), "http://192.168.0.101:3000");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SystemRequirementsChecker.checkWithDefaultDialogs(this);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSION_REQUEST_COARSE_LOCATION);

        readServerURL(this);
        mTextMessage = (TextView) findViewById(R.id.message);
        mStatusMessage = (TextView) findViewById(R.id.textView2);
        mTextMessage.setText(serverUrl);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        ((Button) findViewById(R.id.button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Toast.makeText(MainActivity.this, "start", Toast.LENGTH_SHORT).show();
                    startMonitor(serverUrl);
                } catch (URISyntaxException e) {
                    stopMonitor();
                }
            }
        });


        EstimoteSDK.initialize(this, getString(R.string.estimote_app_id), getString(R.string.estimote_app_key));
    }

}
