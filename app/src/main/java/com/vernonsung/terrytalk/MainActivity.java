package com.vernonsung.terrytalk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI
        getFragmentManager().beginTransaction().add(R.id.frameMain, new WifiP2pFragment()).commit();
    }
}
