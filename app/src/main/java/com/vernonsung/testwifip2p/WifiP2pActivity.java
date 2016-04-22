package com.vernonsung.testwifip2p;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class WifiP2pActivity extends AppCompatActivity
        implements ServiceConnection {
    private static final String LOG_TAG = "testtest";

    // To show devices on the list
    private static final String MAP_ID_DEVICE_NAME = "deviceName";
    private static final String MAP_ID_STATUS = "status";
    private ArrayList<HashMap<String, String>> nearbyDevices;
    private SimpleAdapter listViewDevicesAdapter;

    // Broadcast receiver
    private WifiP2pActivityReceiver wifiP2pActivityReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private IntentFilter wifiP2pServiceIntentFilter;

    // Service binder
    private WifiP2pService wifiP2pService;

    // UI
    private TextView textViewName;
    private TextView textViewIp;
    private Button buttonShout;
    private Button buttonStop;
    private ListView listViewDevices;

    // Function objects
    private AdapterView.OnItemClickListener listViewDevicesOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String targetName = nearbyDevices.get(position).get(MAP_ID_DEVICE_NAME);
            serviceActionConnect(targetName);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

        // Customized
        initializeBroadcastReceiver();
        serviceActionStart();

        // UI
        textViewName = (TextView)findViewById(R.id.textViewName);
        textViewIp = (TextView)findViewById(R.id.textViewIp);
        buttonShout = (Button)findViewById(R.id.buttonShout);
        buttonStop = (Button)findViewById(R.id.buttonStop);
        listViewDevices = (ListView)findViewById(R.id.listViewDevices);
        textViewName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        buttonShout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceActionLocalService();
            }
        });
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceActionStop();
            }
        });
        listViewDevicesAdapter = new SimpleAdapter(this,
                                                   nearbyDevices,
                                                   android.R.layout.simple_list_item_2,
                                                   new String[] {MAP_ID_DEVICE_NAME, MAP_ID_STATUS},
                                                   new int[] {android.R.id.text1, android.R.id.text2});
        listViewDevices.setAdapter(listViewDevicesAdapter);
        listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Bind the service
        Intent intent = new Intent(this, WifiP2pService.class);
        bindService(intent, this, 0);
        // Receive intents related to Wi-Fi P2p
        registerReceiver(wifiP2pActivityReceiver, wifiP2pIntentFilter);
        // Receive intents from the service
        LocalBroadcastManager.getInstance(this).registerReceiver(wifiP2pActivityReceiver, wifiP2pServiceIntentFilter);
        Log.d(LOG_TAG, "Broadcast receiver registered");
    }

    @Override
    protected void onPause() {
        // Stop receiving intents related to Wi-Fi P2p
        unregisterReceiver(wifiP2pActivityReceiver);
        // Stop receiving intents from the service
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wifiP2pActivityReceiver);
        Log.d(LOG_TAG, "Broadcast receiver unregistered");
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        WifiP2pService.LocalBinder binder = (WifiP2pService.LocalBinder)service;
        wifiP2pService = binder.getService();
        // Update data from the service
        updateStateFromService();
        updateIpFromService();
        updateNearByDevicesFromService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        wifiP2pService = null;
        Log.d(LOG_TAG, "Service disconnected");
    }

    private void initializeBroadcastReceiver() {
        wifiP2pActivityReceiver = new WifiP2pActivityReceiver(this);
        // Filter the intent received by a broadcast receiver in order to sense Wi-Fi P2P status
        wifiP2pIntentFilter = new IntentFilter();
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);  // Receive peers update from WifiP2pService
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);  // Receive group clients from WifiP2pService
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);  // Not necessary

        // Filter the intent sent from WifiP2pService in order to react to service state change
        wifiP2pServiceIntentFilter = new IntentFilter();
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_NEARBY_DEVICES_ACTION);
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_STATE_ACTION);
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_IP_ACTION);
    }

    private void serviceActionStart() {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_PLAY);
        startService(intent);
    }

    private void serviceActionConnect(String targetName) {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_CONNECT);
        // Put target name into the intent
        intent.putExtra(WifiP2pService.INTENT_EXTRA_TARGET, targetName);
        startService(intent);
    }

    private void serviceActionLocalService() {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_LOCAL_SERVICE);
        startService(intent);
    }

    private void serviceActionStop() {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_STOP);
        startService(intent);
    }

    public void showDeviceName(String name) {
        textViewName.setText(name);
    }

    public void setIp(String ip) {
        textViewIp.setText(ip);
    }

    public void setState(WifiP2pService.WifiP2pState state) {
        switch (state) {
            case NON_INITIALIZED:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case INITIALIZE_WIFI_P2P:
                break;
            case INITIALIZED:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case DISCOVER_PEERS:
                break;
            case ADD_SERVICE_REQUEST:
                break;
            case DISCOVER_SERVICES:
                break;
            case SEARCHING:
                break;
            case REMOVE_SERVICE_REQUEST:
                break;
            case STOP_PEER_DISCOVERY:
                break;
            case SEARCH_STOPPED:
                break;
            case ADD_LOCAL_SERVICE:
                break;
            case SHOUT:
                buttonShout.setText(R.string.silent);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case UPDATE_CLIENT_LIST:
                break;
            case AUDIO_STREAM_SETUP_KING:
                break;
            case DISCOVER_PEERS_SHOUT:
                break;
            case REMOVE_LOCAL_SERVICE:
                break;
            case CLEAR_CLIENT_LIST:
                break;
            case AUDIO_STREAM_DISMISS_KING:
                break;
            case SILENT:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case STOP_PEER_DISCOVERY_SHOUT:
                break;
            case DISCOVER_PEERS_DATA:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case ADD_SERVICE_REQUEST_DATA:
                break;
            case DISCOVER_SERVICES_DATA:
                break;
            case DATA_REQUESTED:
                break;
            case REMOVE_SERVICE_REQUEST_DATA:
                break;
            case STOP_PEER_DISCOVERY_DATA:
                break;
            case DATA_STOPPED:
                break;
            case DISCOVER_PEERS_CONNECT:
                break;
            case CONNECT:
                break;
            case CONNECTING:
                break;
            case CANCEL_CONNECT:
                break;
            case DISCONNECTED:
                break;
            case AUDIO_STREAM_SETUP:
                break;
            case CONNECTED:
                break;
            case AUDIO_STREAM_DISMISS:
                break;
            case REMOVE_GROUP:
                break;
        }
    }

    public void updateNearByDevicesFromService() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        nearbyDevices = wifiP2pService.getNearbyDevices();
        listViewDevicesAdapter.notifyDataSetChanged();
    }

    private void updateIpFromService() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg;
        InetAddress ip = wifiP2pService.getWifiP2pDeviceIp();
        if (ip == null) {
            msg = "";
        } else {
            msg = ip.getHostAddress();
        }
        setIp(msg);
    }

    private void updateStateFromService() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        setState(wifiP2pService.getCurrentState());
    }
}
