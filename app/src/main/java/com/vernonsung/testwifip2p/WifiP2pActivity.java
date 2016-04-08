package com.vernonsung.testwifip2p;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WifiP2pActivity extends AppCompatActivity
        implements WifiP2pManager.ActionListener,
                   WifiP2pManager.DnsSdServiceResponseListener,
                   WifiP2pManager.DnsSdTxtRecordListener {
    private enum WifiP2pAction {
        NONE,
        // Shout and silent
        ADD_LOCAL_SERVICE,  // WifiP2pManager.addLocalService()
        REMOVE_LOCAL_SERVICE,  // WifiP2pManager.removeLocalService()
        // Search services and stop searching
        ADD_SERVICE_REQUEST,  // WifiP2pManager.addServiceRequest()
        DISCOVER_SERVICES,  // WifiP2pManager.discoverServices()
        REMOVE_SERVICE_REQUEST,  // WifiP2pManager.removeServiceRequest()
        STOP_PEER_DISCOVERY,  // WifiP2pManager.stopPeerDiscovery()
        // Request data and stop requesting data
        DISCOVER_PEERS_DATA,  // WifiP2pManager.discoverPeers()
        ADD_SERVICE_REQUEST_DATA,  // WifiP2pManager.addServiceRequest() for data
        DISCOVER_SERVICES_DATA,  // WifiP2pManager.discoverServices() for data
        REMOVE_SERVICE_REQUEST_DATA,  // WifiP2pManager.removeServiceRequest() for data
        STOP_PEER_DISCOVERY_DATA,  // WifiP2pManager.stopPeerDiscovery() for data
        // Other
        CONNECT,  // WifiP2pManager.connect()
        REMOVE_SERVICE_REQUEST_TO_REQUEST_DNS_SD_TXT_RECORD  // WifiP2pManager.removeServiceRequest() and then WifiP2pManager.addServiceRequest()
    }

    private enum WifiP2pInitialStage {
        NON_INITIALIZED,
        INITIALIZED
    }
    private enum WifiP2pShoutStage {
        SHOUT,
        SILENT
    }
    private enum WifiP2pSearchStage {
        SEARCHING,
        SEARCH_STOPPED
    }
    private enum WifiP2pDataStage {
        DATA_REQUESTED,
        DATA_STOPPED
    }

    // Wi-Fi Direct
    public static final String WIFI_P2P_DNS_SD_SERVICE_TYPE = "_test-wifi-p2p._udp";
    private static final String WIFI_P2P_DNS_SD_SERVICE_DATA_IP = "ip";
    private static final String WIFI_P2P_DNS_SD_SERVICE_DATA_PORT = "port";
    private static final String LOG_TAG = "testtest";
    private static final String WIFI_LOCK = "wifiLock";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pReceiver wifiP2pReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdServiceRequest;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdTxtRecordRequest;
    private WifiP2pDnsSdServiceInfo wifiP2pDnsSdServiceInfo;
    private WifiP2pAction lastAction = WifiP2pAction.NONE;
    private WifiP2pInitialStage wifiP2pInitialStage = WifiP2pInitialStage.NON_INITIALIZED;
    private WifiP2pShoutStage wifiP2pShoutStage = WifiP2pShoutStage.SILENT;
    private WifiP2pSearchStage wifiP2pSearchStage = WifiP2pSearchStage.SEARCH_STOPPED;
    private WifiP2pDataStage wifiP2pDataStage = WifiP2pDataStage.DATA_STOPPED;
    private String targetName;
    private WifiManager.WifiLock wifiLock = null;

    // UI
    private TextView textViewName;
    private Button buttonShout;
    private Button buttonSearch;
    private Button buttonData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

        // UI
        textViewName = (TextView)findViewById(R.id.textViewName);
        buttonShout = (Button)findViewById(R.id.buttonShout);
        buttonSearch = (Button)findViewById(R.id.buttonSearch);
        buttonData = (Button)findViewById(R.id.buttonData);
        buttonShout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                localService();
            }
        });
        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nearbyService();
            }
        });
        buttonData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceData();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        initialWifiP2p();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Receive intents related to Wi-Fi P2p
        registerReceiver(wifiP2pReceiver, wifiP2pIntentFilter);
        Log.d(LOG_TAG, "Broadcast receiver registered");
    }

    @Override
    protected void onPause() {
        // Stop receiving intents related to Wi-Fi P2p
        unregisterReceiver(wifiP2pReceiver);
        Log.d(LOG_TAG, "Broadcast receiver unregistered");
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopWifiP2p();
        super.onStop();
    }

    // implement WifiP2pManager.DnsSdServiceResponseListener
    @Override
    public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
        targetName = instanceName;
        buttonData.setEnabled(true);
        Log.d(LOG_TAG, "Found device " + instanceName);
        Toast.makeText(this, "Found device " + instanceName, Toast.LENGTH_SHORT).show();
    }

    // implement WifiP2pManager.DnsSdTxtRecordListener
    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
        Log.d(LOG_TAG, "onDnsSdTxtRecordAvailable() found device " + fullDomainName);
        String ip = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_IP);
        String port = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT);
        Log.d(LOG_TAG, "Socket " + ip + ":" + port);
        Toast.makeText(this, "Socket " + ip + ":" + port, Toast.LENGTH_SHORT).show();
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onSuccess() {
        switch (lastAction) {
            case NONE:
                break;
            // Shout and silent--------------------------------------------------------------------
            case ADD_LOCAL_SERVICE:
                wifiP2pShoutStage = WifiP2pShoutStage.SHOUT;
                buttonShout.setText(R.string.silent);
                Log.d(LOG_TAG, "Stage -> SHOUT");
                Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show();
                break;
            case REMOVE_LOCAL_SERVICE:
                wifiP2pShoutStage = WifiP2pShoutStage.SILENT;
                buttonShout.setText(R.string.shout);
                Log.d(LOG_TAG, "Stage -> SILENT");
                Toast.makeText(this, R.string.service_removed, Toast.LENGTH_SHORT).show();
                break;
            // Search services and stop searching--------------------------------------------------
            case ADD_SERVICE_REQUEST:
                lastAction = WifiP2pAction.DISCOVER_SERVICES;
                wifiP2pManager.discoverServices(wifiP2pChannel, this);
                break;
            case DISCOVER_SERVICES:
                wifiP2pSearchStage = WifiP2pSearchStage.SEARCHING;
                buttonSearch.setText(R.string.stop);
                Log.d(LOG_TAG, "Stage -> SEARCHING");
                Toast.makeText(this, R.string.discovering_services, Toast.LENGTH_SHORT).show();
                break;
            case REMOVE_SERVICE_REQUEST:
                Log.d(LOG_TAG, "Service request removed");
                lastAction = WifiP2pAction.STOP_PEER_DISCOVERY;
                wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
                break;
            case STOP_PEER_DISCOVERY:
                wifiP2pSearchStage = WifiP2pSearchStage.SEARCH_STOPPED;
                buttonSearch.setText(R.string.search);
                Log.d(LOG_TAG, "Stage -> SEARCH_STOPPED");
                Toast.makeText(this, R.string.stop_discovering_services, Toast.LENGTH_SHORT).show();
                break;
            // Request data and stop requesting data-----------------------------------------------
            case DISCOVER_PEERS_DATA:
                wifiP2pDnsSdTxtRecordRequest = WifiP2pDnsSdServiceRequest.newInstance(targetName, WIFI_P2P_DNS_SD_SERVICE_TYPE);
                lastAction = WifiP2pAction.ADD_SERVICE_REQUEST_DATA;
                wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
                break;
            case ADD_SERVICE_REQUEST_DATA:
                lastAction = WifiP2pAction.DISCOVER_SERVICES_DATA;
                wifiP2pManager.discoverServices(wifiP2pChannel, this);
                break;
            case DISCOVER_SERVICES_DATA:
                wifiP2pDataStage = WifiP2pDataStage.DATA_REQUESTED;
                buttonData.setText(R.string.stop);
                Log.d(LOG_TAG, "Stage -> DATA_REQUESTED");
                Toast.makeText(this, R.string.requesting_service_data, Toast.LENGTH_SHORT).show();
                break;
            case REMOVE_SERVICE_REQUEST_DATA:
                Log.d(LOG_TAG, "Data request removed");
                lastAction = WifiP2pAction.STOP_PEER_DISCOVERY_DATA;
                wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
                break;
            case STOP_PEER_DISCOVERY_DATA:
                wifiP2pDataStage = WifiP2pDataStage.DATA_STOPPED;
                buttonData.setText(R.string.data);
                Log.d(LOG_TAG, "Stage -> DATA_STOPPED");
                Toast.makeText(this, R.string.stop_requesting_data, Toast.LENGTH_SHORT).show();
                break;
            // Other-------------------------------------------------------------------------------
            case CONNECT:
                break;
            case REMOVE_SERVICE_REQUEST_TO_REQUEST_DNS_SD_TXT_RECORD:
                lastAction = WifiP2pAction.ADD_SERVICE_REQUEST;
                wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
                break;
        }
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onFailure(int reason) {
        String msg = getActionFailReason(reason);
        Log.e(LOG_TAG, msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // Translate the reason why a Wi-Fi P2P command failed
    private String getActionFailReason(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return getString(R.string.wifi_p2p_unsupported);
            case WifiP2pManager.BUSY:
                return getString(R.string.wifi_p2p_busy);
            case WifiP2pManager.ERROR:
                return getString(R.string.wifi_p2p_error);
            case WifiP2pManager.NO_SERVICE_REQUESTS:
                return getString(R.string.wifi_p2p_no_service_request);
            default:
                return getString(R.string.this_is_a_bug);
        }
    }

    // Step 1: Initial Wi-Fi P2P
    private void initialWifiP2p() {
        wifiP2pManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
        wifiP2pReceiver = new WifiP2pReceiver(wifiP2pManager, wifiP2pChannel, this);
        // Filter the intent received by WifiP2pReceiver in order to sense Wi-Fi P2P status
        wifiP2pIntentFilter = new IntentFilter();
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        // The request to discovery nearby devices running this APP
        wifiP2pDnsSdServiceRequest = WifiP2pDnsSdServiceRequest.newInstance(WIFI_P2P_DNS_SD_SERVICE_TYPE);
        // Response the discovery requests from nearby devices running this APP
        wifiP2pManager.setDnsSdResponseListeners(wifiP2pChannel, this, this);

        // The service information representing this APP------------------------------------------
        // Random a socket. Use real socket in the future.
        Random random = new Random();
        int random255 = random.nextInt(254) + 1;
        int random65535 = random.nextInt(65534) + 1;
        InetSocketAddress localSocket = new InetSocketAddress("192.168.0." + String.valueOf(random255), random65535);
        // Put socket information into Wi-Fi P2P service info
        Map<String, String> data = new HashMap<>();
        data.put(WIFI_P2P_DNS_SD_SERVICE_DATA_IP, localSocket.getAddress().getHostAddress());
        data.put(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT, String.valueOf(localSocket.getPort()));
        // Random a name
        String name = "_test" + String.valueOf(random.nextInt(10));
        // Create a service with the name
        wifiP2pDnsSdServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(name, WIFI_P2P_DNS_SD_SERVICE_TYPE, data);
        // The service information representing this APP------------------------------------------

        // Change UI
        textViewName.setText(name);

        // To ensure Wi-Fi is ON
        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
        wifiLock.acquire();

        // Change state
        wifiP2pInitialStage = WifiP2pInitialStage.INITIALIZED;
        Log.d(LOG_TAG, "Stage -> INITIALIZED");
    }

    private void stopWifiP2p() {
        if (wifiP2pInitialStage != WifiP2pInitialStage.NON_INITIALIZED) {
            if (wifiLock != null) {
                wifiLock.release();
            }
            wifiP2pManager.cancelConnect(wifiP2pChannel, null);
            wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, null);
            if (wifiP2pDnsSdTxtRecordRequest != null) {
                wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, null);
            }
            wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, null);
            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, null);
            // Change state
            wifiP2pShoutStage = WifiP2pShoutStage.SILENT;
            wifiP2pSearchStage = WifiP2pSearchStage.SEARCH_STOPPED;
            wifiP2pDataStage = WifiP2pDataStage.DATA_STOPPED;
            wifiP2pInitialStage = WifiP2pInitialStage.NON_INITIALIZED;
            Log.d(LOG_TAG, "State -> NON_INITIALIZED");
            buttonShout.setText(R.string.shout);
            buttonSearch.setText(R.string.search);
            buttonData.setText(R.string.data);
            buttonData.setEnabled(false);
        }
    }

    // Announce this device is running this APP
    public void announceService() {
        lastAction = WifiP2pAction.ADD_LOCAL_SERVICE;
        wifiP2pManager.addLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    // Hide this device is running this APP
    public void hideService() {
        lastAction = WifiP2pAction.REMOVE_LOCAL_SERVICE;
        wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    public void localService() {
        switch (wifiP2pShoutStage) {
            case SHOUT:
                hideService();
                break;
            case SILENT:
                announceService();
                break;
        }
    }

    // Step 2: Start to discover the devices running this APP
    // Next step is "Search services" part in onSuccess()
    public void discoverNearbyServices() {
        lastAction = WifiP2pAction.ADD_SERVICE_REQUEST;
        wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
    }

    // Stop discovering nearby devices running this APP
    // Next step is "Stop searching" part in onSuccess()
    public void stopDiscoverNearbySearvices() {
        lastAction = WifiP2pAction.REMOVE_SERVICE_REQUEST;
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
    }

    public void nearbyService() {
        switch (wifiP2pSearchStage) {
            case SEARCHING:
                stopDiscoverNearbySearvices();
                break;
            case SEARCH_STOPPED:
                discoverNearbyServices();
                break;
        }
    }

    // Step 3: Start to request specify device's data
    // Next step is "Request data" part in onSuccess()
    public void requestData() {
        lastAction = WifiP2pAction.DISCOVER_PEERS_DATA;
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
    }

    // Stop requesting data
    // Next stop is "Stop requesting data" part in onSuccess()
    public void stopRequestingData() {
        lastAction = WifiP2pAction.REMOVE_SERVICE_REQUEST_DATA;
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
    }

    public void serviceData() {
        switch (wifiP2pDataStage) {
            case DATA_REQUESTED:
                stopRequestingData();
                break;
            case DATA_STOPPED:
                requestData();
                break;
        }
    }
}
