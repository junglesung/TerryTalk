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
import java.util.LinkedList;

public class WifiP2pActivity extends AppCompatActivity
        implements ServiceConnection {
    // Schedule task through handler to call the service's methods
    private enum ServiceTask {
        UPDATE_IP, UPDATE_DEVICES, UPDATE_STATE
    }

    private static final String LOG_TAG = "testtest";

    // To show devices on the list
    private ArrayList<HashMap<String, String>> nearbyDevices = new ArrayList<>();
    private SimpleAdapter listViewDevicesAdapter;

    // Broadcast receiver
    private WifiP2pActivityReceiver wifiP2pActivityReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private IntentFilter wifiP2pServiceIntentFilter;

    // Bind to service to get information
    private WifiP2pService wifiP2pService;
    private LinkedList<ServiceTask> serviceTaskQueue = new LinkedList<>();

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
            String targetName = nearbyDevices.get(position).get(WifiP2pService.MAP_ID_DEVICE_NAME);
            serviceActionConnect(targetName);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

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
                                                   new String[] {WifiP2pService.MAP_ID_DEVICE_NAME, WifiP2pService.MAP_ID_STATUS},
                                                   new int[] {android.R.id.text1, android.R.id.text2});
        listViewDevices.setAdapter(listViewDevicesAdapter);
        listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);

        // Customized
        initializeBroadcastReceiver();
        // Start the main service if it's not started yet
        serviceActionStart();
        // Update data from the service
        updateStateFromService();
        updateIpFromService();
        updateNearByDevicesFromService();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    // Interface ServiceConnection ---------------------------------------------------------------
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(LOG_TAG, "Service bound");
        WifiP2pService.LocalBinder binder = (WifiP2pService.LocalBinder)service;
        wifiP2pService = binder.getService();
        doServiceTask();
    }

    // It's only called when the service accidentally
    @Override
    public void onServiceDisconnected(ComponentName name) {
        wifiP2pService = null;
        Log.d(LOG_TAG, "Service disconnected unexpectedly");
    }
    // Interface ServiceConnection ---------------------------------------------------------------

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
            case INITIALIZING:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case INITIALIZED:
            case DISCOVER_PEERS:
            case ADD_SERVICE_REQUEST:
            case DISCOVER_SERVICES:
            case SEARCHING:
            case REMOVE_SERVICE_REQUEST:
            case STOP_PEER_DISCOVERY:
            case SEARCH_STOPPED:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case ADD_LOCAL_SERVICE:
            case SHOUT:
            case UPDATE_CLIENT_LIST:
            case AUDIO_STREAM_SETUP_KING:
            case DISCOVER_PEERS_SHOUT:
                buttonShout.setText(R.string.silent);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case REMOVE_LOCAL_SERVICE:
            case CLEAR_CLIENT_LIST:
            case AUDIO_STREAM_DISMISS_KING:
            case SILENT:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case STOP_PEER_DISCOVERY_SHOUT:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case DISCOVER_PEERS_DATA:
            case ADD_SERVICE_REQUEST_DATA:
            case DISCOVER_SERVICES_DATA:
            case DATA_REQUESTED:
            case REMOVE_SERVICE_REQUEST_DATA:
            case STOP_PEER_DISCOVERY_DATA:
            case DATA_STOPPED:
            case DISCOVER_PEERS_CONNECT:
            case CONNECT:
            case CONNECTING:
            case CANCEL_CONNECT:
            case DISCONNECTED:
            case STOP_PEER_DISCOVERY_CONNECT:
            case AUDIO_STREAM_SETUP:
            case CONNECTED:
            case AUDIO_STREAM_DISMISS:
            case REMOVE_GROUP:
                buttonShout.setText(R.string.shout);
                buttonShout.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
        }
    }

    private void updateNearByDevicesFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        nearbyDevices.clear();
        nearbyDevices.addAll(wifiP2pService.getNearbyDevices());
        Log.d(LOG_TAG, "Activity has " + nearbyDevices.size() + " devices now");
        listViewDevicesAdapter.notifyDataSetChanged();
    }

    private void updateIpFromServiceTaskHandler() {
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

    private void updateStateFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        setState(wifiP2pService.getCurrentState());
    }

    private void doServiceTask() {
        while (!serviceTaskQueue.isEmpty()) {
            ServiceTask task = serviceTaskQueue.pop();
            switch (task) {
                case UPDATE_IP:
                    updateIpFromServiceTaskHandler();
                    break;
                case UPDATE_DEVICES:
                    updateNearByDevicesFromServiceTaskHandler();
                    break;
                case UPDATE_STATE:
                    updateStateFromServiceTaskHandler();
                    break;
            }
        }
        unbindWifiP2pService();
    }

    public void updateNearByDevicesFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_DEVICES);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updateIpFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_IP);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updateStateFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_STATE);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void bindWifiP2pService() {
        Log.d(LOG_TAG, "I'm going to bind the service");
        bindService(new Intent(this, WifiP2pService.class), this, 0);
    }

    private void unbindWifiP2pService() {
        unbindService(this);
        wifiP2pService = null;
        Log.d(LOG_TAG, "Service unbound");
    }
}