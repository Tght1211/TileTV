package com.tiletv.app.ws;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket connection manager (singleton).
 * Manages the connection lifecycle to the TileTV backend server,
 * including automatic reconnection on disconnection.
 */
public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private static final int RECONNECT_DELAY = 5000;
    private static final int CONNECTION_TIMEOUT = 10000;

    private static WebSocketManager instance;

    private WebSocketClient client;
    private Callback callback;
    private String serverUrl;
    private boolean connected = false;
    private boolean intentionalClose = false;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onConnected();
        void onDisconnected();
        void onMessage(String message);
    }

    private WebSocketManager() {
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * Connect to the WebSocket server at the given URL.
     *
     * @param url      WebSocket URL, e.g. "ws://192.168.1.100:9870"
     * @param callback Callback for connection events
     */
    public void connect(String url, Callback callback) {
        this.serverUrl = url;
        this.callback = callback;
        this.intentionalClose = false;
        doConnect();
    }

    private void doConnect() {
        try {
            if (client != null) {
                try {
                    client.closeBlocking();
                } catch (Exception ignored) {
                }
            }

            URI uri = new URI(serverUrl);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket connected to " + serverUrl);
                    connected = true;
                    if (callback != null) {
                        callback.onConnected();
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (callback != null) {
                        callback.onMessage(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket closed: code=" + code + " reason=" + reason + " remote=" + remote);
                    connected = false;
                    if (callback != null) {
                        callback.onDisconnected();
                    }
                    if (!intentionalClose) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    connected = false;
                    if (ex != null) {
                        ex.printStackTrace();
                    }
                }
            };
            client.setConnectionLostTimeout(30);
            client.connectBlocking(CONNECTION_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.e(TAG, "WebSocket connect failed: " + e.getMessage());
            connected = false;
            if (callback != null) {
                callback.onDisconnected();
            }
            if (!intentionalClose) {
                scheduleReconnect();
            }
        }
    }

    /**
     * Send a text message to the server.
     *
     * @param message JSON string to send
     */
    public void send(String message) {
        if (client != null && connected) {
            try {
                client.send(message);
            } catch (Exception e) {
                Log.e(TAG, "WebSocket send error: " + e.getMessage());
            }
        }
    }

    /**
     * Check if the WebSocket is currently connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Replace the current callback. Used when switching activities.
     *
     * @param callback The new callback
     */
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    /**
     * Disconnect from the server and stop reconnection attempts.
     */
    public void disconnect() {
        intentionalClose = true;
        reconnectHandler.removeCallbacksAndMessages(null);
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        connected = false;
    }

    /**
     * Get the current server URL.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    private void scheduleReconnect() {
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!connected && serverUrl != null && !intentionalClose) {
                    Log.d(TAG, "Attempting reconnect to " + serverUrl);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            doConnect();
                        }
                    }).start();
                }
            }
        }, RECONNECT_DELAY);
    }
}
