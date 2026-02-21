package com.tiletv.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户记忆持久化存储。
 * 按域名(domain)组织记忆条目,存储到SharedPreferences。
 * 每个记忆包含 key, content, timestamp。
 * 支持: 保存记忆、按域名召回、格式化输出(注入Claude system prompt)、清除。
 */
public class MemoryStore {

    private static final String TAG = "MemoryStore";
    private static final String PREFS_NAME = "tiletv_memory";
    private static final String KEY_MEMORIES = "memories";
    private static final int MAX_MEMORIES_PER_DOMAIN = 20;
    private static final int MAX_GLOBAL_MEMORIES = 100;

    private SharedPreferences prefs;
    private JSONObject allMemories; // { "domain": [ {key, content, ts}, ... ] }

    public MemoryStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        String json = prefs.getString(KEY_MEMORIES, "{}");
        try {
            allMemories = new JSONObject(json);
        } catch (JSONException e) {
            allMemories = new JSONObject();
        }
    }

    private void save() {
        prefs.edit().putString(KEY_MEMORIES, allMemories.toString()).apply();
    }

    /**
     * 保存一条记忆
     */
    public void saveEntry(String domain, String key, String content) {
        try {
            JSONArray domainMemories = allMemories.optJSONArray(domain);
            if (domainMemories == null) {
                domainMemories = new JSONArray();
            }

            // 检查是否已有相同key的记忆,如果有则更新
            boolean found = false;
            for (int i = 0; i < domainMemories.length(); i++) {
                JSONObject entry = domainMemories.getJSONObject(i);
                if (key.equals(entry.optString("key"))) {
                    entry.put("content", content);
                    entry.put("ts", System.currentTimeMillis());
                    found = true;
                    break;
                }
            }

            if (!found) {
                JSONObject entry = new JSONObject();
                entry.put("key", key);
                entry.put("content", content);
                entry.put("ts", System.currentTimeMillis());
                domainMemories.put(entry);
            }

            // 限制每个域名的记忆数量
            while (domainMemories.length() > MAX_MEMORIES_PER_DOMAIN) {
                domainMemories.remove(0);
            }

            allMemories.put(domain, domainMemories);
            save();

            Log.d(TAG, "Memory saved: " + domain + "/" + key);
        } catch (JSONException e) {
            Log.e(TAG, "saveEntry error", e);
        }
    }

    /**
     * 获取某个域名的所有记忆
     */
    public List<String> getMemories(String domain) {
        List<String> result = new ArrayList<>();
        JSONArray domainMemories = allMemories.optJSONArray(domain);
        if (domainMemories == null) return result;

        for (int i = 0; i < domainMemories.length(); i++) {
            try {
                JSONObject entry = domainMemories.getJSONObject(i);
                result.add(entry.optString("key") + ": " + entry.optString("content"));
            } catch (JSONException e) {
                // skip
            }
        }
        return result;
    }

    /**
     * 获取格式化的记忆文本,用于注入Claude system prompt。
     */
    public String getFormattedMemory(String domain) {
        List<String> memories = getMemories(domain);
        // Also include global memories
        List<String> globalMemories = getMemories("_global");

        StringBuilder sb = new StringBuilder();
        if (!globalMemories.isEmpty()) {
            for (String m : globalMemories) {
                sb.append("- ").append(m).append("\n");
            }
        }
        if (!memories.isEmpty()) {
            for (String m : memories) {
                sb.append("- ").append(m).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 保存全局记忆(用户偏好、纠正等,不关联特定域名)
     */
    public void saveGlobalMemory(String key, String content) {
        saveEntry("_global", key, content);
    }

    /**
     * 获取所有域名
     */
    public List<String> getAllDomains() {
        List<String> domains = new ArrayList<>();
        java.util.Iterator<String> keys = allMemories.keys();
        while (keys.hasNext()) {
            domains.add(keys.next());
        }
        return domains;
    }

    /**
     * 清除指定域名的记忆
     */
    public void clearDomain(String domain) {
        allMemories.remove(domain);
        save();
    }

    /**
     * 清除所有记忆
     */
    public void clearAll() {
        allMemories = new JSONObject();
        save();
    }
}
