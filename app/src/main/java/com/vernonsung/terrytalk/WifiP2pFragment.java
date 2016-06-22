package com.vernonsung.terrytalk;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * A {@link Fragment} for users to manipulate Wi-Fi direct connection.
 * Activities that contain this fragment must implement the
 * {@link WifiP2pFragment.OnPortRequirementListener} interface
 * to handle interaction events.
 */
public class WifiP2pFragment extends Fragment
        implements ServiceConnection,
                   SwipeRefreshLayout.OnRefreshListener,
                   Handler.Callback {

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnPortRequirementListener {
        void onPortRequirement();
    }

    // Schedule task through handler to call the service's methods
    private enum ServiceTask {
        UPDATE_IP, UPDATE_PORT, UPDATE_DEVICES, UPDATE_STATE
    }

    private static final String LOG_TAG = "testtest";

    // To show devices on the list
    private ArrayList<HashMap<String, String>> nearbyDevices = new ArrayList<>();
    private SimpleAdapter listViewDevicesAdapter;

    // Broadcast receiver
    private WifiP2pFragmentReceiver wifiP2PFragmentReceiver;
    private IntentFilter wifiP2pIntentFilter;
    private IntentFilter wifiP2pServiceIntentFilter;

    // Bind to service to get information
    private WifiP2pService wifiP2pService;
    private LinkedList<ServiceTask> serviceTaskQueue = new LinkedList<>();
    private boolean serviceStopped = true;

    // Permission request
    private static final int PERMISSION_REQUEST_SERVICE = 100;

    // Interact with the activity
    private OnPortRequirementListener onPortRequirementListener;

    // Display refresh finished 1s later
    private Handler handlerRefreshFinish;
    private static final int HANDLER_REFRESH_FINISH_WHAT = 13;
    private static final int HANDLER_REFRESH_FINISH_DELAY_MS = 1000;

    // Connect to target
    String targetName;
    int targetPort;

    // UI
    private TextView textViewName;
    private TextView textViewState;
    private TextView textViewIp;
    private TextView textViewPort;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ListView listViewDevices;

    // Function objects
    private AdapterView.OnItemClickListener listViewDevicesOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            targetName = nearbyDevices.get(position).get(WifiP2pService.MAP_ID_DEVICE_NAME);
            // Change to another fragment for user to input port
            onPortRequirementListener.onPortRequirement();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        handlerRefreshFinish = new Handler(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // UI
        View view =  inflater.inflate(R.layout.fragment_wifi_p2p, container, false);  // Always false
        textViewName = (TextView)view.findViewById(R.id.textViewName);
        textViewState = (TextView)view.findViewById(R.id.textViewState);
        textViewIp = (TextView)view.findViewById(R.id.textViewIp);
        textViewPort = (TextView)view.findViewById(R.id.textViewPort);
        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayoutDevices);
        listViewDevices = (ListView)view.findViewById(R.id.listViewDevices);
        // TODO: User can change name in the future
//        textViewName.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
//            }
//        });
        swipeRefreshLayout.setOnRefreshListener(this);
        listViewDevicesAdapter = new SimpleAdapter(getActivity(),
                                                   nearbyDevices,
                                                   android.R.layout.simple_list_item_2,
                                                   new String[] {WifiP2pService.MAP_ID_DEVICE_NAME, WifiP2pService.MAP_ID_STATUS},
                                                   new int[] {android.R.id.text1, android.R.id.text2});
        listViewDevices.setAdapter(listViewDevicesAdapter);
        listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_wifi_p2p_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuItemStop:
                serviceActionStop();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initializeBroadcastReceiver();
        // Start the main service if it's not started yet
        checkPermissionToStartService();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnPortRequirementListener) {
            onPortRequirementListener = (OnPortRequirementListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    /**
     * Deprecated in API level 23. Keep it here for backward compatibility
     */
    @Deprecated
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnPortRequirementListener) {
            onPortRequirementListener = (OnPortRequirementListener) activity;
        } else {
            throw new RuntimeException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        onPortRequirementListener = null;
    }


    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Receive intents related to Wi-Fi P2p
        getActivity().registerReceiver(wifiP2PFragmentReceiver, wifiP2pIntentFilter);
        // Receive intents from the service
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(wifiP2PFragmentReceiver, wifiP2pServiceIntentFilter);
        Log.d(LOG_TAG, "Broadcast receiver registered");
        // Update information from Wi-Fi P2P service
        updateAllFromService();
    }

    @Override
    public void onPause() {
        // Stop receiving intents related to Wi-Fi P2p
        getActivity().unregisterReceiver(wifiP2PFragmentReceiver);
        // Stop receiving intents from the service
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(wifiP2PFragmentReceiver);
        Log.d(LOG_TAG, "Broadcast receiver unregistered");
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (!serviceStopped) {
            Toast.makeText(getActivity(), R.string.terry_talks_still_runs_in_background, Toast.LENGTH_SHORT).show();
        }
        super.onDestroy();
    }

    // Interface ServiceConnection ---------------------------------------------------------------
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(LOG_TAG, "Service bound");
        serviceStopped = false;
        WifiP2pService.LocalBinder binder = (WifiP2pService.LocalBinder)service;
        wifiP2pService = binder.getService();
        doServiceTask();
    }

    // It's only called when the service accidentally
    @Override
    public void onServiceDisconnected(ComponentName name) {
        wifiP2pService = null;
        Log.d(LOG_TAG, "Service disconnected unexpectedly");
    }
    // Interface ServiceConnection ---------------------------------------------------------------

    // Interface SwipeRefreshLayout.OnRefreshListener --------------------------------------------
    @Override
    public void onRefresh() {
        serviceActionRefresh();
    }
    // Interface SwipeRefreshLayout.OnRefreshListener --------------------------------------------

    // Interface Handler.Callback ----------------------------------------------------------------
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_REFRESH_FINISH_WHAT:
                // Turn the refreshing sign OFF
                swipeRefreshLayout.setRefreshing(false);
                break;
        }
        return true;
    }
    // Interface Handler.Callback ----------------------------------------------------------------

    // Permission check and request for Android 6+ -----------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_SERVICE:
                localServicePermissionHandler(grantResults);
                break;
        }
    }

    private void localServicePermissionHandler(int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length == 0) {
            return;
        }
        // Check whether every permission is granted
        for (int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        // Check all permissions again
        checkPermissionToStartService();
    }

    @TargetApi(23)
    private void checkPermissionToStartService() {
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        // Check permissions after Android 6 (API 23)
        if (currentApiVersion >= Build.VERSION_CODES.M) {
            // List all permissions to check for grants
            String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            ArrayList<String> permissionsToRequest = new ArrayList<>();

            // Check every permissions
            for (String s : permissions) {
                if (ContextCompat.checkSelfPermission(getActivity(), s) == PackageManager.PERMISSION_DENIED) {
                    permissionsToRequest.add(s);
                }
            }

            // There are permission grants to request
            if (!permissionsToRequest.isEmpty()) {
                requestPermissions(permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_SERVICE);
                // Async call back onRequestPermissionsResult() will be called
                return;
            }
        }

        // Finally all permission are granted
        serviceActionStart();
    }
    // Permission check and request for Android 6+ -----------------------------------------------

    private void initializeBroadcastReceiver() {
        wifiP2PFragmentReceiver = new WifiP2pFragmentReceiver(this);
        // Filter the intent received by a broadcast receiver in order to sense Wi-Fi P2P status
        wifiP2pIntentFilter = new IntentFilter();
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);  // Receive peers update from WifiP2pService
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);  // Receive group clients from WifiP2pService
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        // wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);  // Not necessary

        // Filter the intent sent from WifiP2pService in order to react to service state change
        wifiP2pServiceIntentFilter = new IntentFilter();
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_NEARBY_DEVICES_ACTION);
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_STATE_ACTION);
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_IP_ACTION);
        wifiP2pServiceIntentFilter.addAction(WifiP2pService.UPDATE_PORT_ACTION);
    }

    private void serviceActionStart() {
        Intent intent = new Intent(getActivity(), WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_START);
        getActivity().startService(intent);
        // Update data from the service
        updateAllFromService();
    }

    private void serviceActionConnect() {
        if (targetName == null || targetName.isEmpty()) {
            Log.e(LOG_TAG, "No target name");
            return;
        }
        Intent intent = new Intent(getActivity(), WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_CONNECT);
        // Put target name into the intent
        intent.putExtra(WifiP2pService.INTENT_EXTRA_TARGET, targetName);
        intent.putExtra(WifiP2pService.INTENT_EXTRA_PORT, targetPort);
        getActivity().startService(intent);
    }

    private void serviceActionRefresh() {
        Intent intent = new Intent(getActivity(), WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_REFRESH);
        getActivity().startService(intent);
        // Display the refresh sign
        if (!swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }
        // Schedule to turn the refresh sign OFF
        handlerRefreshFinish.sendEmptyMessageDelayed(HANDLER_REFRESH_FINISH_WHAT, HANDLER_REFRESH_FINISH_DELAY_MS);
    }

    private void serviceActionStop() {
        Intent intent = new Intent(getActivity(), WifiP2pService.class);
        intent.setAction(WifiP2pService.ACTION_STOP);
        getActivity().startService(intent);
    }

    public void showDeviceName(String name) {
        textViewName.setText(name);
    }

    public void setIp(String ip) {
        textViewIp.setText(ip);
    }

    public void setPort(int port) {
        String s = "Password: ";
        if (port != 0) {
            s += String.valueOf(port);
        }
        textViewPort.setText(s);
    }

    public void setState(WifiP2pService.WifiP2pState state) {
        switch (state) {
            case INITIALIZING:
                listViewDevices.setOnItemClickListener(null);
                break;
            case SEARCHING:
            case IDLE:
                listViewDevices.setOnItemClickListener(listViewDevicesOnItemClickListener);
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
                listViewDevices.setOnItemClickListener(null);
                break;
            case STOPPING:
            case STOPPED:
                listViewDevices.setOnItemClickListener(null);
                // Leave APP
                serviceStopped = true;
                getActivity().finish();
                break;
        }
        textViewState.setText(state.toString());
    }

    private void updateNearByDevicesFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(getActivity(), R.string.please_restart_app, Toast.LENGTH_SHORT).show();
            return;
        }
        nearbyDevices.clear();
        nearbyDevices.addAll(wifiP2pService.getNearbyDevices());
        ArrayList<HashMap<String, String>> clients = wifiP2pService.getClients();
        // Merge clients status to nearbyDevices
        for (HashMap<String, String> client : clients) {
            String name = client.get(WifiP2pService.MAP_ID_DEVICE_NAME);
            boolean found = false;
            for (HashMap<String, String> device : nearbyDevices) {
                if (name.equals(device.get(WifiP2pService.MAP_ID_DEVICE_NAME))) {
                    String status = device.get(WifiP2pService.MAP_ID_STATUS);
                    if (status.compareToIgnoreCase(getString(R.string.connected)) != 0) {
                        Log.d(LOG_TAG, "Replace status " + status + " by Connected");
                        device.put(WifiP2pService.MAP_ID_STATUS, getString(R.string.connected));
                    }
                    found = true;
                    break;
                }
            }
            // Add client if it's not in the nearbyDevices yet
            if (!found) {
                nearbyDevices.add(0, client);
            }
        }
        Log.d(LOG_TAG, "Activity has " + nearbyDevices.size() + " devices now");
        listViewDevicesAdapter.notifyDataSetChanged();
    }

    private void updateIpFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(getActivity(), R.string.please_restart_app, Toast.LENGTH_SHORT).show();
            return;
        }
        String msg;
        InetAddress ip = wifiP2pService.getWifiP2pLocalDeviceIp();
        if (ip == null) {
            msg = "";
        } else {
            msg = ip.getHostAddress();
        }
        setIp(msg);
    }

    private void updatePortFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(getActivity(), R.string.please_restart_app, Toast.LENGTH_SHORT).show();
            return;
        }
        int port = wifiP2pService.getLocalRegistrationPort();
        setPort(port);
    }

    private void updateStateFromServiceTaskHandler() {
        if (wifiP2pService == null) {
            Log.e(LOG_TAG, "Service is not running");
            Toast.makeText(getActivity(), R.string.please_restart_app, Toast.LENGTH_SHORT).show();
            return;
        }
        setState(wifiP2pService.getCurrentState());
    }

    private void doServiceTask() {
        while (!serviceTaskQueue.isEmpty()) {
            ServiceTask task = serviceTaskQueue.pop();
            switch (task) {
                case UPDATE_IP:
                    updateIpFromServiceTaskHandler();
                    break;
                case UPDATE_PORT:
                    updatePortFromServiceTaskHandler();
                    break;
                case UPDATE_DEVICES:
                    updateNearByDevicesFromServiceTaskHandler();
                    break;
                case UPDATE_STATE:
                    updateStateFromServiceTaskHandler();
                    break;
            }
        }
        unbindWifiP2pService();
    }

    public void updateNearByDevicesFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_DEVICES);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updateIpFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_IP);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updatePortFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_PORT);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updateStateFromService() {
        serviceTaskQueue.push(ServiceTask.UPDATE_STATE);
        if (wifiP2pService == null) {
            bindWifiP2pService();
        }
    }

    private void updateAllFromService() {
        updateIpFromService();
        updatePortFromService();
        updateStateFromService();
        updateNearByDevicesFromService();
    }

    private void bindWifiP2pService() {
        Log.d(LOG_TAG, "I'm going to bind the service");
        getActivity().bindService(new Intent(getActivity(), WifiP2pService.class), this, 0);
    }

    private void unbindWifiP2pService() {
        getActivity().unbindService(this);
        wifiP2pService = null;
        Log.d(LOG_TAG, "Service unbound");
    }

    // For the activity to call ------------------------------------------------------------------
    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
        Log.d(LOG_TAG, "Set target port " + String.valueOf(targetPort));
    }

    public void connectTarget() {
        serviceActionConnect();
    }
}