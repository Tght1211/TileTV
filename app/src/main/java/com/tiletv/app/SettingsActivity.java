package com.tiletv.app;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiletv.app.ws.WebSocketManager;

/**
 * Settings Activity - Apple TV style dark settings page.
 *
 * Features:
 * - Server address input (host IP or hostname)
 * - Port input (default 9870)
 * - Test Connection button - attempts WebSocket connection and shows result
 * - Save button - persists settings to SharedPreferences
 * - H5 voice control URL display
 * - BACK key returns to MainActivity
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText etHost;
    private EditText etPort;
    private Button btnTest;
    private Button btnSave;
    private TextView tvTestResult;
    private TextView tvH5Url;

    private static final String PREFS_NAME = "tiletv_prefs";
    private static final String KEY_SERVER_HOST = "server_host";
    private static final String KEY_SERVER_PORT = "server_port";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        hideSystemUI();

        initViews();
        loadSettings();
        setupListeners();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 19) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void initViews() {
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        btnTest = findViewById(R.id.btn_test);
        btnSave = findViewById(R.id.btn_save);
        tvTestResult = findViewById(R.id.tv_test_result);
        tvH5Url = findViewById(R.id.tv_h5_url);
    }

    /**
     * Load saved settings from SharedPreferences and populate fields.
     */
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String host = prefs.getString(KEY_SERVER_HOST, "");
        int port = prefs.getInt(KEY_SERVER_PORT, 9870);

        etHost.setText(host);
        etPort.setText(String.valueOf(port));
        updateH5Url(host, port);
    }

    private void setupListeners() {
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    /**
     * Test WebSocket connection to the specified server.
     */
    private void testConnection() {
        final String host = etHost.getText().toString().trim();
        final String portStr = etPort.getText().toString().trim();

        if (host.isEmpty()) {
            showTestResult("请输入服务器地址", 0xFFFF453A);
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showTestResult("端口格式错误", 0xFFFF453A);
            return;
        }

        showTestResult("正在测试连接...", 0xFFFFD60A);
        btnTest.setEnabled(false);

        final String wsUrl = "ws://" + host + ":" + port;

        // Test connection on background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Disconnect existing connection first
                    WebSocketManager.getInstance().disconnect();
                    Thread.sleep(500);

                    WebSocketManager.getInstance().connect(wsUrl, new WebSocketManager.Callback() {
                        @Override
                        public void onConnected() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showTestResult("连接成功!", 0xFF30D158);
                                    btnTest.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onDisconnected() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (tvTestResult != null
                                            && "正在测试连接...".equals(tvTestResult.getText().toString())) {
                                        showTestResult("连接失败", 0xFFFF453A);
                                    }
                                    btnTest.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onMessage(String message) {
                            // Not needed during test
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showTestResult("连接失败: " + e.getMessage(), 0xFFFF453A);
                            btnTest.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Show test connection result.
     */
    private void showTestResult(String text, int color) {
        if (tvTestResult != null) {
            tvTestResult.setVisibility(View.VISIBLE);
            tvTestResult.setText(text);
            tvTestResult.setTextColor(color);
        }
    }

    /**
     * Save server settings to SharedPreferences.
     */
    private void saveSettings() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();

        if (host.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SERVER_HOST, host);
        editor.putInt(KEY_SERVER_PORT, port);
        editor.apply();

        updateH5Url(host, port);

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();

        // Reconnect with new settings
        final String wsUrl = "ws://" + host + ":" + port;
        new Thread(new Runnable() {
            @Override
            public void run() {
                WebSocketManager.getInstance().disconnect();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                WebSocketManager.getInstance().connect(wsUrl, new WebSocketManager.Callback() {
                    @Override
                    public void onConnected() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showTestResult("已连接到新服务器", 0xFF30D158);
                            }
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        // Will auto-reconnect
                    }

                    @Override
                    public void onMessage(String message) {
                        // Not used here
                    }
                });
            }
        }).start();
    }

    /**
     * Update the H5 voice control URL display.
     */
    private void updateH5Url(String host, int port) {
        if (tvH5Url != null) {
            if (host != null && host.length() > 0) {
                tvH5Url.setText("H5语音控制: http://" + host + ":" + port + "/h5");
            } else {
                tvH5Url.setText("H5语音控制: 未配置");
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
}
