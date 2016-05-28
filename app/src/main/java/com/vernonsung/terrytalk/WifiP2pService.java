package com.vernonsung.terrytalk;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class WifiP2pService extends Service
        implements WifiP2pManager.ActionListener,
                   WifiP2pManager.DnsSdServiceResponseListener,
                   WifiP2pManager.DnsSdTxtRecordListener,
//                   WifiP2pManager.PeerListListener,  // Vernon debug
                   Handler.Callback {
    public class LocalBinder extends Binder {
        WifiP2pService getService() {
            // Return this instance of service so clients can call public methods
            return WifiP2pService.this;
        }
    }

    public enum WifiP2pState {
        NON_INITIALIZED, INITIALIZING, INITIALIZED,
        DISCOVER_PEERS, ADD_SERVICE_REQUEST, DISCOVER_SERVICES, SEARCHING, REMOVE_SERVICE_REQUEST, STOP_PEER_DISCOVERY, SEARCH_STOPPED,
        REGISTRATION_SETUP, ADD_LOCAL_SERVICE, SHOUT, REMOVE_GROUP_SHOUT, CLEAR_REMEMBERED_GROUP_SHOUT, CLIENT_REJECTED, UPDATE_CLIENT_LIST, AUDIO_STREAM_SETUP_KING, DISCOVER_PEERS_SHOUT,
        REMOVE_GROUP_SILENT, REMOVE_LOCAL_SERVICE, CLEAR_CLIENT_LIST, AUDIO_STREAM_DISMISS_KING, REGISTRATION_DISMISS, SILENT, STOP_PEER_DISCOVERY_SHOUT,
        DISCOVER_PEERS_DATA, ADD_SERVICE_REQUEST_DATA, DISCOVER_SERVICES_DATA, DATA_REQUESTED,
        REMOVE_SERVICE_REQUEST_DATA, STOP_PEER_DISCOVERY_DATA, DATA_STOPPED,
        DISCOVER_PEERS_CONNECT, CONNECT, CONNECTING, CLEAR_REMEMBERED_GROUP_CONNECT, CANCEL_CONNECT, DISCONNECTED, STOP_PEER_DISCOVERY_CONNECT,
        AUDIO_STREAM_SETUP, CONNECTED, AUDIO_STREAM_DISMISS, REMOVE_GROUP, CONNECTION_END
    }

    private static final String LOG_TAG = "testtest";
    private final IBinder mBinder = new LocalBinder();
    // Start commands
    public static final String ACTION_PLAY = "com.vernonsung.testwifip2p.action.play";
    public static final String ACTION_STOP = "com.vernonsung.testwifip2p.action.stop";
    public static final String ACTION_CONNECT = "com.vernonsung.testwifip2p.action.connect";
    public static final String ACTION_LOCAL_SERVICE = "com.vernonsung.testwifip2p.action.localservice";
    public static final String INTENT_EXTRA_TARGET = "com.vernonsung.testwifip2p.TARGET";  // String
    // Local broadcast to send
    public static final String UPDATE_NEARBY_DEVICES_ACTION = "com.vernonsung.testwifip2p.action.update_nearby_devices";
    public static final String UPDATE_STATE_ACTION = "com.vernonsung.testwifip2p.action.update_status";
    public static final String UPDATE_IP_ACTION = "com.vernonsung.testwifip2p.action.update_ip";
    public static final String INTENT_EXTRA_STATE = "com.vernonsung.testwifip2p.state";  // enum WifiP2pState
    public static final String INTENT_EXTRA_IP = "com.vernonsung.testwifip2p.ip";  // String
    // Wi-Fi Direct
    public static final String WIFI_P2P_DNS_SD_SERVICE_TYPE = "_test-wifi-p2p._udp";
    private static final String WIFI_P2P_DNS_SD_SERVICE_DATA_PORT = "port";
    private static final String WIFI_LOCK = "wifiLock";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pReceiver wifiP2pReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdServiceRequest;
    private WifiP2pDnsSdServiceRequest wifiP2pDnsSdTxtRecordRequest;
    private WifiP2pDnsSdServiceInfo wifiP2pDnsSdServiceInfo;
    private WifiManager.WifiLock wifiLock = null;
    private String wifiP2pDeviceName;
    private String targetName;
    private String targetMac;
    private InetAddress wifiP2pDeviceIp;
    // Nearby devices list -----------------------------------------------------------------------
    public static final String MAP_ID_DEVICE_NAME = "deviceName";
    public static final String MAP_ID_STATUS = "status";
    public static final String MAP_ID_MAC = "mac";
    public static final String MAP_ID_REGISTRATION_PORT = "port";
    private ArrayList<HashMap<String, String>> nearbyDevices;
    private Handler retryHandler;
    private static final int HANDLER_WHAT = 12;
    private static final int HANDLER_DELAY_MS = 30000;
    // Finite state machine ----------------------------------------------------------------------
    private WifiP2pState targetState = WifiP2pState.SEARCHING;  // Default
    private WifiP2pState currentState = WifiP2pState.NON_INITIALIZED;
    private boolean serviceAnnounced = false;
    private boolean serviceConnedted = false;  // Indicate whether it can removeGroup()
    // Audio stream ------------------------------------------------------------------------------
    // TODO: Check whether to remove this variable
//    private InetSocketAddress localSocket;
    // Retistration ------------------------------------------------------------------------------
    private SocketServerThreads socketServerThreads = null;
    private Thread registrationThread = null;
    private InetAddress serverIp;
    private int serverPort;

    // Vernon debug
//    private static final String Z3_DISPLAY = "Z3";
//    private static final String M4_DISPLAY = "M4";
//    private static final String NOTE_12_2_DISPLAY = "Note Pro 12.2";
//    private static final String Z3_MAC = "86:8e:df:79:08:d8";
//    private static final String M4_MAC = "5a:48:22:62:af:30";
//    private static final String NOTE_12_2_MAC = "b6:3a:28:d6:7c:4e";

    public WifiP2pService() {
        // Variables
        nearbyDevices = new ArrayList<>();
        // Set a timer to retry some parts of the finite state machine in a regular interval
        retryHandler = new Handler(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_PLAY:
                onActionPlay();
                break;
            case ACTION_STOP:
                onActionStop();
                break;
            case ACTION_CONNECT:
                onActionConnect(intent);
                break;
            case ACTION_LOCAL_SERVICE:
                onActionLocalService();
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

//    @Override
//    public void onPeersAvailable(WifiP2pDeviceList peers) {
//        nearbyDevices.clear();
//        Collection<WifiP2pDevice> devices = peers.getDeviceList();
//        for (WifiP2pDevice device : devices) {
//            HashMap<String, String> a = new HashMap<>();
//            a.put(MAP_ID_DEVICE_NAME, device.deviceName);
//            switch (device.status) {
//                case WifiP2pDevice.AVAILABLE:
//                    a.put(MAP_ID_STATUS, getString(R.string.available));
//                    break;
//                case WifiP2pDevice.CONNECTED:
//                    a.put(MAP_ID_STATUS, getString(R.string.connected));
//                    break;
//                case WifiP2pDevice.FAILED:
//                    a.put(MAP_ID_STATUS, getString(R.string.failed));
//                    break;
//                case WifiP2pDevice.INVITED:
//                    a.put(MAP_ID_STATUS, getString(R.string.invited));
//                    break;
//                case WifiP2pDevice.UNAVAILABLE:
//                    a.put(MAP_ID_STATUS, getString(R.string.unavailable));
//                    break;
//            }
//            nearbyDevices.add(a);
//        }
//
//        // Notify activity to update nearby device list
//        Intent intent = new Intent(UPDATE_NEARBY_DEVICES_ACTION);
//        intent.putExtra(INTENT_EXTRA_DEVICELIST, peers);
//        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
//    }

    // implement WifiP2pManager.DnsSdServiceResponseListener
    @Override
    public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
        // Store the nearby device's name
        HashMap<String, String> device = new HashMap<>();
//        device.put(MAP_ID_DEVICE_NAME, srcDevice.deviceName);  // Sometimes srcDevice.deviceName is empty. Use instanceName instead
        device.put(MAP_ID_DEVICE_NAME, instanceName);
        device.put(MAP_ID_STATUS, getDeviceState(srcDevice));
        device.put(MAP_ID_MAC, srcDevice.deviceAddress);
        nearbyDevices.add(device);
        notifyActivityUpdateDeviceList();
        Log.d(LOG_TAG, "Found device " + instanceName + " " + srcDevice.deviceAddress);
        Toast.makeText(this, "Found device " + instanceName + " " + srcDevice.deviceAddress, Toast.LENGTH_LONG).show();
        Log.d(LOG_TAG, "There are " + nearbyDevices.size() + " devices now");
    }

    // implement WifiP2pManager.DnsSdTxtRecordListener
    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
        // Sometime srcDevice.deviceName is empty
        if (srcDevice.deviceName.isEmpty()) {
            Log.d(LOG_TAG, "Device name is empty, restart requesting data");
            goToNextState();
            return;
        }

        HashMap<String, String> device = null;
        // Search for existing device
        for (HashMap<String, String> d: nearbyDevices) {
            if (Objects.equals(d.get(MAP_ID_DEVICE_NAME), srcDevice.deviceName)) {
                device = d;
            }
        }
        // Add new device
        if (device == null) {
            device = new HashMap<>();
            device.put(MAP_ID_DEVICE_NAME, srcDevice.deviceName);
            device.put(MAP_ID_STATUS, getDeviceState(srcDevice));
            device.put(MAP_ID_MAC, srcDevice.deviceAddress);
            nearbyDevices.add(device);
        }
        // Put ip and port into device status
        targetMac = srcDevice.deviceAddress;
        String port = txtRecordMap.get(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT);
        String status = device.get(MAP_ID_STATUS);
        if (status != null && !status.contains(port)) {
            status += " " + ":" + port;
            device.put(MAP_ID_STATUS, status);
        }
        Log.d(LOG_TAG, targetMac + " " + ":" + port);
        Toast.makeText(this, targetMac + " " + ":" + port, Toast.LENGTH_LONG).show();
        // Store registration port to register later
        device.put(MAP_ID_REGISTRATION_PORT, port);
        // Notify the activity to update the list
        notifyActivityUpdateDeviceList();
        // Change state
        WifiP2pState lastTargetState = targetState;
        setTargetState(WifiP2pState.CONNECTING);
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
        // Terminate this service
        stopService(true);
    }

    @Override
    public boolean handleMessage(Message msg) {
        // Retry current part in the finite state machine if current part has no respond for a while
        if ((targetState == WifiP2pState.SEARCHING ||
                targetState == WifiP2pState.DATA_REQUESTED ||
                targetState == WifiP2pState.CONNECTING) &&
                currentState == targetState) {
            goToNextState();
        }
        return false;
    }

    // After receiving an intent with a "PLAY" action
    private void onActionPlay() {
        // Make service running until manually stop it
        turnIntoForeground();
        if (currentState == WifiP2pState.NON_INITIALIZED) {
            goToNextState();
        }
    }

    // After receiving an intent with a "STOP" action
    private void onActionStop() {
        stopService(false);
    }

    // After receiving an intent with a "CONNECT" action
    private void onActionConnect(Intent intent) {
        targetName = intent.getStringExtra(INTENT_EXTRA_TARGET);
        setTargetState(WifiP2pState.DATA_REQUESTED);
        goToNextState();
    }

    // After receiving an intent with a "CONNECT" action
    private void onActionLocalService() {
        localService();
    }

    // Turn into a foreground service. Provide a running notification
    private void turnIntoForeground() {
        int NOTIFICATION_ID = 1;
        // assign the song name to songName
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, WifiP2pActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.terrytalk_is_still_running))
                .setSmallIcon(R.mipmap.ic_launcher)
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

    // Notify the activity to update status
    private void notifyActivityUpdateIp() {
        String ip;
        if (wifiP2pDeviceIp == null) {
            ip = "";
        } else {
            ip = wifiP2pDeviceIp.getHostAddress();
        }
        Intent intent = new Intent(UPDATE_IP_ACTION);
        intent.putExtra(INTENT_EXTRA_IP, ip);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Part 1: Initial Wi-Fi P2P and necessary data-----------------------------------------------
    private void initialAll() {
        initialAudioStream();
        initializeWifiP2p();

        // Vernon debug
//        vernonTestFunction();
        // Change state
        goToNextState();
    }

    private void initialAudioStream() {
        // TODO: Merge TestAudioStream to use real port
        // Vernon debug. Random a socket.
//        Random random = new Random();
//        int random255 = random.nextInt(254) + 1;
//        int random65535 = random.nextInt(65534) + 1;
//        localSocket = new InetSocketAddress("192.168.0." + String.valueOf(random255), random65535);
    }

    private void initializeWifiP2p() {
        wifiP2pManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
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
        // The request to discovery nearby devices running this APP
        wifiP2pDnsSdServiceRequest = WifiP2pDnsSdServiceRequest.newInstance(WIFI_P2P_DNS_SD_SERVICE_TYPE);
        // Response the discovery requests from nearby devices running this APP
        wifiP2pManager.setDnsSdResponseListeners(wifiP2pChannel, this, this);

        // To ensure Wi-Fi is ON
        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
        wifiLock.acquire();
    }

    // Vernon debug
//    public void vernonTestFunction() {
//        // Vernon debug
//        wifiP2pManager.discoverPeers(wifiP2pChannel, new WifiP2pManager.ActionListener() {
//            @Override
//            public void onSuccess() {
//                Toast.makeText(WifiP2pActivity.this, "Discover peers", Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onFailure(int reason) {
//                Toast.makeText(WifiP2pActivity.this, WifiP2pActivity.this.getActionFailReason(reason), Toast.LENGTH_SHORT).show();
//            }
//        });
//        wifiP2pManager.addLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, null);
//        wifiP2pManager.addServiceRequest(wifiP2pChannel, wifiP2pDnsSdServiceRequest, new WifiP2pManager.ActionListener() {
//            @Override
//            public void onSuccess() {
//                try {
//                    Thread.sleep(100, 0);
//                } catch (Exception e) {
//                    Toast.makeText(WifiP2pActivity.this, "Sleep fail", Toast.LENGTH_SHORT).show();
//                }
//                wifiP2pManager.discoverServices(wifiP2pChannel, null);
//            }
//
//            @Override
//            public void onFailure(int reason) {
//
//            }
//        });
//    }

    private void initialized() {
        // Change state
        goToNextState();
    }

    private void nonInitialized() {
        // Stop receiving intents related to Wi-Fi P2p
        try {
            unregisterReceiver(wifiP2pReceiver);
        } catch (IllegalArgumentException e) {
            Log.d(LOG_TAG, "Receiver is already unregistered");
        }
        Log.d(LOG_TAG, "Broadcast receiver unregistered");
        // Release Wi-Fi lock
        if (wifiLock != null) {
            wifiLock.release();
        }
        // Stop service
        stopSelf();
    }

    // When buttonStop is clicked
    private void stopService(boolean forceMove) {
        retryHandler.removeMessages(HANDLER_WHAT);
        // Change state
        if (currentState == WifiP2pState.NON_INITIALIZED) {
            stopSelf();
            return;
        }
        // Go towards NON_INITIALIZED
        WifiP2pState lastTargetState = targetState;
        setTargetState(WifiP2pState.NON_INITIALIZED);
        // Make the finite state machine move if it stops
        if (forceMove || lastTargetState == currentState) {
            goToNextState();
        }
    }

    // Part 2: Announce this device is running this APP-------------------------------------------
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
            setTargetState(WifiP2pState.NON_INITIALIZED);
            goToNextState();
            return;
        }
        serverPort = serverSocket.getLocalPort();
        socketServerThreads = new SocketServerThreads(serverSocket);
        registrationThread = new Thread(socketServerThreads, "RegistrationServer");
        registrationThread.start();
        goToNextState();
    }

    // When the command succeeds, onSuccess() will be called
    private void announceService() {
        // Put socket information into Wi-Fi P2P service info
        Map<String, String> data = new HashMap<>();
        data.put(WIFI_P2P_DNS_SD_SERVICE_DATA_PORT, String.valueOf(serverPort));
        // Random a name if it doesn't exist
        if (wifiP2pDeviceName == null) {
            wifiP2pDeviceName = "_test" + String.valueOf(new Random().nextInt(10));
        }
        // Create a service with the name
        wifiP2pDnsSdServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(wifiP2pDeviceName, WIFI_P2P_DNS_SD_SERVICE_TYPE, data);
        // The service information representing this APP------------------------------------------

        wifiP2pManager.addLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    private void serviceAnnounced() {
        serviceAnnounced = true;
        Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show();
        // Check registration socket thread
        if (!registrationThread.isAlive()) {
            Log.d(LOG_TAG, "Registration thread terminated accidentally.");
            setTargetState(WifiP2pState.NON_INITIALIZED);
            Toast.makeText(this, R.string.registration_service_terminated_accidentally, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.service_created, Toast.LENGTH_SHORT).show();
        }
        // Let the finite state machine keep going
        if (targetState != currentState) {
            goToNextState();
        }
    }

    private void removeWifiP2pGroupShout() {
        if (serviceConnedted) {
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
        } else {
            goToNextState();
        }
    }

    private void clientRejected() {
        setTargetState(WifiP2pState.SHOUT);
        goToNextState();
    }

    private void removeWifiP2pGroupSilent() {
        if (serviceConnedted) {
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
        } else {
            goToNextState();
        }
    }

    // Hide this device is running this APP
    // When the command succeeds, onSuccess() will be called
    private void hideService() {
        wifiP2pManager.removeLocalService(wifiP2pChannel, wifiP2pDnsSdServiceInfo, this);
    }

    private void serviceHided() {
        serviceAnnounced = false;
        Toast.makeText(this, R.string.service_removed, Toast.LENGTH_SHORT).show();
        // Change state
        if (currentState == targetState) {
            setTargetState(WifiP2pState.SEARCHING);
        }
        goToNextState();
    }

    private void updateClientList() {
        // TODO: After receiving WIFI_P2P_CONNECTION_CHANGED_ACTION broadcast intent, show the clients from extra parameter groupInfo on the listView in the activity
        goToNextState();
    }

    private void audioStreamSetupKing() {
        // TODO: Setup the audio stream for the king
        goToNextState();
    }

    private void discoverPeersShout() {
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
    }

    private void clearClientList() {
        // TODO: Clear the listView in the activity
        goToNextState();
    }

    private void audioStreamDismissKing() {
        // TODO: Dismiss the king's audio streams
        goToNextState();
    }

    // Stop registration socket server thread as well as clear all audio streams
    private void registrationDismiss() {
        socketServerThreads.setToQuit(true);
        goToNextState();
    }

    private void stopPeerDiscoveryShout() {
        wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
    }

    // When buttonShout clicked
    private void localService() {
        WifiP2pState lastTargetState = targetState;
        if (serviceAnnounced) {
            setTargetState(WifiP2pState.SILENT);
        } else {
            setTargetState(WifiP2pState.SHOUT);
        }
        // Make the finite state machine move if it stops
        if (lastTargetState == currentState) {
            goToNextState();
        }
    }

    // When WifiP2pReceiver receives WIFI_P2P_CONNECTION_CHANGED_ACTION
    public void connectionChangeActionHandler(NetworkInfo networkInfo, WifiP2pInfo wifiP2pInfo, WifiP2pGroup groupInfo) {
        // Check whether the connection is established or broken
        if (!networkInfo.isConnected()) {
            Log.d(LOG_TAG, "Connection is broken");
            Toast.makeText(this, "Connection is broken", Toast.LENGTH_SHORT).show();
            // Change state
            if (serviceAnnounced) {  // Check serviceAnnounced instead of (currentState == SHOUT) because it may be in other state of part "shouted"
                // I host the service.
                // Note: If client A disconnects while client B is still connected, networkInfo.isConnected() is true
                Log.d(LOG_TAG, "All clients disconnected");
                serviceConnedted = false;
                // Clear clients on list view
                nearbyDevices.clear();
                notifyActivityUpdateDeviceList();
                // Change state
                if (currentState == WifiP2pState.SHOUT) {
                    goToNextState();
                }
            } else if (serviceConnedted) {
                // I'm a client. Search nearby devices again.
                Log.d(LOG_TAG, "I'm a client. Search nearby devices again.");
                serviceConnedted = false;
                // Remove IP
                wifiP2pDeviceIp = null;
                notifyActivityUpdateIp();
                // Search nearby devices again if the finite state machine is stopped and the connection is not canceled by me
                if (currentState == targetState) {
                    setTargetState(WifiP2pState.SEARCHING);
                    goToNextState();
                }
            }
            // It's not necessary to read group and P2P info
            return;
        }

        // Connection established
        serviceConnedted = true;

        // Store group owner IP for registration
        serverIp = wifiP2pInfo.groupOwnerAddress;
        String groupOwnerIp = wifiP2pInfo.groupOwnerAddress.getHostAddress();
        Log.d(LOG_TAG, "Connection is established. Group owner IP " + groupOwnerIp);
        Toast.makeText(this, "Group owner IP " + groupOwnerIp, Toast.LENGTH_SHORT).show();

        // Get Wi-Fi Direct interface IP
        String interfaceName = groupInfo.getInterface();
        wifiP2pDeviceIp = getIpByInterface(interfaceName);
        if (wifiP2pDeviceIp == null) {
            Log.e(LOG_TAG, "Wi-Fi direct IP not found");
            Toast.makeText(this, "Wi-Fi direct IP not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(LOG_TAG, "Wi-Fi direct IP " + wifiP2pDeviceIp.getHostAddress());
        notifyActivityUpdateIp();

        // Get group clients
        if (currentState == WifiP2pState.SHOUT) {
            // I host the service
            if (groupInfo.isGroupOwner()) {
                // I'm the group owner. Show clients on list view
                Collection<WifiP2pDevice> devices = groupInfo.getClientList();
                nearbyDevices.clear();
                for (WifiP2pDevice device : devices) {
                    HashMap<String, String> a = new HashMap<>();
                    a.put(MAP_ID_DEVICE_NAME, device.deviceName);
                    a.put(MAP_ID_STATUS, getDeviceState(device));
                    nearbyDevices.add(a);
                }
                notifyActivityUpdateDeviceList();
                // Change state
                goToNextState();
            } else {
                // I should be the group owner. Disconnect
                Log.e(LOG_TAG, "I should be the group owner because I host the service");
                Toast.makeText(this, "I should be the group owner because I host the service", Toast.LENGTH_SHORT).show();
                // Change state
                WifiP2pState lastTargetState = targetState;
                setTargetState(WifiP2pState.CLIENT_REJECTED);
                // Make the finite state machine move if it stops
                if (lastTargetState == currentState) {
                    goToNextState();
                }
            }
        } else if (groupInfo.isGroupOwner()) {
            // I'm a client. I should not be the group owner. Disconnect
            Log.e(LOG_TAG, "I'm a client. I should not be the group owner. Disconnect");
            setTargetState(WifiP2pState.CONNECTION_END);
            goToNextState();
        } else {
            // I'm a client and not a group owner. Well done
            // Set the group owner's status to connected
            for (HashMap<String, String> device : nearbyDevices) {
                if (device.get(MAP_ID_DEVICE_NAME).equals(targetName)) {
                    // Change device status
                    try {
                        String status = device.get(MAP_ID_STATUS);
                        status = status.replaceFirst("^\\w+\\s", getDeviceState(groupInfo.getOwner()) + " ");
                        device.put(MAP_ID_STATUS, status);
                        notifyActivityUpdateDeviceList();
                    } catch (Exception e) {
                        // String.replaceFirst() may throw exceptions
                        e.printStackTrace();
                        Log.e(LOG_TAG, "replace device status failed because " + e.toString());
                    }
                    // Store king's registration port to register later
                    try {
                        serverPort = Integer.parseInt(device.get(MAP_ID_REGISTRATION_PORT));
                    } catch (NumberFormatException e) {
                        Log.e(LOG_TAG, "Server registration port " + device.get(MAP_ID_REGISTRATION_PORT) + " is not valid");
                        serverPort = 0;
                    }
                    break;
                }
            }
            // Change state
            setTargetState(WifiP2pState.CONNECTED);
            goToNextState();
        }
    }

    private InetAddress getIpByInterface(String interfaceName) {
        try {
//            ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
//            for (NetworkInterface i : interfaces) {
//                Log.d(LOG_TAG, "NIC " + i.getName() + " v.s " + interfaceName);
//                if (i.getName().equals(interfaceName)) {
//                    ArrayList<InetAddress> addresses = Collections.list(i.getInetAddresses());
//                    for (InetAddress a : addresses) {
//                        if (!a.isLoopbackAddress() && a instanceof Inet4Address && a.getHostAddress().startsWith("192")) {
//                            return a;
//                        } else {
//                            Log.d(LOG_TAG, "IP " + a.getHostAddress() + " is found but not what we want");
//                        }
//                    }
//                }
//            }
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

    // Part 3: Discover the devices running this APP----------------------------------------------
    // When the command succeeds, onSuccess() will be called
    private void discoverNearbyDevices() {
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
    }

    // When the command succeeds, onPeersAvailable() will be called
//    private void discoverNearbyDevicesStep2() {
//        wifiP2pManager.requestPeers(wifiP2pChannel, this);
//    }

    // When the command succeeds, onSuccess() will be called
    private void discoverNearbyServices() {
        // Remove all out of date devices
        nearbyDevices.clear();
        notifyActivityUpdateDeviceList();
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
        } else {
            // Set timer to retry requesting data after an interval
            retryHandler.sendEmptyMessageDelayed(HANDLER_WHAT, HANDLER_DELAY_MS);
        }
    }

    // Restart Wi-Fi Direct peer discover when Android system stop it unexpectedly
    public void restartPeerDiscoverInAccident() {
//        if (currentState == WifiP2pState.SEARCHING ||
//                currentState == WifiP2pState.DATA_REQUESTED ||
//                currentState == WifiP2pState.CONNECTING) {
//            wifiP2pManager.discoverPeers(wifiP2pChannel, null);
//            goToNextState();
//            Log.d(LOG_TAG, "Finite state machine part restarts");
//        } else {
        Log.d(LOG_TAG, "Nearby device discovery stops");
//        }
    }

    // Stop discovering nearby devices running this APP
    // When the command succeeds, onSuccess() will be called
    private void stopDiscoverNearbyServices() {
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
        } else {
            // Set timer to retry requesting data after an interval
            retryHandler.sendEmptyMessageDelayed(HANDLER_WHAT, HANDLER_DELAY_MS);
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
        wifiP2pManager.discoverPeers(wifiP2pChannel, this);
    }

    // When the command succeeds, onSuccess() will be called
    private void connectToTargetStep2() {
        WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
        wifiP2pConfig.deviceAddress = targetMac;
        wifiP2pConfig.groupOwnerIntent = 0;
//        wifiP2pConfig.wps.setup = WpsInfo.PBC;  // TODO: Try other settings
        Log.d(LOG_TAG, "Connect to " + wifiP2pConfig.deviceAddress);
        Toast.makeText(this, "Connect to " + wifiP2pConfig.deviceAddress, Toast.LENGTH_SHORT).show();
        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig, this);
    }

    // Move to next state after receiving WIFI_P2P_CONNECTION_CHANGED_ACTION
    private void connecting() {
        if (targetState != currentState) {
            goToNextState();
        } else {
            // Set timer to retry requesting data after an interval
            retryHandler.sendEmptyMessageDelayed(HANDLER_WHAT, HANDLER_DELAY_MS);
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

    private void stopPeerDiscoveryConnect() {
        wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, this);
    }

    // Disconnect with the target device
    // When the command succeeds, onSuccess() will be called
    private void removeWifiP2pGroup() {
        if (serviceConnedted) {
            wifiP2pManager.removeGroup(wifiP2pChannel, this);
        } else {
            goToNextState();
        }
    }

    private void connectionEnd() {
        if (targetState == currentState) {
            // From CONNECTING state
            setTargetState(WifiP2pState.SEARCHING);
        }
        goToNextState();
    }

    // Step 6: Setup audio stream
    private void audioStreamSetup() {
        // TODO: Create a new audio stream and send the port to the server
        int fakePort = 54321;
        // Register to the king
        SocketClientTask socketClientTask = new SocketClientTask(
                this,
                new InetSocketAddress(wifiP2pDeviceIp, 0),
                new InetSocketAddress(serverIp, serverPort),
                fakePort);  // Vernon debug. Fake port. TODO: Replace by real port
        socketClientTask.execute();
        // After socketClientTask finished, audioStreamSetupPart2() will be called
    }

    // Called after socketClientTask finished
    public void audioStreamSetupPart2(int remoteAudioStreamPort) {
        if (remoteAudioStreamPort == 0) {
            Log.d(LOG_TAG, "Registration failed");
            setTargetState(WifiP2pState.CONNECTION_END);
        } else {
            // TODO: Associate audio stream to kings IP and port
        }
        goToNextState();
    }

    private void audioStreamDismiss() {
        // TODO: Deconstruct the audio stream
        goToNextState();
    }

    // Step 6: Clear remembered Wi-Fi direct group------------------------------------------------
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
                                goToNextState();
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
    // Go to next state when current state action is successful
    private void goToNextState() {
        WifiP2pState lastState = currentState;
        switch (currentState) {
            case NON_INITIALIZED:
                currentState = WifiP2pState.INITIALIZING;
                break;
            case INITIALIZING:
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
                currentState = WifiP2pState.SEARCH_STOPPED;
                break;
            case SEARCH_STOPPED:
                if (targetState == WifiP2pState.SHOUT) {
                    currentState = WifiP2pState.REGISTRATION_SETUP;
                } else if (targetState == WifiP2pState.DATA_REQUESTED) {
                    currentState = WifiP2pState.DISCOVER_PEERS_DATA;
                } else {  // if (targetState == WifiP2pState.SEARCHING || targetState == WifiP2pState.NON_INITIALIZED) {
                    currentState = WifiP2pState.STOP_PEER_DISCOVERY;
                }
                break;
            case STOP_PEER_DISCOVERY:
                if (targetState == WifiP2pState.SEARCHING) {
                    currentState = WifiP2pState.DISCOVER_PEERS;
                } else {  // if (targetState == WifiP2pState.NON_INITIALIZED)
                    currentState = WifiP2pState.NON_INITIALIZED;
                }
                break;
            case REGISTRATION_SETUP:
                currentState = WifiP2pState.ADD_LOCAL_SERVICE;
                break;
            case ADD_LOCAL_SERVICE:
                currentState = WifiP2pState.SHOUT;
                break;
            case SHOUT:
                if (targetState == WifiP2pState.CLIENT_REJECTED) {
                    currentState = WifiP2pState.REMOVE_GROUP_SHOUT;
                } else if (targetState == WifiP2pState.SHOUT) {
                    currentState = WifiP2pState.UPDATE_CLIENT_LIST;
                } else {
                    currentState = WifiP2pState.REMOVE_GROUP_SILENT;
                }
                break;
            case REMOVE_GROUP_SHOUT:
                currentState = WifiP2pState.CLEAR_REMEMBERED_GROUP_SHOUT;
                break;
            case CLEAR_REMEMBERED_GROUP_SHOUT:
                currentState = WifiP2pState.CLIENT_REJECTED;
                break;
            case CLIENT_REJECTED:
                currentState = WifiP2pState.DISCOVER_PEERS_SHOUT;
                break;
            case UPDATE_CLIENT_LIST:
                currentState = WifiP2pState.AUDIO_STREAM_SETUP_KING;
                break;
            case AUDIO_STREAM_SETUP_KING:
                currentState = WifiP2pState.DISCOVER_PEERS_SHOUT;
                break;
            case DISCOVER_PEERS_SHOUT:
                currentState = WifiP2pState.SHOUT;
                break;
            case REMOVE_GROUP_SILENT:
                currentState = WifiP2pState.REMOVE_LOCAL_SERVICE;
                break;
            case REMOVE_LOCAL_SERVICE:
                currentState = WifiP2pState.CLEAR_CLIENT_LIST;
                break;
            case CLEAR_CLIENT_LIST:
                currentState = WifiP2pState.AUDIO_STREAM_DISMISS_KING;
                break;
            case AUDIO_STREAM_DISMISS_KING:
                currentState = WifiP2pState.REGISTRATION_DISMISS;
                break;
            case REGISTRATION_DISMISS:
                currentState = WifiP2pState.SILENT;
                break;
            case SILENT:
                if (targetState == WifiP2pState.NON_INITIALIZED) {
                    currentState = WifiP2pState.STOP_PEER_DISCOVERY_SHOUT;
                } else {
                    currentState = WifiP2pState.DISCOVER_PEERS;
                }
                break;
            case STOP_PEER_DISCOVERY_SHOUT:
                currentState = WifiP2pState.NON_INITIALIZED;
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
                if (targetState == WifiP2pState.CONNECTING) {
                    currentState = WifiP2pState.DATA_STOPPED;
                } else {  // DATA_REQUESTED, NON_INITIALIZED
                    currentState = WifiP2pState.STOP_PEER_DISCOVERY_DATA;
                }
                break;
            case STOP_PEER_DISCOVERY_DATA:
                if (targetState == WifiP2pState.NON_INITIALIZED) {
                    currentState = WifiP2pState.NON_INITIALIZED;
                } else {  // DATA_REQUESTED
                    currentState = WifiP2pState.DATA_STOPPED;
                }
                break;
            case DATA_STOPPED:
                if (targetState == WifiP2pState.DATA_REQUESTED) {
                    currentState = WifiP2pState.DISCOVER_PEERS_DATA;
                } else if (targetState == WifiP2pState.SEARCHING) {
                    currentState = WifiP2pState.INITIALIZED;
                } else {  // CONNECTING
                    currentState = WifiP2pState.CONNECT;
                }
                break;
            case DISCOVER_PEERS_CONNECT:
                currentState = WifiP2pState.CONNECT;
                break;
            case CONNECT:
                currentState = WifiP2pState.CONNECTING;
                break;
            case CONNECTING:
                if (targetState == WifiP2pState.CONNECTED) {
                    currentState = WifiP2pState.AUDIO_STREAM_SETUP;
                } else if (targetState == WifiP2pState.CONNECTION_END){
                    currentState = WifiP2pState.CLEAR_REMEMBERED_GROUP_CONNECT;
                } else {  // CONNECTING, SEARCHING
                    currentState = WifiP2pState.CANCEL_CONNECT;
                }
                break;
            case CLEAR_REMEMBERED_GROUP_CONNECT:
                currentState = WifiP2pState.REMOVE_GROUP;
                break;
            case CANCEL_CONNECT:
                currentState = WifiP2pState.DISCONNECTED;
                break;
            case DISCONNECTED:
                if (targetState == WifiP2pState.CONNECTING) {
                    currentState = WifiP2pState.DISCOVER_PEERS_CONNECT;
                } else { // SEARCHING, NON_INITIALIZED
                    currentState = WifiP2pState.STOP_PEER_DISCOVERY_CONNECT;
                }
                break;
            case STOP_PEER_DISCOVERY_CONNECT:
                if (targetState == WifiP2pState.SEARCHING) {
                    currentState = WifiP2pState.INITIALIZED;
                } else {  // NON_INITIALIZED
                    currentState = WifiP2pState.NON_INITIALIZED;
                }
                break;
            case AUDIO_STREAM_SETUP:
                currentState = WifiP2pState.CONNECTED;
                break;
            case CONNECTED:
                currentState = WifiP2pState.AUDIO_STREAM_DISMISS;
                break;
            case AUDIO_STREAM_DISMISS:
                currentState = WifiP2pState.REMOVE_GROUP;
                break;
            case REMOVE_GROUP:
                currentState = WifiP2pState.CONNECTION_END;
                break;
            case CONNECTION_END:
                if (targetState == WifiP2pState.NON_INITIALIZED) {
                    currentState = WifiP2pState.NON_INITIALIZED;
                } else {  // if (targetState == SEARCHING)
                    currentState = WifiP2pState.INITIALIZED;
                }
                break;
            default:
                Log.e(LOG_TAG, "Unhandled state " + currentState);
        }
        notifyActivityUpdateStatus();
        Log.d(LOG_TAG, lastState + " -> " + currentState);

        // Remove any retry event
        retryHandler.removeMessages(HANDLER_WHAT);

        // Execute next stat action
        doStateAction();
    }

    // Go to last state when current state action failed
    private void goToPreviousState() {
        // TODO: switch (currentState)
    }

    // Do action according to current state
    private void doStateAction() {
        switch (currentState) {
            case NON_INITIALIZED:
                nonInitialized();
                break;
            case INITIALIZING:
                initialAll();
                break;
            case INITIALIZED:
                initialized();
                break;
            case DISCOVER_PEERS:
                discoverNearbyDevices();
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
                stopDiscoverNearbyServices();
                break;
            case STOP_PEER_DISCOVERY:
                stopDiscoverNearbyDevices();
                break;
            case SEARCH_STOPPED:
                goToNextState();
                break;
            case REGISTRATION_SETUP:
                registrationSetup();
                break;
            case ADD_LOCAL_SERVICE:
                announceService();
                break;
            case SHOUT:
                serviceAnnounced();
                break;
            case REMOVE_GROUP_SHOUT:
                removeWifiP2pGroupShout();
                break;
            case CLEAR_REMEMBERED_GROUP_SHOUT:
                clearRememberedDevicesStep1();
                break;
            case CLIENT_REJECTED:
                clientRejected();
                break;
            case UPDATE_CLIENT_LIST:
                updateClientList();
                break;
            case AUDIO_STREAM_SETUP_KING:
                audioStreamSetupKing();
                break;
            case DISCOVER_PEERS_SHOUT:
                discoverPeersShout();
                break;
            case REMOVE_GROUP_SILENT:
                removeWifiP2pGroupSilent();
                break;
            case REMOVE_LOCAL_SERVICE:
                hideService();
                break;
            case CLEAR_CLIENT_LIST:
                clearClientList();
                break;
            case AUDIO_STREAM_DISMISS_KING:
                audioStreamDismissKing();
                break;
            case REGISTRATION_DISMISS:
                registrationDismiss();
                break;
            case SILENT:
                serviceHided();
                break;
            case STOP_PEER_DISCOVERY_SHOUT:
                stopPeerDiscoveryShout();
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
            case DISCOVER_PEERS_CONNECT:
                connectToTarget();
                break;
            case CONNECT:
                connectToTargetStep2();
                break;
            case CONNECTING:
                connecting();
                break;
            case CLEAR_REMEMBERED_GROUP_CONNECT:
                clearRememberedDevicesStep1();
                break;
            case CANCEL_CONNECT:
                stopOnGoingConnectRequest();
                break;
            case DISCONNECTED:
                goToNextState();
                break;
            case STOP_PEER_DISCOVERY_CONNECT:
                stopPeerDiscoveryConnect();
                break;
            case AUDIO_STREAM_SETUP:
                audioStreamSetup();
                break;
            case CONNECTED:
                connected();
                break;
            case AUDIO_STREAM_DISMISS:
                audioStreamDismiss();
                break;
            case REMOVE_GROUP:
                removeWifiP2pGroup();
                break;
            case CONNECTION_END:
                connectionEnd();
                break;
            default:
                Log.e(LOG_TAG, "State " + currentState + " has no action");
        }
    }

    // Getter and setter -------------------------------------------------------------------------
    private void setTargetState(WifiP2pState state) {
        Log.d(LOG_TAG, "Target state " + targetState + " -> " + state);
        targetState = state;
    }

    public String getWifiP2pDeviceName() {
        return wifiP2pDeviceName;
    }

    public void setWifiP2pDeviceName(String wifiP2pDeviceName) {
        this.wifiP2pDeviceName = wifiP2pDeviceName;
    }

    public WifiP2pState getCurrentState() {
        return currentState;
    }

    public ArrayList<HashMap<String, String>> getNearbyDevices() {
        return nearbyDevices;
    }

    public InetAddress getWifiP2pDeviceIp() {
        return wifiP2pDeviceIp;
    }
}
