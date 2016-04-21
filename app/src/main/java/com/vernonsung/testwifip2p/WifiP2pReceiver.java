package com.vernonsung.testwifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;

public class WifiP2pReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "testtest";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pService wifiP2pService;  // APP activity to deal Wi-Fi P2P

    public WifiP2pReceiver(WifiP2pManager wifiP2pManager,
                           WifiP2pManager.Channel wifiP2pChannel,
                           WifiP2pService wifiP2pService) {
        this.wifiP2pManager = wifiP2pManager;
        this.wifiP2pChannel = wifiP2pChannel;
        this.wifiP2pService = wifiP2pService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // TODO: wifiP2pStateChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            wifiP2pPeersChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            wifiP2pConnectionChangeActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
             wifiP2pThisDeviceChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            wifiP2pDiscoveryChangedActionHandler(intent);
        } else {
            // Unhandled action
            Log.e(LOG_TAG, "WifiP2pReceiver received an unhandled action " + action);
        }
    }

    // After receiving an intent with action WIFI_P2P_PEERS_CHANGED_ACTION,
    // discover the devices running this APP
    private void wifiP2pPeersChangedActionHandler(Intent intent) {
        Log.d(LOG_TAG, "Nearby devices changed");
        // Vernon debug
        wifiP2pManager.requestPeers(wifiP2pChannel, wifiP2pService);
    }

    private void wifiP2pConnectionChangeActionHandler(Intent intent) {
        // Get network info to check the connection is established or broken
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        // Get group owner IP from P2P info
        WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        // Get group clients from P2P group info
        WifiP2pGroup groupInfo = null;
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentApiVersion < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // < 4.3 API 18
            Log.d(LOG_TAG, "SDK < 4.3 API 18, don't show client list");
//            wifiP2pManager.requestGroupInfo(wifiP2pChannel, nGroupInfoListener);
        } else {
            // >= 4.3 API 18
            groupInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        }
        wifiP2pService.connectionChangeActionHandler(networkInfo, wifiP2pInfo, groupInfo);
        return;


        // Get network info to check the connection is established or broken
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (!networkInfo.isConnected()) {
            Log.d(LOG_TAG, "Connection is broken");
            Toast.makeText(wifiP2pService, "Connection is broken", Toast.LENGTH_SHORT).show();
            // Go back to search nearby devices running this APP
            wifiP2pService.setTargetStateSearching();
            // It's not necessary to read group and P2P info
            return;
        }

        // Get group owner IP from P2P info
        WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        if (wifiP2pInfo.isGroupOwner) {
            Log.d(LOG_TAG, "Connection is established. I'm the group owner");
            Toast.makeText(wifiP2pService, "I'm the group owner", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(LOG_TAG, "Connection is established. Group owner IP " + wifiP2pInfo.groupOwnerAddress.getHostAddress());
            Toast.makeText(wifiP2pService, "Group owner IP " + wifiP2pInfo.groupOwnerAddress.getHostAddress(), Toast.LENGTH_SHORT).show();
        }

        // Get group info
//        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
//        if (currentApiVersion < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
//            // < 4.3 API 18
//            wifiP2pManager.requestGroupInfo(wifiP2pChannel, nGroupInfoListener);
//        } else {
//            // >= 4.3 API 18
            WifiP2pGroup groupInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        String interfaceName = groupInfo.getInterface();
        InetAddress ip = getIpByInterface(interfaceName);
        String iipp = (ip == null)? null : ip.getHostAddress();
        String msg = "My IP " + iipp + " NIC " + interfaceName;
        Log.d(LOG_TAG, msg);
        Toast.makeText(wifiP2pService, msg, Toast.LENGTH_SHORT).show();
//        }

        // Begin the rest processes to CONNECTED state
        wifiP2pService.setTargetStateConnected();
    }

    // After receiving an intent with action WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
    // Show the name in the APP
    private void wifiP2pThisDeviceChangedActionHandler(Intent intent) {
        WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        wifiP2pService.setWifiP2pDeviceName(device.deviceName);
    }

    // Notify if nearby device discovery starts or stops
    private void wifiP2pDiscoveryChangedActionHandler(Intent intent) {
        // Log only
        int status = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 0);
        switch (status) {
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
                Log.d(LOG_TAG, "Nearby device discovery starts");
                break;
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
                wifiP2pService.restartPeerDiscoverInAccident();
                break;
        }
    }
}
