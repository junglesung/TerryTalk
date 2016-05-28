package com.vernonsung.terrytalk;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class SocketClientTask extends AsyncTask<Void, Void, Integer> {
    private static final String LOG_TAG = "testtest";
    private static final int CONNECT_TIMEOUT = 5000;  // ms
    private static final int SOCKET_READ_TIMEOUT = 2000;  // ms

    private SocketAddress localSocketAddress;
    private SocketAddress remoteSocketAddress;
    private int localAudioStreamPort;
    private Socket socket;

    @Override
    protected Integer doInBackground(Void... params) {
        try {
            socket = new Socket();

            // Bind to the specified IP and port
            socket.bind(localSocketAddress);

            // Connect to the server
            socket.connect(remoteSocketAddress, CONNECT_TIMEOUT);

            // Socket IO
            socket.setSoTimeout(SOCKET_READ_TIMEOUT);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());

            // Send local audio stream port
            bufferedOutputStream.write(ByteBuffer.allocate(4).putInt(localAudioStreamPort).array());
            bufferedOutputStream.flush();

            // Receive remote audio stream port
            byte[] buf = new byte[4];
            int length = bufferedInputStream.read(buf);
            if (length < 4) {
                Log.d(LOG_TAG, "Receive remote port failed with only " + String.valueOf(length) + " bytes. Maybe it's a malicious server.");
                return 0;
            }
            int remoteAudioStreamPort = ByteBuffer.wrap(buf).getInt();
            Log.d(LOG_TAG, "Remote audio stream port " + String.valueOf(remoteAudioStreamPort));

            // Verify remote audio stream port
            if (remoteAudioStreamPort <= 0 && remoteAudioStreamPort >= 65536) {
                return 0;
            }
            return remoteAudioStreamPort;
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
        return 0;
    }

    @Override
    protected void onPostExecute(Integer integer) {
        // TODO: Associate the audio stream to the remote port
    }

    // Constructor--------------------------------------------------------------------------------
    public SocketClientTask(SocketAddress localSocketAddress, SocketAddress remoteSocketAddress, int localAudioStreamPort) {
        this.localSocketAddress = localSocketAddress;
        this.remoteSocketAddress = remoteSocketAddress;
        this.localAudioStreamPort = localAudioStreamPort;
    }
}