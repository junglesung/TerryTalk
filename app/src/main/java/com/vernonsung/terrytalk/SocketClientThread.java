package com.vernonsung.terrytalk;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * A socket server which listen to clients' registration in order to establish audio stream
 */
public class SocketClientThread implements Runnable {
    private static final String LOG_TAG = "testtest";
    private static final String MESSAGE = "Hello\n";
    private static final int CONNECT_TIMEOUT = 5000;  // ms
    private static final int SOCKET_READ_TIMEOUT = 2000;  // ms

    private SocketAddress localSocketAddress;
    private SocketAddress remoteSocketAddress;

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = new Socket();

            // Bind to the specified IP and port
            socket.bind(localSocketAddress);

            // Connect to the server
            socket.connect(remoteSocketAddress, CONNECT_TIMEOUT);

            // Socket IO
            socket.setSoTimeout(SOCKET_READ_TIMEOUT);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Communicate with the server
            bufferedWriter.write(MESSAGE);
            bufferedWriter.flush();
            String hello = bufferedReader.readLine();
            socket.close();

            Log.d(LOG_TAG, hello);
        } catch (BindException e) {
            Log.e(LOG_TAG, "Bind to " + localSocketAddress.toString() + " failed. Maybe the IP doesn't exist or the port is in use. Retry later please.");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Server " + remoteSocketAddress.toString() + " has not response. Retry later please.");
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "Close socket error because " + e.getMessage());
            }
        }
    }

    // Constructor--------------------------------------------------------------------------------
    public SocketClientThread(SocketAddress localSocketAddress, SocketAddress remoteSocketAddress) {
        this.localSocketAddress = localSocketAddress;
        this.remoteSocketAddress = remoteSocketAddress;
    }
}

