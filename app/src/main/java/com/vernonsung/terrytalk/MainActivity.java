package com.vernonsung.terrytalk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

/**
 * The main activity that is first shown when the APP starts
 */
public class MainActivity extends AppCompatActivity
        implements PasswordFragment.OnConnectButtonClickedListener,
                   NameFragment.OnChangeWifiP2pNameListener,
                   WifiP2pFragment.OnPortRequirementListener,
                   WifiP2pFragment.OnChangeNameRequestListener {
    private static final String LOG_TAG = "testtest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI. Show WifiP2pFragment at the first start. Fragment manager will recreate it after screen orientation changes.
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.frameMain, new WifiP2pFragment()).commit();
        }
    }

    // Called from PasswordFragment when the user finish entering the password
    @Override
    public void onConnectButtonClicked(int password) {
        // Change to WifiP2pFragment and connect to the server
        getFragmentManager().popBackStackImmediate();
        WifiP2pFragment wifiP2pFragment = (WifiP2pFragment)getFragmentManager().findFragmentById(R.id.frameMain);
        wifiP2pFragment.setTargetPort(password);
        wifiP2pFragment.connectTarget();
    }

    // Called from NameFragment when the user finish entering a new name
    @Override
    public void onChangeWifiP2pName(String newName) {
        // Change to WifiP2pFragment and change the name
        getFragmentManager().popBackStackImmediate();
        WifiP2pFragment wifiP2pFragment = (WifiP2pFragment)getFragmentManager().findFragmentById(R.id.frameMain);
        wifiP2pFragment.changeDeviceName(newName);
    }

    // Called from WifiP2pFragment when it's about to connect to a server
    @Override
    public void onPortRequirement() {
        // Show PasswordFragment for user to input the password shown on the server device screen.
        getFragmentManager().beginTransaction()
                .replace(R.id.frameMain, new PasswordFragment())
                .addToBackStack(null)
                .commit();
    }

    // Called from WifiP2pFragment when user want to change the Wi-Fi direct device name
    @Override
    public void onChangeNameRequest(String originalName) {
        // Show NameFragment for user to input a new name
        getFragmentManager().beginTransaction()
                .replace(R.id.frameMain, NameFragment.newInstance(originalName))
                .addToBackStack(null)
                .commit();
    }

}
