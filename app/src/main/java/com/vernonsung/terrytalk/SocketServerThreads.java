package com.vernonsung.terrytalk;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

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
                BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());

                // Receive clients data
                String remoteMac = receiveRemoteMac(bufferedInputStream);
                int remoteAudioStreamPort = receiveRemoteAudioStreamPort(bufferedInputStream);

                // Verify remote mac audio stream port
                if (remoteMac == null) {
                    return;
                }
                if (remoteAudioStreamPort <= 0 && remoteAudioStreamPort >= 65536) {
                    Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort) + " is invalid. Maybe it's a malicious client.");
                    return;
                }
                Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort));

                // Setup audio stream and get local audio stream port
                int localAudioStreamPort = setupAudioStream(remoteMac, remoteAudioStreamPort);

                // Send local audio stream port
                bufferedOutputStream.write(ByteBuffer.allocate(4).putInt(localAudioStreamPort).array());
                bufferedOutputStream.flush();
                Log.d(LOG_TAG, "Local audio stream port " + String.valueOf(localAudioStreamPort) + " sent");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Client " + socket.getRemoteSocketAddress().toString() + " has no response. " + e.toString());
            } finally {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Close socket error because " + e.toString());
                }
            }
            Log.d(LOG_TAG, "SocketServerThreads:SocketThread exits");
        }

        // Constructor--------------------------------------------------------------------------------
        public SocketThread(Socket socket) {
            this.socket = socket;
        }

        // Receive remote device MAC address
        private String receiveRemoteMac(BufferedInputStream bufferedInputStream) throws IOException {
            byte[] buf = new byte[17];
            int length = bufferedInputStream.read(buf);
            if (length < buf.length) {
                Log.d(LOG_TAG, "Receive remote MAC failed with only " + String.valueOf(length) + " bytes. Maybe it's a malicious server.");
                return null;
            }
            return new String(buf);
        }

        // Receive remote audio stream port
        private int receiveRemoteAudioStreamPort(BufferedInputStream bufferedInputStream) throws IOException {
            byte[] buf = new byte[4];
            int length = bufferedInputStream.read(buf);
            if (length < buf.length) {
                Log.d(LOG_TAG, "Receive remote port failed with only " + String.valueOf(length) + " bytes. Maybe it's a malicious server.");
                return 0;
            }
            return ByteBuffer.wrap(buf).getInt();
        }

        // Setup audio stream
        private int setupAudioStream(String remoteMac, int remoteAudioStreamPort) {
            // Vernon debug
            Log.d(LOG_TAG, "Socket local address " + socket.getLocalAddress().getHostAddress());
            return audioTransceiver.addStream(remoteMac,
                    socket.getLocalAddress(),
                    new InetSocketAddress(socket.getInetAddress(), remoteAudioStreamPort));
        }
    }

    private static final String LOG_TAG = "testtest";
    private static final int CONNECTION_WAITING_TIMEOUT = 1000;  // ms
    private static final int SOCKET_READ_TIMEOUT = 1000;  // ms
    private ServerSocket serverSocket;
    private boolean toQuit = false;
    private AudioTransceiver audioTransceiver;

    @Override
    public void run() {
        // Check server socket
        if (serverSocket.isClosed()) {
            Log.e(LOG_TAG, "Server socket is closed");
            return;
        }
        try {
            Socket socket;

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
        } catch (IOException e) {
            Log.d(LOG_TAG, "Server socket problem. " + e.toString());
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "Close socket error because " + e.toString());
            }
        }
        Log.d(LOG_TAG, "SocketServerThreads exits");
    }

    /**
     * Constructor
     *
     * @param serverSocket An opened server socket to receive clients' registration.
     * @param audioTransceiver An initialized AudioTransceiver to add a new audio stream when each
     *                         client registers.
     */
    public SocketServerThreads(@NonNull ServerSocket serverSocket,
                               @NonNull AudioTransceiver audioTransceiver) {
        this.serverSocket = serverSocket;
        this.audioTransceiver = audioTransceiver;
    }

    // Getter and setter--------------------------------------------------------------------------
    public synchronized boolean isToQuit() {
        return toQuit;
    }

    public synchronized void setToQuit(boolean toQuit) {
        this.toQuit = toQuit;
    }
}

