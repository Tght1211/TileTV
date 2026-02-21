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

import com.tiletv.app.server.TileTVServer;

public class SettingsActivity extends AppCompatActivity {

    private EditText etApiKey;
    private EditText etBaseUrl;
    private EditText etModel;
    private Button btnSave;
    private TextView tvH5Url;

    private static final String PREFS_NAME = "tiletv_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        hideSystemUI();

        etApiKey = findViewById(R.id.et_api_key);
        etBaseUrl = findViewById(R.id.et_base_url);
        etModel = findViewById(R.id.et_model);
        btnSave = findViewById(R.id.btn_save);
        tvH5Url = findViewById(R.id.tv_h5_url);

        loadSettings();

        // Show H5 URL
        TileTVServer server = TileTVApp.getServer();
        if (server != null && tvH5Url != null) {
            tvH5Url.setText("H5 遥控: " + server.getH5Url());
        }

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
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
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etApiKey.setText(prefs.getString("api_key", ""));
        etBaseUrl.setText(prefs.getString("api_base_url", "https://api.anthropic.com"));
        etModel.setText(prefs.getString("api_model", "claude-haiku-4-5-20251001"));
    }

    private void saveSettings() {
        String apiKey = etApiKey.getText().toString().trim();
        String baseUrl = etBaseUrl.getText().toString().trim();
        String model = etModel.getText().toString().trim();

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        if (model.isEmpty()) {
            model = "claude-haiku-4-5-20251001";
        }
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.anthropic.com";
        }

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("api_key", apiKey);
        editor.putString("api_base_url", baseUrl);
        editor.putString("api_model", model);
        editor.apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
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
