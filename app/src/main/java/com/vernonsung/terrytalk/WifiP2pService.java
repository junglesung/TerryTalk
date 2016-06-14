package com.vernonsung.terrytalk;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.rtp.AudioStream;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

public class WifiP2pService extends Service
        implements WifiP2pManager.ActionListener,
        WifiP2pManager.PeerListListener {
    public class LocalBinder extends Binder {
        WifiP2pService getService() {
            // Return this instance of service so clients can call public methods
            return WifiP2pService.this;
        }
    }

    public enum WifiP2pState {
        INITIALIZING, SEARCHING, IDLE,
        REJECTING, SERVER, SERVER_DISCONNECTING,
        CONNECTING, CANCELING, RECONNECTING, REGISTERING, CONNECTED, DISCONNECTING,
        STOPPING, STOPPED
    }

    private static final String LOG_TAG = "testtest";
    private final IBinder mBinder = new LocalBinder();
    // Start commands
    public static final String ACTION_START = "com.vernonsung.testwifip2p.action.start";  // First start this service
    public static final String ACTION_STOP = "com.vernonsung.testwifip2p.action.stop";  // Stop connecting, disconnect, stop this service
    public static final String ACTION_CONNECT = "com.vernonsung.testwifip2p.action.connect";  // Connect to a device
    public static final String ACTION_REFRESH = "com.vernonsung.testwifip2p.action.refresh";  // Refresh nearby device list
    public static final String INTENT_EXTRA_TARGET = "com.vernonsung.testwifip2p.TARGET";  // String. A parameter of ACTION_CONNECT
    public static final String INTENT_EXTRA_PORT = "com.vernonsung.testwifip2p.PORT";  // int. A parameter of ACTION_CONNECT
    // Local broadcast to send
    public static final String UPDATE_NEARBY_DEVICES_ACTION = "com.vernonsung.testwifip2p.action.update_nearby_devices";
    public static final String UPDATE_STATE_ACTION = "com.vernonsung.testwifip2p.action.update_status";
    public static final String UPDATE_IP_ACTION = "com.vernonsung.testwifip2p.action.update_ip";
    public static final String UPDATE_PORT_ACTION = "com.vernonsung.testwifip2p.action.update_port";
    public static final String INTENT_EXTRA_STATE = "com.vernonsung.testwifip2p.state";  // enum WifiP2pState
    public static final String INTENT_EXTRA_IP = "com.vernonsung.testwifip2p.ip";  // String
    public static final String INTENT_EXTRA_REGISTRATION_PORT = "com.vernonsung.testwifip2p.registration_port";  // String
    // Wi-Fi Direct
    private static final String WIFI_LOCK = "wifiLock";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pReceiver wifiP2pReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private WifiManager.WifiLock wifiLock = null;
    private String wifiP2pLocalDeviceName;  // Local Wi-Fi direct device name for both server and clients.
    private String wifiP2pLocalDeviceMac;  // Local Wi-Fi direct device MAC address for both server and clients.
    private String wifiP2pTargetDeviceName;  // Remote Wi-Fi direct device name. Server name for clients.
    private String wifiP2pTargetDeviceMac;  // Remote MAC address. Server MAC address for clients.
    private InetAddress wifiP2pLocalDeviceIp = null;  // Local IP for both server and clients.
    // Nearby devices list -----------------------------------------------------------------------
    public static final String MAP_ID_DEVICE_NAME = "deviceName";
    public static final String MAP_ID_STATUS = "status";
    public static final String MAP_ID_MAC = "mac";
    private ArrayList<HashMap<String, String>> nearbyDevices;
    private ArrayList<HashMap<String, String>> clientDevices;
    // Finite state machine ----------------------------------------------------------------------
    private WifiP2pState currentState = WifiP2pState.STOPPED;  // Default state
    private boolean isConnected = false;  // Indicate whether it can removeGroup()
    private boolean isGroupOwner = false;  // Indicate whether it is a Wi-Fi direct group owner
    private boolean peerDiscoveryStopped = true;  // Indicate whether it can discoverPeers()
    // Audio stream ------------------------------------------------------------------------------
    private AudioTransceiver audioTransceiver = null;
    // Registration ------------------------------------------------------------------------------
    private SocketServerThreads socketServerThreads = null;
    private Thread registrationThread = null;
    private InetAddress remoteIp;        // Registration server IP. Remote IP for clients.
    private int remoteRegistrationPort;  // Registration server port. Remote port for clients.
    private int localRegistrationPort;   // Registration server port. Local port for server.

    // Vernon debug
//    private static final String Z3_DISPLAY = "Z3";
//    private static final String M4_DISPLAY = "M4";
//    private static final String NOTE_12_2_DISPLAY = "Note Pro 12.2";
//    private static final String Z3_MAC = "86:8e:df:79:08:d8";
//    private static final String M4_MAC = "5a:48:22:62:af:30";
//    private static final String NOTE_12_2_MAC = "b6:3a:28:d6:7c:4e";

    // Constructor -------------------------------------------------------------------------------
    public WifiP2pService() {
        // Variables
        nearbyDevices = new ArrayList<>();
        clientDevices = new ArrayList<>();
    }

    // Override functions ------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START:
                onActionStart();
                break;
            case ACTION_STOP:
                onActionStop();
                break;
            case ACTION_CONNECT:
                onActionConnect(intent);
                break;
            case ACTION_REFRESH:
                onActionRefresh();
                break;
            default:
                Log.e(LOG_TAG, "Unknown action " + intent.getAction());
                break;
        }
        // Don't restart service with last intent if it's killed by the system
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Allow the same activity to rebind this service
        return true;
    }

    @Override
    public void onDestroy() {
    }

    // Implement WifiP2pManager.PeerListListener
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        nearbyDevices.clear();
        Collection<WifiP2pDevice> devices = peers.getDeviceList();
        for (WifiP2pDevice device : devices) {
            HashMap<String, String> a = new HashMap<>();
            a.put(MAP_ID_DEVICE_NAME, device.deviceName);
            a.put(MAP_ID_MAC, device.deviceAddress);
            a.put(MAP_ID_STATUS, getDeviceState(device));
            nearbyDevices.add(a);
            Log.d(LOG_TAG, "Found device " + device.deviceName + " " + device.deviceAddress);
        }
        Log.d(LOG_TAG, "There are " + devices.size() + " devices");

        // Notify activity to update nearby device list
        notifyActivityUpdateDeviceList();
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onSuccess() {
        switch (currentState) {
            case INITIALIZING:
                // From removeExistingWifiP2pConnection()
                // Do nothing. WIFI_P2P_CONNECTION_CHANGED_ACTION should be received soon.
                break;
            case SEARCHING:
            case IDLE:
                // From discoverNearbyDevices()
                // Do nothing. WIFI_P2P_PEERS_CHANGED_ACTION will take over.
                break;
            case REJECTING:
                // From rejectClient()
                // Do nothing. WIFI_P2P_CONNECTION_CHANGED_ACTION should be received soon.
                break;
            case SERVER:
                // From beConnectedFirst()
                // Do nothing. Wait for other clients to connect.
                break;
            case SERVER_DISCONNECTING:
                // From dismissServer()
                // Do nothing. WIFI_P2P_CONNECTION_CHANGED_ACTION should be received soon.
                break;
            case CONNECTING:
                // From connectTarget()
                // Do nothing
                break;
            case CANCELING:
                // From stopOnGoingConnectRequest()
                changeState(WifiP2pState.IDLE);
                discoverNearbyDevices();
                break;
            case RECONNECTING:
                break;
            case REGISTERING:
                Log.e(LOG_TAG, "Never here. OnSuccess() REGISTERING");
                break;
            case CONNECTED:
                // From no-where
                break;
            case DISCONNECTING:
                // From disconnectTarget()
                // Do nothing.  WIFI_P2P_CONNECTION_CHANGED_ACTION should be received soon.
                break;
            case STOPPING:
                // From stopService()
                stopServicePart2();
                break;
            case STOPPED:
                // From no-where
                break;
            default:
                Log.e(LOG_TAG, "onSuccess() doesn't handle state " + currentState.toString());
                break;
        }
    }

    // implement WifiP2pManager.ActionListener
    @Override
    public void onFailure(int reason) {
        String msg = getActionFailReason(reason);
        Log.e(LOG_TAG, msg);
        switch (reason) {
            case WifiP2pManager.BUSY:
                wifiP2pBusyHandler();
                break;
            case WifiP2pManager.ERROR:
                wifiP2pErrorHandler();
                break;
            default:
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void wifiP2pBusyHandler() {
        switch (currentState) {
            case INITIALIZING:
                // From removeExistingWifiP2pConnection()
                changeState(WifiP2pState.IDLE);
                break;
            case SEARCHING:
            case IDLE:
                // From discoverNearbyDevices()
                Toast.makeText(WifiP2pService.this, R.string.please_refresh_again, Toast.LENGTH_SHORT).show();
                break;
            case REJECTING:
                // From rejectClient()
                Toast.makeText(WifiP2pService.this, R.string.please_the_client_connect_again, Toast.LENGTH_SHORT).show();
                changeState(WifiP2pState.SERVER);
                discoverNearbyDevices();
                break;
            case SERVER:
                // From beConnectedFirst()
                Toast.makeText(WifiP2pService.this, R.string.please_refresh_again, Toast.LENGTH_SHORT).show();
                break;
            case SERVER_DISCONNECTING:
                // From dismissServer()
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                changeState(WifiP2pState.SERVER);
                break;
            case CONNECTING:
                // From connectTarget()
                changeState(WifiP2pState.IDLE);
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                discoverNearbyDevices();
                break;
            case CANCELING:
                // From stopOnGoingConnectRequest()
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                break;
            case RECONNECTING:
                break;
            case REGISTERING:
                Log.e(LOG_TAG, "Never here. OnBusy() REGISTERING");
                break;
            case CONNECTED:
                // From no-where
                break;
            case DISCONNECTING:
                // From disconnectTarget()
                changeState(WifiP2pState.CONNECTED);
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                break;
            case STOPPING:
                // From stopService()
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                break;
            case STOPPED:
                // From no-where
                break;
        }
    }

    private void wifiP2pErrorHandler() {
        switch (currentState) {
            case INITIALIZING:
                // From removeExistingWifiP2pConnection()
                changeState(WifiP2pState.IDLE);
                break;
            case SEARCHING:
            case IDLE:
                // From discoverNearbyDevices()
                Toast.makeText(WifiP2pService.this, R.string.please_reboot, Toast.LENGTH_SHORT).show();
                break;
            case REJECTING:
                // From rejectClient(). It's already disconnected.
                changeState(WifiP2pState.IDLE);
                discoverNearbyDevices();
                break;
            case SERVER:
                // From beConnectedFirst()
                // Do nothing. It's already discovering.
                break;
            case SERVER_DISCONNECTING:
                // From dismissServer(). It's already disconnected.
                changeState(WifiP2pState.IDLE);
                discoverNearbyDevices();
                break;
            case CONNECTING:
                // From connectTarget()
                changeState(WifiP2pState.IDLE);
                Toast.makeText(WifiP2pService.this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                discoverNearbyDevices();
                break;
            case CANCELING:
                // From stopOnGoingConnectRequest(). Already canceled
                changeState(WifiP2pState.IDLE);
                discoverNearbyDevices();
                break;
            case RECONNECTING:
                break;
            case REGISTERING:
                Log.e(LOG_TAG, "Never here. OnError() REGISTERING");
                break;
            case CONNECTED:
                // From no-where
                break;
            case DISCONNECTING:
                // From disconnectTarget(). Already disconnected
                changeState(WifiP2pState.IDLE);
                discoverNearbyDevices();
                break;
            case STOPPING:
                // From stopService()
                stopServicePart2();
                break;
            case STOPPED:
                // From no-where
                break;
        }
    }

    // Start service actions ---------------------------------------------------------------------
    // After receiving an intent with a "START" action
    private void onActionStart() {
        // Make service running until manually stop it
        turnIntoForeground();
        if (currentState == WifiP2pState.STOPPED) {
            initialAll();
        }
    }

    // After receiving an intent with a "STOP" action
    private void onActionStop() {
        switch (currentState) {
            case INITIALIZING:
            case SEARCHING:
            case IDLE:
                stopService();
                break;
            case REJECTING:
                // Do nothing. Wait for WIFI_P2P_CONNECTION_CHANGED_ACTION to report disconnected
                Toast.makeText(this, R.string.please_wait_for_disconnecting, Toast.LENGTH_SHORT).show();
                break;
            case SERVER:
                dismissServer();
                break;
            case SERVER_DISCONNECTING:
                // Do nothing. Wait for WIFI_P2P_CONNECTION_CHANGED_ACTION to report disconnected
                Toast.makeText(this, R.string.please_wait_for_disconnecting, Toast.LENGTH_SHORT).show();
                break;
            case CONNECTING:
                stopOnGoingConnectRequest();
                break;
            case REGISTERING:
                // TODO: stop socket client async task in the future
                Toast.makeText(this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                break;
            case CONNECTED:
                disconnectTarget();
                break;
            case CANCELING:
                // Do nothing. The time is very short.
                Toast.makeText(this, R.string.please_wait_for_disconnecting, Toast.LENGTH_SHORT).show();
                break;
            case RECONNECTING:
                // TODO: Cancel connecting in the future
                break;
            case DISCONNECTING:
                // Do nothing. Wait for WIFI_P2P_CONNECTION_CHANGED_ACTION to report disconnected
                Toast.makeText(this, R.string.please_wait_for_disconnecting, Toast.LENGTH_SHORT).show();
                break;
            case STOPPING:
            case STOPPED:
                stopService();
                break;
        }
    }

    // After receiving an intent with a "CONNECT" action
    // TODO: Implement changing target when CONNECTING, RECONNECTING, CONNECTED
    private void onActionConnect(Intent intent) {
        switch (currentState) {
            case INITIALIZING:
                return;
            case SEARCHING:
            case IDLE:
                break;
            case REJECTING:
            case SERVER:
            case SERVER_DISCONNECTING:
            case CONNECTING:
            case CANCELING:
            case RECONNECTING:
            case REGISTERING:
            case CONNECTED:
            case DISCONNECTING:
            case STOPPING:
            case STOPPED:
                return;
        }
        wifiP2pTargetDeviceName = intent.getStringExtra(INTENT_EXTRA_TARGET);
        remoteRegistrationPort = intent.getIntExtra(INTENT_EXTRA_PORT, 0);
        if (wifiP2pTargetDeviceName == null ||
                wifiP2pTargetDeviceName.isEmpty() ||
                remoteRegistrationPort == 0) {
            return;
        }
        connectTarget();
    }

    // After receiving an intent with a "REFRESH" action
    private void onActionRefresh() {
        switch (currentState) {
            case INITIALIZING:
                Toast.makeText(this, R.string.please_wait_for_initializing, Toast.LENGTH_SHORT).show();
                break;
            case SEARCHING:
            case IDLE:
            case REJECTING:
            case SERVER:
            case SERVER_DISCONNECTING:
                discoverNearbyDevices();
                break;
            case CONNECTING:
            case CANCELING:
            case RECONNECTING:
                // wifiP2pManager.discoverPeers() conflicts with wifiP2pManager.connect()
                Toast.makeText(this, R.string.please_try_later, Toast.LENGTH_SHORT).show();
                break;
            case REGISTERING:
            case CONNECTED:
                discoverNearbyDevices();
                break;
            case DISCONNECTING:
            case STOPPING:
            case STOPPED:
                Toast.makeText(this, R.string.please_restart_app, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // Some sub-functions ------------------------------------------------------------------------
    // Turn into a foreground service. Provide a running notification
    private void turnIntoForeground() {
        int NOTIFICATION_ID = 1;
        // assign the song name to songName
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, WifiP2pFragment.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.terry_talks_is_still_running))
                .setSmallIcon(R.mipmap.ic_speech_balloon_orange_t)
                .setContentIntent(pi)
                .build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, notification);
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

    // Translate Wi-Fi Direct device state
    private String getDeviceState(WifiP2pDevice device) {
        switch (device.status) {
            case WifiP2pDevice.AVAILABLE:
                return getString(R.string.available);
            case WifiP2pDevice.CONNECTED:
                return getString(R.string.connected);
            case WifiP2pDevice.FAILED:
                return getString(R.string.failed);
            case WifiP2pDevice.INVITED:
                return getString(R.string.invited);
            case WifiP2pDevice.UNAVAILABLE:
                return getString(R.string.unavailable);
        }
        return null;
    }

    // Update UI functions -----------------------------------------------------------------------
    // Notify the activity to update device list
    private void notifyActivityUpdateDeviceList() {
        Intent intent = new Intent(UPDATE_NEARBY_DEVICES_ACTION);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Notify the activity to update status
    private void notifyActivityUpdateStatus() {
        Intent intent = new Intent(UPDATE_STATE_ACTION);
        intent.putExtra(INTENT_EXTRA_STATE, currentState.ordinal());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Notify the activity to update IP
    private void notifyActivityUpdateIp() {
        String ip;
        if (wifiP2pLocalDeviceIp == null) {
            ip = "";
        } else {
            ip = wifiP2pLocalDeviceIp.getHostAddress();
        }
        Intent intent = new Intent(UPDATE_IP_ACTION);
        intent.putExtra(INTENT_EXTRA_IP, ip);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Notify the activity to update port
    private void notifyActivityUpdatePort() {
        Intent intent = new Intent(UPDATE_PORT_ACTION);
        intent.putExtra(INTENT_EXTRA_REGISTRATION_PORT, localRegistrationPort);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Part 1: Initial Wi-Fi P2P and necessary data -----------------------------------------------
    private void initialAll() {
        changeState(WifiP2pState.INITIALIZING);
        initialAudioStream();
        initializeWifiP2p();
        registrationSetup();
    }

    private void initialAudioStream() {
        audioTransceiver = new AudioTransceiver(this);
    }

    private void initializeWifiP2p() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
        // Filter the intent received by WifiP2pReceiver in order to sense Wi-Fi P2P status
        wifiP2pIntentFilter = new IntentFilter();
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        // Receive intents related to Wi-Fi P2p
        wifiP2pReceiver = new WifiP2pReceiver(this);
        registerReceiver(wifiP2pReceiver, wifiP2pIntentFilter);
        Log.d(LOG_TAG, "Broadcast receiver registered");

        // To ensure Wi-Fi is ON
        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
        wifiLock.acquire();
    }

    // Setup server socket thread to communicate with clients
    private void registrationSetup() {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException e) {
            Log.d(LOG_TAG, "Create server socket failed because " + e.toString());
            serverSocket = null;
        }
        if (serverSocket == null || serverSocket.isClosed()) {
            Log.d(LOG_TAG, "Initial server socket failed. Terminate.");
            Toast.makeText(this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
            stopService();
            return;
        }
        localRegistrationPort = serverSocket.getLocalPort();
        socketServerThreads = new SocketServerThreads(serverSocket, audioTransceiver);
        registrationThread = new Thread(socketServerThreads, "RegistrationServer");
        registrationThread.start();
        notifyActivityUpdatePort();
    }

    // Remove existing Wi-Fi direct connection created by other APP or this APP crashed last time
    private void removeExistingWifiP2pConnection() {
        if (isConnected) {
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
            // When command succeeds, WIFI_P2P_CONNECTION_CHANGED_ACTION will be received and invoke discoverNearbyDevices()
        } else {
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }
    }

    // Part 2: Stop Wi-Fi P2P and this service ----------------------------------------------------
    // To stop this background service
    private void stopService() {
        changeState(WifiP2pState.STOPPING);
        if (peerDiscoveryStopped) {
            stopServicePart2();
        } else {
            // OnSuccess() will invoke stopServicePart2()
            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
        }
    }

    private void stopServicePart2() {
        // Close the registration socket
        if (socketServerThreads != null) {
            socketServerThreads.setToQuit(true);
        }
        // Stop receiving intents related to Wi-Fi P2p
        try {
            unregisterReceiver(wifiP2pReceiver);
            Log.d(LOG_TAG, "Broadcast receiver unregistered");
        } catch (IllegalArgumentException e) {
            Log.d(LOG_TAG, "Receiver is already unregistered");
        }
        // Release Wi-Fi lock
        if (wifiLock != null) {
            wifiLock.release();
        }
        // Change state
        changeState(WifiP2pState.STOPPED);
        // Stop service
        stopSelf();
    }

    // Part 3: Discover nearby devices ------------------------------------------------------------
    private void discoverNearbyDevices() {
        // According to my experiment, there is no need to start peer discovery until last peer discovery stops.
        // In other words, it won't occur error to start peer discovery even last peer discovery is on-going.
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
        // When some nearby devices are found, it'll receive WIFI_P2P_STATE_CHANGED_ACTION
    }

    public void discoverNearbyDevicesStep2() {
        wifiP2pManager.requestPeers(wifiP2pChannel, this);
        // When the command succeeds, onPeersAvailable() will be called
    }

    // Part 4: Become a server -------------------------------------------------------------------
    // Be connected by the first client
    private void beConnectedFirst(WifiP2pGroup groupInfo) {
        updateClientList(groupInfo);
        if (!isConnected) {
            // Client found it should not be the group owner. So it disconnected.
            clearRememberedDevicesStep1();
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        } else {
            // Make sure I'm the group owner.
            if (!isGroupOwner) {
                Log.d(LOG_TAG, "I should be the group owner. Clear remembered groups and disconnect");
                // Clear remembered group so that I will be the group owner next time my group formed
                clearRememberedDevicesStep1();
                rejectClient();
            } else {
                // I'm the group owner
                clearRememberedDevicesStep1();
                changeState(WifiP2pState.SERVER);
                discoverNearbyDevices();
            }
        }
    }

    // Break the first clients connection because I'm not the group owner
    private void rejectClient() {
        if (isConnected) {
            changeState(WifiP2pState.REJECTING);
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
        } else {
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }
    }

    // Be connected by more clients
    private void beConnectedMore(WifiP2pGroup groupInfo) {
        updateClientList(groupInfo);
        if (!isConnected) {
            // The last client left
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        } else {
            discoverNearbyDevices();
        }
    }

    // Server disconnects so that all client will disconnect
    private void dismissServer() {
        if (isConnected) {
            changeState(WifiP2pState.SERVER_DISCONNECTING);
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
        } else {
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }
    }

    private void serverDisconnected(WifiP2pGroup groupInfo) {
        updateClientList(groupInfo);
        if (!isConnected) {
            // The last client left
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }

    }

    // Part 5: Handle Wi-Fi direct connection change ----------------------------------------------
    // When WifiP2pReceiver receives WIFI_P2P_CONNECTION_CHANGED_ACTION
    public void connectionChangeActionHandler(NetworkInfo networkInfo, WifiP2pInfo wifiP2pInfo, WifiP2pGroup groupInfo) {
        updateWifiDeviceIp(groupInfo);
        // Check all factors that represent the connection was established because sometimes they are not synchronized.
        isConnected = (groupInfo.getClientList().size() != 0 ||
                wifiP2pLocalDeviceIp != null ||
                networkInfo.isConnected() ||
                wifiP2pInfo.groupFormed ||
                groupInfo.getOwner() != null);
        // Check all factors that represent I'm the group owner because sometimes they are not synchronized.
        isGroupOwner = isConnected &&
                        (wifiP2pInfo.isGroupOwner ||
                        groupInfo.isGroupOwner() ||
                        groupInfo.getOwner().deviceName.equals(wifiP2pLocalDeviceName));
        switch (currentState) {
            case INITIALIZING:
                removeExistingWifiP2pConnection();
                break;
            case SEARCHING:
            case IDLE:
                beConnectedFirst(groupInfo);
                break;
            case REJECTING:
                serverDisconnected(groupInfo);
                break;
            case SERVER:
                beConnectedMore(groupInfo);
                break;
            case SERVER_DISCONNECTING:
                serverDisconnected(groupInfo);
                break;
            case CONNECTING:
            case CANCELING:
                connectTargetStep2(wifiP2pInfo);
                break;
            case RECONNECTING:
                break;
            case CONNECTED:
                connectionEnd();
                break;
            case DISCONNECTING:
                connectionEnd();
                break;
            case STOPPING:
                break;
            case STOPPED:
                break;
        }
    }

    private InetAddress getIpByInterface(@NonNull String interfaceName) {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
            if (networkInterface == null) {
                Log.e(LOG_TAG, "Interface " + interfaceName + " is not found");
                return null;
            }
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            for (InetAddress a : Collections.list(addresses)) {
                if (!a.isLoopbackAddress() && a instanceof Inet4Address && a.getHostAddress().startsWith("192")) {
                    return a;
                } else {
                    Log.d(LOG_TAG, "IP " + a.getHostAddress() + " is found but not what we want");
                }
            }
        } catch (SocketException ex) {
            Log.d("SocketException ", ex.toString());
        }
        Log.e(LOG_TAG, "No useful IP");
        return null;
    }

    /**
     * Update Wi-Fi Direct interface IP
     * Both server and clients call this function.
     *
     * @param groupInfo
     */
    private void updateWifiDeviceIp(@NonNull WifiP2pGroup groupInfo) {
        String interfaceName = groupInfo.getInterface();
        if (interfaceName == null) {
            wifiP2pLocalDeviceIp = null;
        } else {
            wifiP2pLocalDeviceIp = getIpByInterface(interfaceName);
            if (wifiP2pLocalDeviceIp != null) {
                Log.d(LOG_TAG, "Wi-Fi direct IP " + wifiP2pLocalDeviceIp.getHostAddress());
            }
        }
        notifyActivityUpdateIp();
    }

    /**
     * Show clients on list view and remove disconnected clients from audioTransceiver.
     * It's Called only when this is a server.
     */
    private void updateClientList(WifiP2pGroup groupInfo) {
        Collection<WifiP2pDevice> devices = groupInfo.getClientList();
        // Faster way to clear client list if there is no client
        if (devices.size() == 0) {
            clearClientList();
            return;
        }

        ArrayList<HashMap<String, String>> newClientDevices = new ArrayList<>();
        // Add current connected clients to a new list
        for (WifiP2pDevice device : devices) {
            HashMap<String, String> a = new HashMap<>();
            a.put(MAP_ID_DEVICE_NAME, device.deviceName);
            a.put(MAP_ID_STATUS, getDeviceState(device));
            a.put(MAP_ID_MAC, device.deviceAddress);
            newClientDevices.add(a);
        }
        // Remove disconnected clients from audio transceiver.
        // P.S. Clients were added to the audio transceiver when they registered.
        for (HashMap<String, String> a : clientDevices) {
            String macA = a.get(MAP_ID_MAC);
            boolean toRemove = true;
            for (HashMap<String, String> b : newClientDevices) {
                if (b.get(MAP_ID_MAC).equals(macA)) {
                    toRemove = false;
                    break;
                }
            }
            if (toRemove) {
                audioTransceiver.removeStream(macA);
            }
        }
        // Replace the list by new list
        clientDevices = newClientDevices;
        // Update activity
        notifyActivityUpdateDeviceList();
    }

    // Clear client list if all clients disconnected
    private void clearClientList() {
        // Clear the listView in the activity
        clientDevices.clear();
        notifyActivityUpdateDeviceList();
        // Clear all audio streams and stop playing audio
        audioTransceiver.clearStreams();
    }

    /**
     * Store group owner IP for registration
     * It's called only when this is a client
     *
     * @param wifiP2pInfo
     */
    private void updateServerIp(@NonNull WifiP2pInfo wifiP2pInfo) {
        remoteIp = wifiP2pInfo.groupOwnerAddress;
        if (remoteIp == null) {
            Log.d(LOG_TAG, "Disconnected. Group owner IP is null");
        } else {
            String groupOwnerIp = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            Log.d(LOG_TAG, "Connection is established. Group owner IP " + groupOwnerIp);
        }
    }

    /**
     * Set the group owner's status to connected or available
     * It's called only when this is a client
     */
    private void updateTargetStatus() {
        for (HashMap<String, String> device : nearbyDevices) {
            if (device.get(MAP_ID_DEVICE_NAME).equals(wifiP2pTargetDeviceName)) {
                // Change device status
                try {
                    String status = device.get(MAP_ID_STATUS);
                    // Device state is usually "Unavailable" which is not correct. Manually assign instead.
                    // status = status.replaceFirst("^\\w+\\s", getDeviceState(groupInfo.getOwner()) + " ");
                    if (isConnected) {
                        status = status.replaceFirst("^\\w+\\s", "Connected" + " ");
                    } else {
                        status = status.replaceFirst("^\\w+\\s", "Available" + " ");
                    }
                    device.put(MAP_ID_STATUS, status);
                    notifyActivityUpdateDeviceList();
                } catch (Exception e) {
                    // String.replaceFirst() may throw exceptions
                    e.printStackTrace();
                    Log.e(LOG_TAG, "replace device status failed because " + e.toString());
                }
                break;
            }
        }
    }

    // Part 6: Connect to the specified device----------------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void connectTarget() {
        // Find MAC address by device name
        for (HashMap<String, String> a : nearbyDevices) {
            String name = a.get(MAP_ID_DEVICE_NAME);
            if (name.equals(wifiP2pTargetDeviceName)) {
                wifiP2pTargetDeviceMac = a.get(MAP_ID_MAC);
                break;
            }
        }

        // Setup connection configuration
        WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
        wifiP2pConfig.deviceAddress = wifiP2pTargetDeviceMac;
        wifiP2pConfig.groupOwnerIntent = 0;
//        wifiP2pConfig.wps.setup = WpsInfo.PBC;  // TODO: Try other settings

        // Change state
        changeState(WifiP2pState.CONNECTING);

        // Connect to target
        Log.d(LOG_TAG, "Connect to " + wifiP2pConfig.deviceAddress);
        Toast.makeText(this, "Connect to " + wifiP2pConfig.deviceAddress, Toast.LENGTH_SHORT).show();
        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig, this);
        // When the command is successful, WIFI_P2P_CONNECTION_CHANGED_ACTION will be received and invoke connectTargetStep2()
    }

    // Register to the server
    private void connectTargetStep2(WifiP2pInfo wifiP2pInfo) {
        updateServerIp(wifiP2pInfo);
        updateTargetStatus();
        // Make sure I'm not the group owner
        if (!isConnected) {
            // Server detected I'm the group owner instead of itself.
            Log.d(LOG_TAG, "Server detected I'm the group owner instead of itself. Clear remembered groups and disconnect");
            Toast.makeText(this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
            // Clear remembered group so that I won't be the group owner next time I connect to the server.
            clearRememberedDevicesStep1();
            // Disconnect. User may manually connect again.
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        } else {
            if (isGroupOwner) {
                // I found I should not be the group owner.
                Log.d(LOG_TAG, "I should not be the group owner. Clear remembered groups and disconnect");
                Toast.makeText(this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
                // Clear remembered group so that I won't be the group owner next time I connect to the server.
                clearRememberedDevicesStep1();
                // Disconnect. User may manually connect again.
                disconnectTarget();
            } else {  // isConnected == true
                Log.d(LOG_TAG, "Register to the server");
                // Register to the server
                SocketClientTask socketClientTask = new SocketClientTask(
                        this,
                        new InetSocketAddress(wifiP2pLocalDeviceIp, 0),
                        new InetSocketAddress(remoteIp, remoteRegistrationPort),
                        wifiP2pLocalDeviceMac);
                socketClientTask.execute();
                // After socketClientTask finished, audioStreamSetup() will be called
            }
        }
    }

    // Called after socketClientTask finished
    public void audioStreamSetup(AudioStream audioStream) {
        if (audioStream == null) {
            Log.d(LOG_TAG, "Registration failed");
            Toast.makeText(this, R.string.please_try_again, Toast.LENGTH_SHORT).show();
            disconnectTarget();
        } else {
            // Play the audio stream
            audioTransceiver.addClientStream(wifiP2pTargetDeviceMac, audioStream);
            changeState(WifiP2pState.CONNECTED);
        }
    }

    // Cancel on-going connection request
    private void stopOnGoingConnectRequest() {
        changeState(WifiP2pState.CANCELING);
        wifiP2pManager.cancelConnect(wifiP2pChannel, this);
        // onSuccess() will invoke connectTarget() to connect to the new target
    }

    // Disconnect with the target device (the server)
    private void disconnectTarget() {
        if (isConnected) {
            changeState(WifiP2pState.DISCONNECTING);
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
            // When the command succeeds, WIFI_P2P_CONNECTION_CHANGED_ACTION will be received
        } else {
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }
    }

    // Disconnected from target (the server)
    private void connectionEnd() {
        if (!isConnected) {
            // Stop playing the audio stream
            audioTransceiver.removeStream(wifiP2pTargetDeviceMac);
            // Clear remembered group
            clearRememberedDevicesStep1();
            // Change state
            changeState(WifiP2pState.IDLE);
            discoverNearbyDevices();
        }
    }

    // Step 7: Clear remembered Wi-Fi direct group------------------------------------------------
    // Clear remembered devices because
    // 1. the new group created by a remembered device won't show in discovering services
    // 2. The group owner won't change even if the other device create the group
    public void clearRememberedDevicesStep1() {
        Class PersistentGroupInfoListener;   // WifiP2pManager.PersistentGroupInfoListener
        Method requestPersistentGroupInfo;   // WifiP2pManager.requestPersistentGroupInfo()
        Object persistentGroupInfoListener;
        // Get hidden object through Java reflection
        try {
            PersistentGroupInfoListener = Class.forName("android.net.wifi.p2p.WifiP2pManager$PersistentGroupInfoListener");
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, "Bug! Interface WifiP2pManager.PersistentGroupInfoListener not found, " + e.toString());
            return;
        }
        // Get hidden method through Java reflection
        try {
            requestPersistentGroupInfo = WifiP2pManager.class.getMethod(
                    "requestPersistentGroupInfo",
                    WifiP2pManager.Channel.class,
                    PersistentGroupInfoListener);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, "Bug! Method WifiP2pManager.requestPersistentGroupInfo() not found");
            return;
        }
        // Implement hidden interface through dynamic proxies
        try {
            persistentGroupInfoListener = Proxy.newProxyInstance(
                    PersistentGroupInfoListener.getClassLoader(),
                    new Class<?>[]{PersistentGroupInfoListener},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getName().equals("onPersistentGroupInfoAvailable") && args != null) {
                                clearRememberedDevicesStep2(args[0]);
                            }
                            return null;
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Bug! onPersistentGroupInfoAvailable() " + e.toString());
            return;
        }
        // Request remembered groups. clearRememberedDevicesStep2() will be called
        try {
            requestPersistentGroupInfo.invoke(wifiP2pManager, wifiP2pChannel, persistentGroupInfoListener);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Bug! requestPersistentGroupInfo() failed because " + e.toString());
        }
        Log.d(LOG_TAG, "Request remembered Wi-Fi direct groups");
    }

    // parm is WifiP2pGroupList
    public void clearRememberedDevicesStep2(Object parm) {
        Class WifiP2pGroupList;              // android.net.wifi.p2p.WifiP2pGroupList
        final Method deletePersistentGroup;        // WifiP2pManager.deletePersistentGroup()
        Method getGroupList;                 // WifiP2pGroupList.getGroupList()
        final Method getNetworkId;                 // WifiP2pGroup.getNetworkId()

        // Get hidden object through Java reflection
        try {
            WifiP2pGroupList = Class.forName("android.net.wifi.p2p.WifiP2pGroupList");
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, "Bug! Class WifiP2pGroupList not found");
            return;
        }
        // Get hidden method through Java reflection
        try {
            deletePersistentGroup = WifiP2pManager.class.getMethod(
                    "deletePersistentGroup",
                    WifiP2pManager.Channel.class,
                    Integer.TYPE,
                    WifiP2pManager.ActionListener.class);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, "Bug! Method WifiP2pManager.deletePersistentGroup() not found");
            return;
        }
        // Get hidden method through Java reflection
        try {
            getGroupList = WifiP2pGroupList.getMethod("getGroupList");
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, "Bug! Method WifiP2pGroupList.getGroupList() not found");
            return;
        }
        // Get hidden method through Java reflection
        try {
            getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, "Bug! Method WifiP2pGroup.getNetworkId() not found");
            return;
        }
        // Called after a group is deleted
        WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, "A group is deleted");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(LOG_TAG, "A group is not deleted");
            }
        };

        // Delete all remembered Wi-Fi direct groups
        try {
            Collection<WifiP2pGroup> wifiP2pGroupList = (Collection<WifiP2pGroup>) getGroupList.invoke(parm);
            for (WifiP2pGroup group : wifiP2pGroupList) {
                int netId = (int) getNetworkId.invoke(group);
                Log.d(LOG_TAG, "Delete group " + String.valueOf(netId));
                deletePersistentGroup.invoke(wifiP2pManager, wifiP2pChannel, netId, actionListener);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Bug! getNetworkId() or deletePersistentGroup() failed because " + e.toString());
        }
    }

    // Finite state machine ----------------------------------------------------------------------

    /**
     * Change current state
     *
     * @param state The new state
     */
    private void changeState(WifiP2pState state) {
        WifiP2pState lastState = currentState;
        currentState = state;
        notifyActivityUpdateStatus();
        Log.d(LOG_TAG, lastState + " -> " + currentState);
    }

    // Getter and setter -------------------------------------------------------------------------
    public String getWifiP2pLocalDeviceName() {
        return wifiP2pLocalDeviceName;
    }

    public void setWifiP2pLocalDeviceName(String wifiP2pLocalDeviceName) {
        this.wifiP2pLocalDeviceName = wifiP2pLocalDeviceName;
    }

    public String getWifiP2pLocalDeviceMac() {
        return wifiP2pLocalDeviceMac;
    }

    public void setWifiP2pLocalDeviceMac(String wifiP2pLocalDeviceMac) {
        this.wifiP2pLocalDeviceMac = wifiP2pLocalDeviceMac;
    }

    public WifiP2pState getCurrentState() {
        return currentState;
    }

    public ArrayList<HashMap<String, String>> getNearbyDevices() {
        return nearbyDevices;
    }

    public ArrayList<HashMap<String, String>> getClients() {
        return clientDevices;
    }

    public InetAddress getWifiP2pLocalDeviceIp() {
        return wifiP2pLocalDeviceIp;
    }

    public int getLocalRegistrationPort() {
        return localRegistrationPort;
    }

    public void setPeerDiscoveryStopped(boolean peerDiscoveryStopped) {
        this.peerDiscoveryStopped = peerDiscoveryStopped;
    }
}
