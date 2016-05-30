package com.vernonsung.terrytalk;

import android.net.rtp.AudioStream;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SocketClientTask extends AsyncTask<Void, Void, AudioStream> {
    private static final String LOG_TAG = "testtest";
    private static final int CONNECT_TIMEOUT = 5000;  // ms
    private static final int SOCKET_READ_TIMEOUT = 2000;  // ms

    private WifiP2pService wifiP2pService;
    private InetSocketAddress localSocketAddress;
    private InetSocketAddress remoteSocketAddress;
    private String localMac;
    private Socket socket;

    @Override
    protected AudioStream doInBackground(Void... params) {
        try {
            AudioStream audioStream = new AudioStream(localSocketAddress.getAddress());
            socket = new Socket();

            // Bind to the specified IP and port
            socket.bind(localSocketAddress);

            // Connect to the server
            socket.connect(remoteSocketAddress, CONNECT_TIMEOUT);

            // Socket IO
            socket.setSoTimeout(SOCKET_READ_TIMEOUT);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());

            // Send local Wi-Fi direct MAC address and local audio stream port
            bufferedOutputStream.write(localMac.getBytes());
            bufferedOutputStream.write(ByteBuffer.allocate(4).putInt(audioStream.getLocalPort()).array());
            bufferedOutputStream.flush();
            Log.d(LOG_TAG, "Local audio stream port " + String.valueOf(audioStream.getLocalPort()) + " sent");

            // Receive remote audio stream port
            byte[] buf = new byte[4];
            int length = bufferedInputStream.read(buf);
            if (length < 4) {
                Log.d(LOG_TAG, "Receive remote port failed with only " + String.valueOf(length) + " bytes. Maybe it's a malicious server.");
                return null;
            }
            int remoteAudioStreamPort = ByteBuffer.wrap(buf).getInt();
            Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort));

            // Verify remote audio stream port
            if (remoteAudioStreamPort <= 0 && remoteAudioStreamPort >= 65536) {
                Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort) + " is invalid. Maybe it's a malicious client.");
                return null;
            }

            // Associate audio stream
            audioStream.associate(remoteSocketAddress.getAddress(), remoteAudioStreamPort);

            return audioStream;
        } catch (BindException e) {
            Log.e(LOG_TAG, "Bind to " + localSocketAddress.toString() + " failed. Maybe the IP doesn't exist or the port is in use. Retry later please.");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Server " + remoteSocketAddress.toString() + " has no response. Retry later please." + e.toString());
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "Close socket error because " + e.toString());
            }
        }
        Log.d(LOG_TAG, "SocketClientTask exits");
        return null;
    }

    @Override
    protected void onPostExecute(AudioStream audioStream) {
        wifiP2pService.audioStreamSetupPart2(audioStream);
    }

    // Constructor--------------------------------------------------------------------------------
    public SocketClientTask(WifiP2pService wifiP2pService,
                            InetSocketAddress localSocketAddress,
                            InetSocketAddress remoteSocketAddress,
                            String localMac) {
        this.wifiP2pService = wifiP2pService;
        this.localSocketAddress = localSocketAddress;
        this.remoteSocketAddress = remoteSocketAddress;
        this.localMac = localMac;
    }
}
