package com.vernonsung.testwifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.Date;

public class WifiP2pReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "testtest";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WifiP2pActivity wifiP2pActivity;  // APP activity to deal Wi-Fi P2P

    public WifiP2pReceiver(WifiP2pManager wifiP2pManager,
                           WifiP2pManager.Channel wifiP2pChannel,
                           WifiP2pActivity wifiP2pActivity) {
        this.wifiP2pManager = wifiP2pManager;
        this.wifiP2pChannel = wifiP2pChannel;
        this.wifiP2pActivity = wifiP2pActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // TODO: wifiP2pStateChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            wifiP2pPeersChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // TODO: wifiP2pConnectionChangeActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // TODO: wifiP2pThisDeviceChangedActionHandler(intent);
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
                Log.d(LOG_TAG, "Nearby device discovery stops");
                break;
        }

        // Start Wifi P2P peer discovery if it's stopped
//        if (status != WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
//            wifiP2pManager.discoverPeers(wifiP2pChannel, new WifiP2pManager.ActionListener() {
//                @Override
//                public void onSuccess() {
//                    Log.d(LOG_TAG, "Restart nearby device discovery successfully");
//                }
//
//                @Override
//                public void onFailure(int reason) {
//                    Log.d(LOG_TAG, "Restart nearby device discovery failed");
//                }
//            });
//        }
    }
}
