package com.vernonsung.testwifip2p;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WifiP2pActivity extends AppCompatActivity
        implements WifiP2pManager.ActionListener,
                   WifiP2pManager.DnsSdServiceResponseListener,
                   WifiP2pManager.DnsSdTxtRecordListener,
                   Handler.Callback {

    private enum WifiP2pState {
        NON_INITIALIZED, INITIALIZE_WIFI_P2P, INITIALIZED,
        DISCOVER_PEERS, ADD_SERVICE_REQUEST, DISCOVER_SERVICES, SEARCHING, REMOVE_SERVICE_REQUEST, STOP_PEER_DISCOVERY, SEARCH_STOPPED,
        ADD_LOCAL_SERVICE, SHOUT, REMOVE_LOCAL_SERVICE, SILENT,
        DISCOVER_PEERS_DATA, ADD_SERVICE_REQUEST_DATA, DISCOVER_SERVICES_DATA, DATA_REQUESTED,
        REMOVE_SERVICE_REQUEST_DATA, STOP_PEER_DISCOVERY_DATA, DATA_STOPPED,
        CONNECT, CONNECTING, CANCEL_CONNECT, DISCONNECTED,
        AUDIO_STREAM_SETUP, CONNECTED, AUDIO_STEAM_DISMISS, REMOVE_GROUP
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
    private String targetName;
    private String targetMac;
    private WifiManager.WifiLock wifiLock = null;
    // Nearby devices list -----------------------------------------------------------------------
    private ArrayList<String> nearbyDevices;
    private ArrayAdapter<String> listViewDevicesAdapter;
    private Handler retryHandler;
    private static final int HANDLER_WHAT = 12;
    private static final int HANDLER_DELAY_MS = 30000;
    // Finite state machine ----------------------------------------------------------------------
    private WifiP2pState targetState = WifiP2pState.SEARCHING;  // Default
    private WifiP2pState currentState = WifiP2pState.NON_INITIALIZED;
    private boolean serviceAnnounced = false;

    // UI
    private TextView textViewName;
    private Button buttonShout;
    private Button buttonStop;
    private ListView listviewDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_p2p);

        // Variables
        nearbyDevices = new ArrayList<>();

        // UI
        textViewName = (TextView)findViewById(R.id.textViewName);
        buttonShout = (Button)findViewById(R.id.buttonShout);
        buttonStop = (Button)findViewById(R.id.buttonStop);
        listviewDevices = (ListView)findViewById(R.id.listViewDevices);
        buttonShout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                localService();
            }
        });
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopConnectingTarget();
            }
        });
        listViewDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nearbyDevices);
        listviewDevices.setAdapter(listViewDevicesAdapter);
        listviewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                targetName = nearbyDevices.get(position);
                targetState = WifiP2pState.DATA_REQUESTED;
                goToNextState();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeWifiP2p();
        retryHandler = new Handler(this);
        retryHandler.sendEmptyMessageDelayed(HANDLER_WHAT, HANDLER_DELAY_MS);
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
        retryHandler.removeMessages(HANDLER_WHAT);
        stopWifiP2p();
        super.onStop();
    }

    // implement WifiP2pManager.DnsSdServiceResponseListener
    @Override
    public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
        // Store the nearby device's name
        nearbyDevices.add(instanceName);
        listViewDevicesAdapter.notifyDataSetChanged();
        Log.d(LOG_TAG, "Found device " + instanceName);
        Toast.makeText(this, "Found device " + instanceName, Toast.LENGTH_SHORT).show();
    }

    // implement WifiP2pManager.DnsSdTxtRecordListener
    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
        targetMac = srcDevice.deviceAddress;
        String ip = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_IP);
        String port = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT);
        Log.d(LOG_TAG, "Socket " + ip + ":" + port);
        Toast.makeText(this, "Socket " + ip + ":" + port, Toast.LENGTH_SHORT).show();
        // Change state
        WifiP2pState lastTargetState = targetState;
        targetState = WifiP2pState.CONNECTING;
        // Make the finite state machine move if it stops
        if (lastTargetState == currentState) {
            goToNextState();
        }
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onSuccess() {
        goToNextState();
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onFailure(int reason) {
        String msg = getActionFailReason(reason);
        Log.e(LOG_TAG, msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean handleMessage(Message msg) {
        // Retry current part in the finite state machine if current part has no respond for a while
        if (currentState == targetState) {
            goToNextState();
        }
        // Set next retry time
        retryHandler.sendEmptyMessageDelayed(HANDLER_WHAT, HANDLER_DELAY_MS);
        return false;
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

    // Part 1: Initial Wi-Fi P2P------------------------------------------------------------------
    private void initializeWifiP2p() {
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
        // Random a socket.
        // TODO: Use real socket in the future.
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
        goToNextState();
    }

    private void initialized() {
        // Change UI
        buttonShout.setEnabled(true);
        buttonStop.setEnabled(false);
        // Change state
        goToNextState();
    }

    private void stopWifiP2p() {
        if (currentState != WifiP2pState.NON_INITIALIZED) {
            if (wifiLock != null) {
                wifiLock.release();
            }
            wifiP2pManager.removeGroup(wifiP2pChannel, null);
            wifiP2pManager.cancelConnect(wifiP2pChannel, null);
            wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, null);
            if (wifiP2pDnsSdTxtRecordRequest != null) {
                wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, null);
            }
            wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, null);
            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, null);
        }

        // Change state
        Log.d(LOG_TAG, currentState + " -> " + WifiP2pState.NON_INITIALIZED);
        currentState = WifiP2pState.NON_INITIALIZED;
    }

    // Part 2: Announce this device is running this APP-------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void announceService() {
        wifiP2pManager.addLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    private void serviceAnnounced() {
        serviceAnnounced = true;
        Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show();
        // Change UI
        buttonShout.setText(R.string.silent);
        textViewName.setClickable(false);
        // Change state
        targetState = WifiP2pState.SEARCHING;
        goToNextState();
    }

    // Hide this device is running this APP
    // When the command succeeds, onSuccess() will be called
    private void hideService() {
        wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    private void serviceHided() {
        serviceAnnounced = false;
        Toast.makeText(this, R.string.service_removed, Toast.LENGTH_SHORT).show();
        // Change UI
        buttonShout.setText(R.string.shout);
        textViewName.setClickable(true);
        // Change state
        targetState = WifiP2pState.SEARCHING;
        goToNextState();
    }

    // When buttonShout clicked
    private void localService() {
        WifiP2pState lastTargetState = targetState;
        if (serviceAnnounced) {
            targetState = WifiP2pState.SILENT;
        } else {
            targetState = WifiP2pState.SHOUT;
        }
        // Make the finite state machine move if it stops
        if (lastTargetState == currentState) {
            goToNextState();
        }
    }

    // Part 3: Discover the devices running this APP----------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void discoverNearByDevices() {
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
    }

    // When the command succeeds, onSuccess() will be called
    private void discoverNearbyServices() {
        // Remove all out of date devices
        nearbyDevices.clear();
        listViewDevicesAdapter.notifyDataSetChanged();
        // Use new request to discovery devices
        wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
    }

    // When the command succeeds, onSuccess() will be called
    private void discoverNearByServicesStep2() {
        wifiP2pManager.discoverServices(wifiP2pChannel, this);
    }

    private void searching() {
        if (targetState != currentState) {
            goToNextState();
        }
    }

    // Stop discovering nearby devices running this APP
    // When the command succeeds, onSuccess() will be called
    private void stopDiscoverNearbySearvices() {
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, this);
    }

    // When the command succeeds, onSuccess() will be called
    private void stopDiscoverNearbyDevices() {
        wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
    }

    // Step 4: Request specify device's data------------------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void requestData() {
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
        // Change UI
        buttonShout.setEnabled(false);
        buttonStop.setEnabled(true);
    }

    // When the command succeeds, onSuccess() will be called
    private void requestDataStep2() {
        wifiP2pDnsSdTxtRecordRequest = WifiP2pDnsSdServiceRequest.newInstance(targetName, WIFI_P2P_DNS_SD_SERVICE_TYPE);
        wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
    }

    // When the command succeeds, onSuccess() will be called
    private void requestDataStep3() {
        wifiP2pManager.discoverServices(wifiP2pChannel, this);
    }

    private void dataRequested() {
        if (targetState != currentState) {
            goToNextState();
        }
    }

    // Stop requesting data
    // When the command succeeds, onSuccess() will be called
    private void stopRequestingData() {
        wifiP2pManager.removeServiceRequest(wifiP2pChannel, wifiP2pDnsSdTxtRecordRequest, this);
    }

    private void stopRequestingDataStep2() {
        wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
    }

    // Step 5: Connect to the specified device----------------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void connectToTarget() {
        WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
        wifiP2pConfig.deviceAddress = targetMac;
//        wifiP2pConfig.groupOwnerIntent = 0;
        wifiP2pConfig.wps.setup = WpsInfo.PBC;  // TODO: Try other settings
        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig, this);
    }

    private void connecting() {
        if (targetState != currentState) {
            goToNextState();
        }
    }

    private void connected() {
        if (targetState != currentState) {
            goToNextState();
        }
    }

    // Stop connecting to the specified device
    // When the command succeeds, onSuccess() will be called
    private void stopOnGoingConnectRequest() {
        wifiP2pManager.cancelConnect(wifiP2pChannel, this);
    }

    // Disconnect with the target device
    // When the command succeeds, onSuccess() will be called
    private void removeWifiP2pGroup() {
        wifiP2pManager.removeGroup(wifiP2pChannel, this);
    }

    // When buttonStop is clicked
    private void stopConnectingTarget() {
        WifiP2pState lastTargetState = targetState;
        targetState = WifiP2pState.SEARCHING;
        // Make the finite state machine move if it stops
        if (lastTargetState == currentState) {
            goToNextState();
        }
    }

    // When WifiP2pReceiver receives an intent which indicates the connection established
    public void setTargetStateConnected() {
        // Only clients change state
        if (!serviceAnnounced) {
            WifiP2pState lastTargetState = targetState;
            targetState = WifiP2pState.CONNECTED;
            // Make the finite state machine move if it stops
            if (lastTargetState == currentState) {
                goToNextState();
            }
        }
        // TODO: Design server action
    }

    // When WifiP2pReceiver receives an intent which indicates the connection was broken
    public void setTargetStateSearching() {
        // Only clients change state
        if (!serviceAnnounced) {
            WifiP2pState lastTargetState = targetState;
            targetState = WifiP2pState.SEARCHING;
            // Make the finite state machine move if it stops
            if (lastTargetState == currentState) {
                goToNextState();
            }
        }
        // TODO: Design server action
    }

    // Step 6: Setup audio stream
    private void audioStreamSetup() {
        // TODO: Integrate with TestAudioStream
    }

    private void audioStreamDismiss() {
        // TODO: Integrate with TestAudioStream
    }

    // Finite state machine -----------------------------------------------------------------------
    // Go to next state when current state action is successful
    public void goToNextState() {
        WifiP2pState lastState = currentState;
        switch (currentState) {
            case NON_INITIALIZED:
                currentState = WifiP2pState.INITIALIZE_WIFI_P2P;
                break;
            case INITIALIZE_WIFI_P2P:
                currentState = WifiP2pState.INITIALIZED;
                break;
            case INITIALIZED:
                currentState = WifiP2pState.DISCOVER_PEERS;
                break;
            case DISCOVER_PEERS:
                currentState = WifiP2pState.ADD_SERVICE_REQUEST;
                break;
            case ADD_SERVICE_REQUEST:
                currentState = WifiP2pState.DISCOVER_SERVICES;
                break;
            case DISCOVER_SERVICES:
                currentState = WifiP2pState.SEARCHING;
                break;
            case SEARCHING:
                currentState = WifiP2pState.REMOVE_SERVICE_REQUEST;
                break;
            case REMOVE_SERVICE_REQUEST:
                currentState = WifiP2pState.STOP_PEER_DISCOVERY;
                break;
            case STOP_PEER_DISCOVERY:
                currentState = WifiP2pState.SEARCH_STOPPED;
                break;
            case SEARCH_STOPPED:
                if (targetState == WifiP2pState.SHOUT) {
                    currentState = WifiP2pState.ADD_LOCAL_SERVICE;
                } else if (targetState == WifiP2pState.SILENT) {
                    currentState = WifiP2pState.REMOVE_LOCAL_SERVICE;
                } else if (targetState == WifiP2pState.SEARCHING) {
                    currentState = WifiP2pState.DISCOVER_PEERS;
                } else {
                    currentState = WifiP2pState.DISCOVER_PEERS_DATA;
                }
                break;
            case ADD_LOCAL_SERVICE:
                currentState = WifiP2pState.SHOUT;
                break;
            case SHOUT:
                currentState = WifiP2pState.DISCOVER_PEERS;
                break;
            case REMOVE_LOCAL_SERVICE:
                currentState = WifiP2pState.SILENT;
                break;
            case SILENT:
                currentState = WifiP2pState.DISCOVER_PEERS;
                break;
            case DISCOVER_PEERS_DATA:
                currentState = WifiP2pState.ADD_SERVICE_REQUEST_DATA;
                break;
            case ADD_SERVICE_REQUEST_DATA:
                currentState = WifiP2pState.DISCOVER_SERVICES_DATA;
                break;
            case DISCOVER_SERVICES_DATA:
                currentState = WifiP2pState.DATA_REQUESTED;
                break;
            case DATA_REQUESTED:
                currentState = WifiP2pState.REMOVE_SERVICE_REQUEST_DATA;
                break;
            case REMOVE_SERVICE_REQUEST_DATA:
                currentState = WifiP2pState.STOP_PEER_DISCOVERY_DATA;
                break;
            case STOP_PEER_DISCOVERY_DATA:
                currentState = WifiP2pState.DATA_STOPPED;
                break;
            case DATA_STOPPED:
                if (targetState == WifiP2pState.DATA_REQUESTED) {
                    currentState = WifiP2pState.DISCOVER_PEERS_DATA;
                } else if (targetState == WifiP2pState.SEARCHING) {
                    currentState = WifiP2pState.INITIALIZED;
                } else {
                    currentState = WifiP2pState.CONNECT;
                }
                break;
            case CONNECT:
                currentState = WifiP2pState.CONNECTING;
                break;
            case CONNECTING:
                if (targetState == WifiP2pState.CONNECTED) {
                    currentState = WifiP2pState.AUDIO_STREAM_SETUP;
                } else {
                    currentState = WifiP2pState.CANCEL_CONNECT;
                }
                break;
            case CANCEL_CONNECT:
                currentState = WifiP2pState.DISCONNECTED;
                break;
            case DISCONNECTED:
                if (targetState == WifiP2pState.CONNECTING) {
                    currentState = WifiP2pState.CONNECT;
                } else {
                    currentState = WifiP2pState.INITIALIZED;
                }
                break;
            case AUDIO_STREAM_SETUP:
                currentState = WifiP2pState.CONNECTED;
                break;
            case CONNECTED:
                currentState = WifiP2pState.AUDIO_STEAM_DISMISS;
                break;
            case AUDIO_STEAM_DISMISS:
                currentState = WifiP2pState.REMOVE_GROUP;
                break;
            case REMOVE_GROUP:
                currentState = WifiP2pState.INITIALIZED;
                break;
            default:
                Log.e(LOG_TAG, "Unhandled state " + currentState);
        }
        Log.d(LOG_TAG, lastState + " -> " + currentState);
        doStateAction();
    }

    // Go to last state when current state action failed
    public void goToPreviousState() {
        // TODO: switch (currentState)
    }

    // Do action according to current state
    private void doStateAction() {
        switch (currentState) {
            case NON_INITIALIZED:
                break;
            case INITIALIZE_WIFI_P2P:
                initializeWifiP2p();
                break;
            case INITIALIZED:
                initialized();
                break;
            case DISCOVER_PEERS:
                discoverNearByDevices();
                break;
            case ADD_SERVICE_REQUEST:
                discoverNearbyServices();
                break;
            case DISCOVER_SERVICES:
                discoverNearByServicesStep2();
                break;
            case SEARCHING:
                searching();
                break;
            case REMOVE_SERVICE_REQUEST:
                stopDiscoverNearbySearvices();
                break;
            case STOP_PEER_DISCOVERY:
                stopDiscoverNearbyDevices();
                break;
            case SEARCH_STOPPED:
                goToNextState();
                break;
            case ADD_LOCAL_SERVICE:
                announceService();
                break;
            case SHOUT:
                serviceAnnounced();
                break;
            case REMOVE_LOCAL_SERVICE:
                hideService();
                break;
            case SILENT:
                serviceHided();
                break;
            case DISCOVER_PEERS_DATA:
                requestData();
                break;
            case ADD_SERVICE_REQUEST_DATA:
                requestDataStep2();
                break;
            case DISCOVER_SERVICES_DATA:
                requestDataStep3();
                break;
            case DATA_REQUESTED:
                dataRequested();
                break;
            case REMOVE_SERVICE_REQUEST_DATA:
                stopRequestingData();
                break;
            case STOP_PEER_DISCOVERY_DATA:
                stopRequestingDataStep2();
                break;
            case DATA_STOPPED:
                goToNextState();
                break;
            case CONNECT:
                connectToTarget();
                break;
            case CONNECTING:
                connecting();
                break;
            case CANCEL_CONNECT:
                stopOnGoingConnectRequest();
                break;
            case DISCONNECTED:
                goToNextState();
                break;
            case AUDIO_STREAM_SETUP:
                audioStreamSetup();
                break;
            case CONNECTED:
                connected();
                break;
            case AUDIO_STEAM_DISMISS:
                audioStreamDismiss();
                break;
            case REMOVE_GROUP:
                removeWifiP2pGroup();
                break;
            default:
                Log.e(LOG_TAG, "State " + currentState + " has no action");
        }
    }
}
