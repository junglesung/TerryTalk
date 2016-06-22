package com.vernonsung.terrytalk;

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

public class WifiP2pReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "testtest";
    private WifiP2pService wifiP2pService;  // APP activity to deal Wi-Fi P2P

    public WifiP2pReceiver(WifiP2pService wifiP2pService) {
        this.wifiP2pService = wifiP2pService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            wifiP2pStateChangedActionHandler(intent);
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

    // After receiving an intent with action WIFI_P2P_STATE_CHANGED_ACTION,
    // announce if Wi-Fi is OFF
    private void wifiP2pStateChangedActionHandler(Intent intent) {
        if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
            Log.d(LOG_TAG, "Wi-Fi is OFF, please turn ON Wi-Fi");
            Toast.makeText(wifiP2pService, R.string.please_turn_on_wifi, Toast.LENGTH_SHORT).show();
        }
    }

    // After receiving an intent with action WIFI_P2P_PEERS_CHANGED_ACTION,
    // discover the devices running this APP
    private void wifiP2pPeersChangedActionHandler(Intent intent) {
        Log.d(LOG_TAG, "Nearby devices changed");
        wifiP2pService.discoverNearbyDevicesStep2();
        // TODO: Connect to last group in the future
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
            Log.e(LOG_TAG, "SDK < 4.3 API 18, incompatible");
//            wifiP2pManager.requestGroupInfo(wifiP2pChannel, nGroupInfoListener);
        } else {
            // >= 4.3 API 18
            groupInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        }
        wifiP2pService.connectionChangeActionHandler(networkInfo, wifiP2pInfo, groupInfo);
    }

    // After receiving an intent with action WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
    // Show the name in the APP
    private void wifiP2pThisDeviceChangedActionHandler(Intent intent) {
        WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        wifiP2pService.setWifiP2pLocalDeviceName(device.deviceName);
        wifiP2pService.setWifiP2pLocalDeviceMac(device.deviceAddress);
    }

    // Notify if nearby device discovery starts or stops
    private void wifiP2pDiscoveryChangedActionHandler(Intent intent) {
        int status = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 0);
        wifiP2pService.peerDiscoveryChangedActionHandler(status);
    }
}
