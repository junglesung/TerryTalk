package com.vernonsung.terrytalk;

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

                // Receive remote audio stream port
                byte[] buf = new byte[4];
                int length = bufferedInputStream.read(buf);
                if (length < 4) {
                    Log.d(LOG_TAG, "Receive remote port failed with only " + String.valueOf(length) + " bytes. Maybe it's a malicious server.");
                    return;
                }
                int remoteAudioStreamPort = ByteBuffer.wrap(buf).getInt();
                Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort));

                // Verify remote audio stream port
                if (remoteAudioStreamPort <= 0 && remoteAudioStreamPort >= 65536) {
                    return;
                }
                int localAudioStreamPort = setupAudioStream(remoteAudioStreamPort);

                // Send local audio stream port
                bufferedOutputStream.write(ByteBuffer.allocate(4).putInt(localAudioStreamPort).array());
                bufferedOutputStream.flush();
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

        // Setup audio stream
        private int setupAudioStream(int remoteAudioStreamPort) {
            // TODO: Create an audio stream.
            // TODO: Associate it to remote socket IP and remoteAudioStreamPort
            // TODO: Return the audio stream local port
            return 12345;  // Vernon debug. Fake port
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

