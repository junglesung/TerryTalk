package com.vernonsung.terrytalk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

/**
 * The main activity that is first shown when the APP starts
 */
public class MainActivity extends AppCompatActivity
        implements PasswordFragment.OnConnectButtonClickedListener,
                   WifiP2pFragment.OnPortRequirementListener {
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

    // Called from WifiP2pFragment when it's about to connect to a server
    @Override
    public void onPortRequirement() {
        // Show PasswordFragment for user to input the password shown on the server device screen.
        getFragmentManager().beginTransaction()
                .replace(R.id.frameMain, new PasswordFragment())
                .addToBackStack(null)
                .commit();
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
}
