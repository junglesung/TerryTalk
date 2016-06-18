package com.vernonsung.terrytalk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

public class WifiP2pFragmentReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "testtest";
    private WifiP2pFragment wifiP2PFragment;  // APP activity to deal Wi-Fi P2P

    public WifiP2pFragmentReceiver(WifiP2pFragment wifiP2PFragment) {
        this.wifiP2PFragment = wifiP2PFragment;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // TODO: wifiP2pStateChangedActionHandler(intent);
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            wifiP2pThisDeviceChangedActionHandler(intent);
        } else if (WifiP2pService.UPDATE_GROUP_OWNERS_ACTION.equals(action)) {
            updateGroupOwnersActionHandler(intent);
        } else if (WifiP2pService.UPDATE_CLIENT_DEVICES_ACTION.equals(action)) {
            updateClientDevicesActionHandler(intent);
        } else if (WifiP2pService.UPDATE_STATE_ACTION.equals(action)) {
            updateStateActionHandler(intent);
        } else if (WifiP2pService.UPDATE_IP_ACTION.equals(action)) {
            updateIpActionHandler(intent);
        } else if (WifiP2pService.UPDATE_PORT_ACTION.equals(action)) {
            updatePortActionHandler(intent);
        } else {
            // Unhandled action
            Log.e(LOG_TAG, "WifiP2pReceiver received an unhandled action " + action);
        }
    }

    // After receiving an intent with action WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
    // Show the name in the APP
    private void wifiP2pThisDeviceChangedActionHandler(Intent intent) {
        WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        wifiP2PFragment.showDeviceName(device.deviceName);
    }

    // After receiving an intent with action WifiP2pService.UPDATE_GROUP_OWNERS_ACTION
    // Show the update device list
    private void updateGroupOwnersActionHandler(Intent intent) {
        Log.d(LOG_TAG, "I'm going to update group owners from the service");
        // updateNearbyDevicesFromService() will distinguish to update clients or group owners
        wifiP2PFragment.updateNearbyDevicesFromService();
    }

    // After receiving an intent with action WifiP2pService.UPDATE_CLIENT_DEVICES_ACTION
    // Show the update client list
    private void updateClientDevicesActionHandler(Intent intent) {
        Log.d(LOG_TAG, "I'm going to update client devices from the service");
        // updateNearbyDevicesFromService() will distinguish to update clients or group owners
        wifiP2PFragment.updateNearbyDevicesFromService();
    }

    // After receiving an intent with action WifiP2pService.UPDATE_STATE_ACTION
    // Change UI according to the new status
    private void updateStateActionHandler(Intent intent) {
        int index = intent.getIntExtra(WifiP2pService.INTENT_EXTRA_STATE, -1);
        WifiP2pService.WifiP2pState[] values = WifiP2pService.WifiP2pState.values();
        if (index < 0 || index >= values.length) {
            Log.e(LOG_TAG, "Got wrong state index " + index + " from intent");
            return;
        }
        WifiP2pService.WifiP2pState state = values[index];
        wifiP2PFragment.setState(state);
        Log.d(LOG_TAG, "Set activity state " + state);
    }

    // After receiving an intent with action WifiP2pService.UPDATE_IP_ACTION
    // Show new IP
    private void updateIpActionHandler(Intent intent) {
        String ip = intent.getStringExtra(WifiP2pService.INTENT_EXTRA_IP);
        if (ip == null) {
            ip = "";
        }
        wifiP2PFragment.setIp(ip);
    }

    // After receiving an intent with action WifiP2pService.UPDATE_PORT_ACTION
    // Show port
    private void updatePortActionHandler(Intent intent) {
        int port = intent.getIntExtra(WifiP2pService.INTENT_EXTRA_REGISTRATION_PORT, 0);
        wifiP2PFragment.setPort(port);
    }

}
