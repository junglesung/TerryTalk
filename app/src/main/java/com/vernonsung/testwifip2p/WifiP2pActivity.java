package com.vernonsung.testwifip2p;

import android.content.Context;
import android.content.IntentFilter;
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
    private enum WifiP2pState {
        FIRST,
        INITIALIZED
    }

    private enum WiFiP2pAction {
        NONE,
        ADD_LOCAL_SERVICE,  // WifiP2pManager.addLocalService()
        ADD_SERVICE_REQUEST,  // WifiP2pManager.addServiceRequest()
        CONNECT,  // WifiP2pManager.connect()
        DISCOVER_SERVICES,  // WifiP2pManager.discoverServices()
        REMOVE_SERVICE_REQUEST,  // WifiP2pManager.removeServiceRequest()
        REMOVE_SERVICE_REQUEST_TO_REQUEST_DNS_SD_TXT_RECORD  // WifiP2pManager.removeServiceRequest() and then WifiP2pManager.addServiceRequest()
    }

    // Wi-Fi Direct
    public static final String WIFI_P2P_DNS_SD_SERVICE_TYPE = "_test-wifi-p2p._ucp";
    private static final String WIFI_P2P_DNS_SD_SERVICE_DATA_IP = "ip";
    private static final String WIFI_P2P_DNS_SD_SERVICE_DATA_PORT = "port";
    private static final String LOG_TAG = "testtest";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pReceiver wifiP2pReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdServiceRequest;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdTxtRecordRequest;
    private WifiP2pDnsSdServiceInfo wifiP2pDnsSdServiceInfo;
    private WifiP2pState currentState = WifiP2pState.FIRST;
    private WiFiP2pAction lastAction = WiFiP2pAction.NONE;

    // UI
    private TextView textViewName;
    private Button buttonShout;
    private Button buttonSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

        // UI
        textViewName = (TextView)findViewById(R.id.textViewName);
        buttonShout = (Button)findViewById(R.id.buttonShout);
        buttonSearch = (Button)findViewById(R.id.buttonSearch);
        buttonShout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                announceService();
            }
        });
        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchServices();
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
        Log.d(LOG_TAG, "onDnsSdServiceAvailable() found device " + instanceName);
        Toast.makeText(this, "onDnsSdServiceAvailable() found device " + instanceName, Toast.LENGTH_SHORT).show();

        // Further request data
        wifiP2pDnsSdTxtRecordRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, WIFI_P2P_DNS_SD_SERVICE_TYPE);
        lastAction = WiFiP2pAction.REMOVE_SERVICE_REQUEST_TO_REQUEST_DNS_SD_TXT_RECORD;
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
        // TODO: Store this device and its MAC address
    }

    // implement WifiP2pManager.DnsSdTxtRecordListener
    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
        Log.d(LOG_TAG, "onDnsSdTxtRecordAvailable() found device " + fullDomainName);
        String ip = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_IP);
        String port = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT);
        Log.d(LOG_TAG, "Socket " + ip + ":" + port);
        Toast.makeText(this, "Socket " + ip + ":" + port, Toast.LENGTH_SHORT).show();

        // Finish. Stop discovering services
        lastAction = WiFiP2pAction.REMOVE_SERVICE_REQUEST;
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onSuccess() {
        switch (lastAction) {
            case NONE:
                break;
            case ADD_LOCAL_SERVICE:
                Log.d(LOG_TAG, "Service created");
                Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show();
                break;
            case ADD_SERVICE_REQUEST:
                lastAction = WiFiP2pAction.DISCOVER_SERVICES;
                wifiP2pManager.discoverServices(wifiP2pChannel, this);
                break;
            case CONNECT:
                break;
            case DISCOVER_SERVICES:
                Log.d(LOG_TAG, "Discovering services");
                Toast.makeText(this, R.string.discovering_services, Toast.LENGTH_SHORT).show();
                break;
            case REMOVE_SERVICE_REQUEST:
                Log.d(LOG_TAG, "Stop discovering services");
                Toast.makeText(this, R.string.stop_discovering_services, Toast.LENGTH_SHORT).show();
                break;
            case REMOVE_SERVICE_REQUEST_TO_REQUEST_DNS_SD_TXT_RECORD:
                lastAction = WiFiP2pAction.ADD_SERVICE_REQUEST;
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

        // Change state
        currentState = WifiP2pState.INITIALIZED;
        Log.d(LOG_TAG, "State -> INITIALIZED");
    }

    private void stopWifiP2p() {
        if (currentState != WifiP2pState.FIRST) {
            wifiP2pManager.cancelConnect(wifiP2pChannel, null);
            wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, null);
            if (wifiP2pDnsSdTxtRecordRequest != null) {
                wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, null);
            }
            wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, null);
            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, null);
            // Change state
            currentState = WifiP2pState.FIRST;
            Log.d(LOG_TAG, "State -> FIRST");
        }
    }

    // Step 2: Start to discover the devices running this APP
    // Next step is wifiP2pManager.discoverServices(wifiP2pChannel, this) in onSuccess()
    public void discoverNearbyServices() {
        lastAction = WiFiP2pAction.ADD_SERVICE_REQUEST;
        wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
    }

    // Announce this device is running this APP
    public void announceService() {
        lastAction = WiFiP2pAction.ADD_LOCAL_SERVICE;
        wifiP2pManager.addLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    public void searchServices() {
        discoverNearbyServices();
    }
}
