package com.vernonsung.terrytalk;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
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
        UPDATE_IP, UPDATE_PORT, UPDATE_DEVICES, UPDATE_STATE
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

    // Permission request
    private static final int PERMISSION_REQUEST_SERVICE = 100;

    // Connect to target
    String targetName;
    int targetPort;

    // UI
    private TextView textViewName;
    private TextView textViewIp;
    private TextView textViewPort;
    private Button buttonRefresh;
    private Button buttonStop;
    private EditText editTextPort;
    private ListView listViewDevices;

    // Function objects
    private AdapterView.OnItemClickListener listViewDevicesOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            targetName = nearbyDevices.get(position).get(WifiP2pService.MAP_ID_DEVICE_NAME);
            try {
                targetPort = Integer.parseInt(editTextPort.getText().toString());
            } catch (NumberFormatException e) {
                Log.e(LOG_TAG, "User input port is not an integer.");
                Toast.makeText(getApplicationContext(), R.string.please_input_the_right_port, Toast.LENGTH_SHORT).show();
                return;
            }
            serviceActionConnect();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

        // UI
        textViewName = (TextView)findViewById(R.id.textViewName);
        textViewIp = (TextView)findViewById(R.id.textViewIp);
        textViewPort = (TextView)findViewById(R.id.textViewPort);
        buttonRefresh = (Button)findViewById(R.id.buttonRefresh);
        buttonStop = (Button)findViewById(R.id.buttonStop);
        editTextPort = (EditText)findViewById(R.id.editTextPort);
        listViewDevices = (ListView)findViewById(R.id.listViewDevices);
        textViewName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceActionRefresh();
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
        checkPermissionToStartService();
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

    // Permission check and request for Android 6+ -----------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_SERVICE:
                localServicePermissionHandler(grantResults);
                break;
        }
    }

    private void localServicePermissionHandler(int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length == 0) {
            return;
        }
        // Check whether every permission is granted
        for (int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        // Check all permissions again
        checkPermissionToStartService();
    }

    private void checkPermissionToStartService() {
        // List all permissions to check for grants
        String[] permissions = new String[] {Manifest.permission.RECORD_AUDIO};
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        // Check every permissions
        for (String s : permissions) {
            if (ContextCompat.checkSelfPermission(this, s) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(s);
            }
        }

        // There are permission grants to request
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_SERVICE);
            // Async call back onRequestPermissionsResult() will be called
            return;
        }

        // Finally all permission are granted
        serviceActionStart();
    }
    // Permission check and request for Android 6+ -----------------------------------------------

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
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_PORT_ACTION);
    }

    private void serviceActionStart() {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_START);
        startService(intent);
        // Update data from the service
        updateStateFromService();
        updateIpFromService();
        updatePortFromService();
        updateNearByDevicesFromService();
    }

    private void serviceActionConnect() {
        if (targetName == null || targetName.isEmpty()) {
            Log.e(LOG_TAG, "No target name");
            return;
        }
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_CONNECT);
        // Put target name into the intent
        intent.putExtra(WifiP2pService.INTENT_EXTRA_TARGET, targetName);
        intent.putExtra(WifiP2pService.INTENT_EXTRA_PORT, targetPort);
        startService(intent);
    }

    private void serviceActionRefresh() {
        Intent intent = new Intent(this, WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_REFRESH);
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

    public void setPort(int port) {
        if (port == 0) {
            textViewPort.setText("");
        } else {
            String s = ":" + String.valueOf(port);
            textViewPort.setText(s);
        }
    }

    public void setState(WifiP2pService.WifiP2pState state) {
        switch (state) {
            case INITIALIZING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case SEARCHING:
            case IDLE:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case REJECTING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
                break;
            case SERVER:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case SERVER_DISCONNECTING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                break;
            case CONNECTING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case CANCELING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                break;
            case RECONNECTING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                break;
            case REGISTERING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                break;
            case CONNECTED:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(true);
                listViewDevices.setOnItemClickListener(null);
                break;
            case DISCONNECTING:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(true);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                break;
            case STOPPING:
            case STOPPED:
                buttonRefresh.setText(R.string.refresh);
                buttonRefresh.setEnabled(false);
                buttonStop.setText(R.string.stop);
                buttonStop.setEnabled(false);
                listViewDevices.setOnItemClickListener(null);
                // Leave APP
                finish();
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
        ArrayList<HashMap<String, String>> clients = wifiP2pService.getClients();
        // Merge clients status to nearbyDevices
        for (HashMap<String, String> client : clients) {
            String name = client.get(WifiP2pService.MAP_ID_DEVICE_NAME);
            boolean found = false;
            for (HashMap<String, String> device : nearbyDevices) {
                if (name.equals(device.get(WifiP2pService.MAP_ID_DEVICE_NAME))) {
                    String status = device.get(WifiP2pService.MAP_ID_STATUS);
                    if (status.compareToIgnoreCase("connected") != 0) {
                        Log.d(LOG_TAG, "Replace status " + status + " by CoNNeCTeD");
                        device.put(WifiP2pService.MAP_ID_STATUS, "CoNNeCTeD");
                    }
                    found = true;
                    break;
                }
            }
            // Add client if it's not in the nearbyDevices yet
            if (!found) {
                nearbyDevices.add(0, client);
            }
        }
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
        InetAddress ip = wifiP2pService.getWifiP2pLocalDeviceIp();
        if (ip == null) {
            msg = "";
        } else {
            msg = ip.getHostAddress();
        }
        setIp(msg);
    }

    private void updatePortFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        int port = wifiP2pService.getLocalRegistrationPort();
        setPort(port);
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
                case UPDATE_PORT:
                    updatePortFromServiceTaskHandler();
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

    private void updatePortFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_PORT);
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