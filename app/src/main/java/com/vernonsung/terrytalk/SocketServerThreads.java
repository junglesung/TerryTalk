package com.vernonsung.terrytalk;

import android.util.Log;

import java.io.*;
import java.net.*;

/**
 * A socket client to register to the king and establish an audio stream
 */
public class SocketServerThreads implements Runnable{
    public class SocketThread implements Runnable {
        private Socket socket;

        @Override
        public void run() {
            try {
                // Set read timeout
                socket.setSoTimeout(SOCKET_READ_TIMEOUT);
                // Socket IO
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                // Communicate with the client
                String hello = bufferedReader.readLine();
                bufferedWriter.write(hello);
                bufferedWriter.flush();
                socket.close();

                Log.d(LOG_TAG, hello);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Client " + socket.getRemoteSocketAddress() + " has no response.");
            } finally {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Close socket error becuase " + e.getMessage());
                }
            }
        }

        // Constructor--------------------------------------------------------------------------------
        public SocketThread(Socket socket) {
            this.socket = socket;
        }
    }

    private static final String LOG_TAG = "testtest";
    private static final int CONNECTION_WAITING_TIMEOUT = 1000;  // ms
    private static final int SOCKET_READ_TIMEOUT = 1000;  // ms
    private boolean toQuit = false;
    private SocketAddress socketAddress = null;

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            Socket socket;

            // Bind to user specified IP and port
            serverSocket.bind(socketAddress);

            // Remember the bound IP and port
            setSocketAddress(serverSocket.getLocalSocketAddress());

            // Set connection waiting timeout
            serverSocket.setSoTimeout(CONNECTION_WAITING_TIMEOUT);

            // Wait for connection until receiving quit command
            int i = 0;
            while (!isToQuit()) {
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    Log.d(LOG_TAG, "Timeout " + String.valueOf(i++));
                    continue;
                }

                // Create a new thread to communicate with the new clent
                new Thread(new SocketThread(socket)).start();

                i = 0;
            }
        } catch (BindException e) {
            Log.e(LOG_TAG, "Bind to " + socketAddress.toString() + " failed. Maybe the IP doesn't exist or the port is in use. Retry later please.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Constructor--------------------------------------------------------------------------------
    public SocketServerThreads(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    // Getter and setter--------------------------------------------------------------------------
    public synchronized boolean isToQuit() {
        return toQuit;
    }

    public synchronized void setToQuit(boolean toQuit) {
        this.toQuit = toQuit;
    }

    public synchronized SocketAddress getSocketAddress() {
        return socketAddress;
    }

    private synchronized void setSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }
}

