package com.tiletv.app.server;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TileTVServer {

    private static final String TAG = "TileTVServer";
    private static final int HTTP_PORT = 9870;
    private static final int WS_PORT = 9871;

    private Context context;
    private HttpServer httpServer;
    private WsServer wsServer;
    private MessageListener listener;
    private Set<WebSocket> clients = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());

    public interface MessageListener {
        void onClientMessage(String json);
        void onClientConnected();
        void onClientDisconnected();
    }

    public TileTVServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        httpServer = new HttpServer(HTTP_PORT);
        httpServer.start();
        Log.d(TAG, "HTTP server started on port " + HTTP_PORT);

        wsServer = new WsServer(new InetSocketAddress(WS_PORT));
        wsServer.setReuseAddr(true);
        wsServer.start();
        Log.d(TAG, "WebSocket server started on port " + WS_PORT);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "WebSocket server stop interrupted", e);
            }
        }
    }

    public String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                if (ip != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ip & 0xff), (ip >> 8 & 0xff),
                            (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get IP", e);
        }
        return "127.0.0.1";
    }

    public String getH5Url() {
        return "http://" + getLocalIpAddress() + ":" + HTTP_PORT + "/h5/";
    }

    public String getWsUrl() {
        return "ws://" + getLocalIpAddress() + ":" + WS_PORT;
    }

    // ====== Broadcast methods ======

    public void broadcastFrame(String base64Data) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "frame");
            msg.put("data", base64Data);
            broadcast(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "broadcastFrame error", e);
        }
    }

    public void broadcastStatus(String text, String level) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "status");
            msg.put("text", text);
            msg.put("level", level);
            broadcast(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "broadcastStatus error", e);
        }
    }

    public void broadcastPageInfo(String url, String title) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "pong");
            msg.put("url", url);
            msg.put("title", title);
            broadcast(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "broadcastPageInfo error", e);
        }
    }

    public void broadcastToast(String text) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "toast");
            msg.put("text", text);
            broadcast(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "broadcastToast error", e);
        }
    }

    public void broadcastJson(String json) {
        broadcast(json);
    }

    private void broadcast(String message) {
        for (WebSocket client : clients) {
            if (client != null && client.isOpen()) {
                try {
                    client.send(message);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to send to client", e);
                }
            }
        }
    }

    public boolean hasClients() {
        return !clients.isEmpty();
    }

    // ====== HTTP Server (NanoHTTPD) ======

    private class HttpServer extends NanoHTTPD {

        HttpServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();

            // CORS headers for all responses
            if (Method.OPTIONS.equals(session.getMethod())) {
                Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                addCorsHeaders(resp);
                return resp;
            }

            // Serve H5 static files
            if (uri.startsWith("/h5")) {
                return serveH5(uri);
            }

            // API status endpoint
            if ("/api/status".equals(uri)) {
                try {
                    JSONObject status = new JSONObject();
                    status.put("status", "running");
                    status.put("wsUrl", getWsUrl());
                    status.put("h5Url", getH5Url());
                    status.put("clients", clients.size());
                    Response resp = newFixedLengthResponse(
                            Response.Status.OK, "application/json", status.toString());
                    addCorsHeaders(resp);
                    return resp;
                } catch (JSONException e) {
                    return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR, "text/plain", "Error");
                }
            }

            // Root redirects to H5
            if ("/".equals(uri) || uri.isEmpty()) {
                Response resp = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
                resp.addHeader("Location", "/h5/");
                return resp;
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }

        private Response serveH5(String uri) {
            String assetPath;
            if ("/h5".equals(uri) || "/h5/".equals(uri)) {
                assetPath = "h5/index.html";
            } else {
                assetPath = uri.substring(1); // Remove leading /
            }

            try {
                InputStream is = context.getAssets().open(assetPath);
                byte[] data = readAll(is);
                is.close();

                String mimeType = getMimeType(assetPath);
                Response resp = newFixedLengthResponse(
                        Response.Status.OK, mimeType, new java.io.ByteArrayInputStream(data), data.length);
                addCorsHeaders(resp);
                resp.addHeader("Cache-Control", "no-cache");
                return resp;
            } catch (IOException e) {
                return newFixedLengthResponse(
                        Response.Status.NOT_FOUND, "text/plain", "File not found: " + assetPath);
            }
        }

        private byte[] readAll(InputStream is) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }

        private void addCorsHeaders(Response resp) {
            resp.addHeader("Access-Control-Allow-Origin", "*");
            resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            resp.addHeader("Access-Control-Allow-Headers", "Content-Type");
        }
    }

    // ====== WebSocket Server (Java-WebSocket) ======

    private class WsServer extends WebSocketServer {

        WsServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            Log.d(TAG, "WS client connected: " + conn.getRemoteSocketAddress());
            clients.add(conn);
            // Send welcome
            try {
                JSONObject welcome = new JSONObject();
                welcome.put("type", "status");
                welcome.put("text", "TileTV 已连接");
                welcome.put("level", "done");
                conn.send(welcome.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Welcome message error", e);
            }
            if (listener != null) {
                listener.onClientConnected();
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            Log.d(TAG, "WS client disconnected: " + reason);
            clients.remove(conn);
            if (listener != null) {
                listener.onClientDisconnected();
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            if (listener != null) {
                listener.onClientMessage(message);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            Log.e(TAG, "WS error: " + (ex != null ? ex.getMessage() : "unknown"), ex);
            if (conn != null) {
                clients.remove(conn);
            }
        }

        @Override
        public void onStart() {
            Log.d(TAG, "WebSocket server started");
        }
    }
}
