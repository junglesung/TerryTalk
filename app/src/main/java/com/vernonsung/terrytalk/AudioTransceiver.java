package com.vernonsung.terrytalk;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Transmit and receive audio streams between devices.
 * Thread-safe
 */
public class AudioTransceiver implements AudioManager.OnAudioFocusChangeListener{
    enum PlayerState {
        INITIAL, PREPARED, PLAYING
    }

    private static final String LOG_TAG = "testtest";
    private static final String WAKE_LOCK = "wakeLock";
    private AudioGroup group;
    private HashMap<String, AudioStream> streams = new HashMap<>();  // <Remote MAC address, Audio stream> list
    private int originalAudioMode = AudioManager.MODE_INVALID;
    private PowerManager.WakeLock wakeLock = null;
    private PlayerState currentState = PlayerState.INITIAL;
    private Context context;

    /**
     * Constructor
     *
     * @param context To get system services like AudioManager, PowerManager
     */
    public AudioTransceiver(@NonNull Context context) {
        this.context = context;

        // Initial group
        group = new AudioGroup();
        group.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);

        // Change state
        currentState = PlayerState.PREPARED;
        Log.d(LOG_TAG, "Audio transceiver state -> PREPARED");
    }

    // Be aware of audio focus change
    @Override
    public void onAudioFocusChange(int focusChange) {
        // Do something based on focus change...
        Log.d(LOG_TAG, "Audio focus change is called");
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                group.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);
                Log.d(LOG_TAG, "Audio focus change -> AUDIOFOCUS_GAIN");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time
                group.setMode(AudioGroup.MODE_ON_HOLD);
                Log.d(LOG_TAG, "Audio focus change -> AUDIOFOCUS_LOSS");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time
                // Do nothing
                Log.d(LOG_TAG, "Audio focus change -> AUDIOFOCUS_LOSS_TRANSIENT");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                // Do nothing
                Log.d(LOG_TAG, "Audio focus change -> AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                break;
        }
    }

    /**
     * Add a new audio stream for a server. The stream is set to both speak and listen.
     *
     * @param remoteMac The MAC address of the remote device. This is the ID of the stream in the
     *                  list so that it can be identify to remove when the remote device
     *                  disconnects.
     * @param localIp The local IP that the new audio stream is going to bind.
     * @param remoteSocket The IP and port of the remote device for the audio stream to associate
     *                     to.
     * @return Local audio stream port or 0 in the case of error.
     */
    public synchronized int addServerStream(@NonNull String remoteMac,
                                            @NonNull InetAddress localIp,
                                            @NonNull InetSocketAddress remoteSocket) {
        if (currentState == PlayerState.INITIAL) {
            Log.e(LOG_TAG, "Initial audio transceiver failed, please restart");
            return 0;
        }

        // Create a new stream
        AudioStream stream;
        try {
            stream = new AudioStream(localIp);
        } catch (SocketException e) {
            Log.e(LOG_TAG, "Initial AudioStream failed because " + e.getMessage());
            return 0;
        }
        Log.d(LOG_TAG, "Local port " + String.valueOf(stream.getLocalPort()));
        stream.setCodec(AudioCodec.AMR);
        stream.setMode(RtpStream.MODE_NORMAL);

        if (currentState == PlayerState.PREPARED) {
            Log.d(LOG_TAG, "The first audio stream is added. Start palying audio.");
            startPlayAudio();
        }
        stream.associate(remoteSocket.getAddress(), remoteSocket.getPort());
        stream.join(group);
        Log.d(LOG_TAG, "New audio stream " +
                stream.getLocalAddress().getHostAddress() + ":" + stream.getLocalPort() + " -> " +
                stream.getRemoteAddress().getHostAddress() + ":" + String.valueOf(stream.getRemotePort()));

        // Add the new stream to list
        streams.put(remoteMac, stream);

        return stream.getLocalPort();
    }

    /**
     * Add an associated audio stream for a client. The stream is set to listen only.
     *
     * @param remoteMac The MAC address of the remote device. This is the ID of the stream in the
     *                  list so that it can be identify to remove when the remote device
     *                  disconnects.
     * @param stream An audio stream which is bound to local ip and associated to a remote socket.
     */
    public synchronized void addClientStream(@NonNull String remoteMac, @NonNull AudioStream stream) {
        if (currentState == PlayerState.INITIAL) {
            Log.e(LOG_TAG, "Initial audio transceiver failed, please restart");
            return;
        }

        stream.setCodec(AudioCodec.AMR);
        stream.setMode(RtpStream.MODE_RECEIVE_ONLY);

        if (currentState == PlayerState.PREPARED) {
            Log.d(LOG_TAG, "The first audio stream is added. Start palying audio.");
            startPlayAudio();
        }
        stream.join(group);
        Log.d(LOG_TAG, "New audio stream " +
                stream.getLocalAddress().getHostAddress() + ":" + stream.getLocalPort() + " -> " +
                stream.getRemoteAddress().getHostAddress() + ":" + String.valueOf(stream.getRemotePort()));

        // Add the new stream to list
        streams.put(remoteMac, stream);
    }

    /**
     * Remove an audio stream
     *
     * @param remoteMac The MAC address of the remote device. This is the ID of the stream to
     *                  remove.
     */
    public synchronized void removeStream(String remoteMac) {
        AudioStream stream = streams.remove(remoteMac);
        if (stream == null) {
            Log.d(LOG_TAG, "Audio stream of " + remoteMac + " is not found");
            return;
        }
        stream.join(null);
        Log.d(LOG_TAG, "Audio stream of " + remoteMac + " is removed");
        if (streams.isEmpty()) {
            Log.d(LOG_TAG, "Last audio stream is removed. Stop playing audio.");
            stopPlayAudio();
        }
    }

    /**
     * Remove all audio streams
     */
    public synchronized void clearStreams() {
        for (AudioStream stream : streams.values()) {
            stream.join(null);
        }
        streams.clear();
        Log.d(LOG_TAG, "All audio streams are removed. Stop playing audio.");
        stopPlayAudio();
    }

    /**
     * Print all audio streams to debug
     */
    public synchronized void printStreams() {
        for (AudioStream stream : streams.values()) {
            Log.d(LOG_TAG, "Current audio stream " +
                    stream.getLocalAddress().getHostAddress() + ":" + stream.getLocalPort() + " -> " +
                    stream.getRemoteAddress().getHostAddress() + ":" + String.valueOf(stream.getRemotePort()));
        }
    }

    // It's called when the first stream is added. PREPARED -> PLAYING
    private void startPlayAudio() {
        try {
            // AudioGroup needs this.
            setupAudioMode();

            // Acquire lock to avoid power saving mode
            acquireLock();

            // Acquire audio focus to avoid influence from other APPs
            acquireAudioFocus();

            // Change state
            currentState = PlayerState.PLAYING;
            Log.d(LOG_TAG, "Audio transceiver state -> PLAYING");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Start playing audio failed, " + e.toString());
        }
    }

    // It's called after the last audio stream is removed. While PLAYING
    private void stopPlayAudio() {
        if (currentState == PlayerState.PLAYING) {
            if (abandonFocus()) {
                Log.d(LOG_TAG, "Abandon audio focus successfully");
            } else {
                Log.d(LOG_TAG, "Abandon audio focus failed");
            }
            restoreAudioMode();
            releaseLock();

            // Change state
            currentState = PlayerState.PREPARED;
            Log.d(LOG_TAG, "Audio transceiver state -> PREPARED");
        }
    }

    private void setupAudioMode() {
        AudioManager m = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        originalAudioMode = m.getMode();
        m.setMode(AudioManager.MODE_IN_COMMUNICATION);
        Log.d(LOG_TAG, "Set audio mode MODE_IN_COMMUNICATION");
    }

    private void restoreAudioMode() {
        AudioManager m = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (originalAudioMode != AudioManager.MODE_INVALID) {
            m.setMode(originalAudioMode);
            Log.d(LOG_TAG, "Restore audio mode " + originalAudioMode);
        }
    }

    // Acquire lock to avoid power saving mode
    private void acquireLock() {
        // To ensure that the CPU continues running
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
        wakeLock.acquire();
    }

    // Release lock to save power
    private void releaseLock() {
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    // Acquire audio focus
    private void acquireAudioFocus() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // could not get audio focus.
            Log.d(LOG_TAG, "Request audio focus failed");
        }
        Log.d(LOG_TAG, "Request audio focus successfully");
    }

    // Release audio focus
    private boolean abandonFocus() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }
}
