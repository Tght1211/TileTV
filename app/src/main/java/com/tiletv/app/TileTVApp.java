package com.tiletv.app;

import android.app.Application;
import android.util.Log;

import com.tiletv.app.server.TileTVServer;

/**
 * Application类 - 管理TileTVServer的生命周期。
 * 服务器在Application创建时启动,在进程结束时停止。
 */
public class TileTVApp extends Application {

    private static final String TAG = "TileTVApp";
    private static TileTVServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        startServer();
    }

    private void startServer() {
        if (server == null) {
            server = new TileTVServer(this);
            try {
                server.start();
                Log.d(TAG, "TileTV Server started: " + server.getH5Url());
            } catch (Exception e) {
                Log.e(TAG, "Failed to start server", e);
            }
        }
    }

    public static TileTVServer getServer() {
        return server;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
