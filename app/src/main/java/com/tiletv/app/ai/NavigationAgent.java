package com.tiletv.app.ai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI导航Agent - 使用Claude API tool-use驱动WebView浏览器操作。
 */
public class NavigationAgent {

    private static final String TAG = "NavigationAgent";
    private static final int MAX_ITERATIONS = 50;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private String apiKey;
    private String baseUrl;
    private String model;
    private WebViewAutomation automation;
    private MemoryStore memoryStore;
    private OkHttpClient httpClient;

    private volatile boolean isProcessing = false;
    private volatile boolean isInterrupted = false;

    public interface Callback {
        void onStatus(String text, String level);
        void onFrame(String base64);
        void onComplete(String summary);
        void onError(String error);
    }

    public NavigationAgent(String apiKey, String baseUrl, String model,
                           WebViewAutomation automation, MemoryStore memoryStore) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "https://api.anthropic.com";
        this.model = model != null && !model.isEmpty() ? model : "claude-haiku-4-5-20251001";
        this.automation = automation;
        this.memoryStore = memoryStore;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public void interrupt() {
        isInterrupted = true;
    }

    /**
     * 处理语音指令 - 在后台线程运行
     */
    public void handleVoiceCommand(final String text, final Callback callback) {
        if (isProcessing) {
            callback.onStatus("正在处理上一个指令，请稍等...", "info");
            return;
        }

        isProcessing = true;
        isInterrupted = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    executeCommand(text, callback);
                } catch (Exception e) {
                    Log.e(TAG, "handleVoiceCommand error", e);
                    callback.onError("AI 错误: " + e.getMessage());
                } finally {
                    isProcessing = false;
                }
            }
        }).start();
    }

    private void executeCommand(String text, Callback callback) throws JSONException, IOException {
        callback.onStatus("AI 正在理解: \"" + text + "\"", "thinking");

        // Take initial screenshot
        String screenshotBase64 = automation.screenshot();
        String currentUrl = automation.getCurrentUrl();
        String domain = extractDomain(currentUrl);
        String memoryContext = memoryStore.getFormattedMemory(domain);

        String systemPrompt = buildSystemPrompt(currentUrl, memoryContext);

        // Build initial messages
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        JSONArray userContent = new JSONArray();

        // Screenshot image
        JSONObject imageBlock = new JSONObject();
        imageBlock.put("type", "image");
        JSONObject imageSource = new JSONObject();
        imageSource.put("type", "base64");
        imageSource.put("media_type", "image/jpeg");
        imageSource.put("data", screenshotBase64);
        imageBlock.put("source", imageSource);
        userContent.put(imageBlock);

        // Text instruction
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", "用户语音指令: \"" + text + "\"");
        userContent.put(textBlock);

        userMsg.put("content", userContent);
        messages.put(userMsg);

        // Send initial frame
        callback.onFrame(screenshotBase64);

        // Agentic loop
        int iteration = 0;
        java.util.List<String> actionHistory = new java.util.ArrayList<>();
        while (iteration < MAX_ITERATIONS && !isInterrupted) {
            iteration++;
            callback.onStatus("AI 思考中... (第" + iteration + "步)", "thinking");

            // Compact old screenshots
            compactMessages(messages);

            // Call Claude API
            JSONObject responseJson = callClaudeApi(systemPrompt, messages);
            if (responseJson == null) {
                callback.onError("API 调用失败");
                return;
            }

            // Extract content array
            JSONArray content = responseJson.getJSONArray("content");
            String stopReason = responseJson.optString("stop_reason", "");

            // Append assistant message
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", content);
            messages.put(assistantMsg);

            // Show text responses
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    String aiText = block.optString("text", "").trim();
                    if (!aiText.isEmpty()) {
                        callback.onStatus("AI: " + aiText.substring(0, Math.min(aiText.length(), 120)), "thinking");
                        Log.d(TAG, "[AI text] " + aiText.substring(0, Math.min(aiText.length(), 150)));
                    }
                }
            }

            // Check if done
            if ("end_turn".equals(stopReason)) {
                String finalText = getLastTextFromContent(content);
                callback.onComplete(finalText.isEmpty() ? "完成" : finalText);
                break;
            }

            // Collect tool_use blocks
            JSONArray toolUseBlocks = new JSONArray();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("tool_use".equals(block.optString("type"))) {
                    toolUseBlocks.put(block);
                }
            }

            if (toolUseBlocks.length() == 0) {
                callback.onComplete("完成");
                break;
            }

            // Execute tools and build results
            JSONArray toolResults = new JSONArray();
            for (int i = 0; i < toolUseBlocks.length(); i++) {
                if (isInterrupted) {
                    callback.onStatus("已打断", "done");
                    return;
                }

                JSONObject toolBlock = toolUseBlocks.getJSONObject(i);
                String toolName = toolBlock.getString("name");
                String toolId = toolBlock.getString("id");
                JSONObject input = toolBlock.optJSONObject("input");
                if (input == null) input = new JSONObject();

                String desc = describeAction(toolName, input);
                callback.onStatus("AI 执行: " + desc, "thinking");
                Log.d(TAG, "[AI tool] " + desc);

                // Execute tool
                String textResult = executeTool(toolName, input, domain);

                // Wait for page to settle
                boolean isNavAction = "navigate".equals(toolName) || "go_back".equals(toolName)
                        || ("press_key".equals(toolName) && "enter".equalsIgnoreCase(input.optString("key")))
                        || "click".equals(toolName);
                try {
                    Thread.sleep(isNavAction ? 1500 : 500);
                } catch (InterruptedException ignored) {}

                // Take screenshot after action
                String postScreenshot = automation.screenshot();
                callback.onFrame(postScreenshot);

                // Build tool result
                JSONObject toolResult = new JSONObject();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", toolId);

                JSONArray resultContent = new JSONArray();
                if (textResult != null && !textResult.isEmpty()) {
                    JSONObject resultText = new JSONObject();
                    resultText.put("type", "text");
                    resultText.put("text", textResult);
                    resultContent.put(resultText);
                }
                JSONObject resultImage = new JSONObject();
                resultImage.put("type", "image");
                JSONObject resultImageSource = new JSONObject();
                resultImageSource.put("type", "base64");
                resultImageSource.put("media_type", "image/jpeg");
                resultImageSource.put("data", postScreenshot);
                resultImage.put("source", resultImageSource);
                resultContent.put(resultImage);

                toolResult.put("content", resultContent);
                toolResults.put(toolResult);
            }

            // Append tool results as user message
            JSONObject toolResultMsg = new JSONObject();
            toolResultMsg.put("role", "user");
            toolResultMsg.put("content", toolResults);
            messages.put(toolResultMsg);

            // Stuck detection: 滑动窗口检测重复模式
            StringBuilder actionSig = new StringBuilder();
            for (int i = 0; i < toolUseBlocks.length(); i++) {
                try {
                    JSONObject tb = toolUseBlocks.getJSONObject(i);
                    String tn = tb.getString("name");
                    JSONObject ti = tb.optJSONObject("input");
                    actionSig.append(tn);
                    if ("click".equals(tn) && ti != null) {
                        // 100px网格，更宽松地匹配相近点击
                        int cx = (ti.optInt("x") / 100) * 100;
                        int cy = (ti.optInt("y") / 100) * 100;
                        actionSig.append(cx).append(",").append(cy);
                    }
                    actionSig.append("|");
                } catch (JSONException ignored) {}
            }
            actionHistory.add(actionSig.toString());

            int sz = actionHistory.size();
            boolean stuck = false;

            // 检测1：最后3步完全一样 (AAA)
            if (sz >= 3
                && actionHistory.get(sz-1).equals(actionHistory.get(sz-2))
                && actionHistory.get(sz-2).equals(actionHistory.get(sz-3))) {
                stuck = true;
            }

            // 检测2：最后6步形成ABABAB模式
            if (!stuck && sz >= 6
                && actionHistory.get(sz-1).equals(actionHistory.get(sz-3))
                && actionHistory.get(sz-3).equals(actionHistory.get(sz-5))
                && actionHistory.get(sz-2).equals(actionHistory.get(sz-4))) {
                stuck = true;
            }

            // 检测3：最近8步中，type_text出现4次以上且URL未变（输入反复失败）
            if (!stuck && sz >= 8) {
                int typeCount = 0;
                for (int hi = sz - 1; hi >= sz - 8; hi--) {
                    if (actionHistory.get(hi).contains("type_text")) typeCount++;
                }
                String nowUrl = automation.getCurrentUrl();
                if (typeCount >= 4 && nowUrl != null && nowUrl.equals(currentUrl)) {
                    stuck = true;
                }
            }

            if (stuck) {
                callback.onStatus("AI 检测到重复操作循环，已停止", "done");
                break;
            }
        }

        if (iteration >= MAX_ITERATIONS) {
            callback.onStatus("已达到最大步骤数(50步)，任务可能需要用户协助", "done");
        }

        // Update page info
        String finalUrl = automation.getCurrentUrl();
        String finalDomain = extractDomain(finalUrl);
        if (!domain.equals(finalDomain)) {
            memoryStore.saveEntry(finalDomain, "visit", "用户说\"" + text + "\"后到达此站");
        }
    }

    // ====== Claude API Call ======

    private JSONObject callClaudeApi(String systemPrompt, JSONArray messages) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt);
            body.put("messages", messages);
            body.put("tools", buildToolDefinitions());

            String url = baseUrl;
            if (!url.endsWith("/")) url += "/";
            url += "v1/messages";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(JSON_TYPE, body.toString()))
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                Log.e(TAG, "API error " + response.code() + ": " + responseBody.substring(0, Math.min(responseBody.length(), 200)));
                return null;
            }

            return new JSONObject(responseBody);
        } catch (Exception e) {
            Log.e(TAG, "callClaudeApi error", e);
            return null;
        }
    }

    // ====== Tool Definitions ======

    private JSONArray buildToolDefinitions() throws JSONException {
        JSONArray tools = new JSONArray();

        // click
        tools.put(buildTool("click",
                "Click at specific x,y coordinates on the page screenshot. Coordinates are in pixels, origin is top-left.",
                new String[]{"x", "y"}, new String[]{"number", "number"},
                new String[]{"X coordinate (pixels from left)", "Y coordinate (pixels from top)"},
                new String[]{"x", "y"}));

        // type_text
        tools.put(buildTool("type_text",
                "Type text into the currently focused element. Use click first to focus an input field.",
                new String[]{"text"}, new String[]{"string"},
                new String[]{"Text to type"},
                new String[]{"text"}));

        // press_key
        tools.put(buildTool("press_key",
                "Press a keyboard key. Common keys: Enter, Tab, Escape, Backspace, ArrowUp, ArrowDown. For combos: Control+a",
                new String[]{"key"}, new String[]{"string"},
                new String[]{"Key name (e.g. Enter, Escape, Control+a)"},
                new String[]{"key"}));

        // scroll
        JSONObject scrollTool = new JSONObject();
        scrollTool.put("name", "scroll");
        scrollTool.put("description", "Scroll the page. Use direction 'up' or 'down'. Default scrolls 400 pixels.");
        JSONObject scrollSchema = new JSONObject();
        scrollSchema.put("type", "object");
        JSONObject scrollProps = new JSONObject();
        JSONObject dirProp = new JSONObject();
        dirProp.put("type", "string");
        dirProp.put("description", "Scroll direction");
        JSONArray dirEnum = new JSONArray();
        dirEnum.put("up");
        dirEnum.put("down");
        dirProp.put("enum", dirEnum);
        scrollProps.put("direction", dirProp);
        JSONObject amtProp = new JSONObject();
        amtProp.put("type", "number");
        amtProp.put("description", "Pixels to scroll (default 400)");
        scrollProps.put("amount", amtProp);
        scrollSchema.put("properties", scrollProps);
        JSONArray scrollReq = new JSONArray();
        scrollReq.put("direction");
        scrollSchema.put("required", scrollReq);
        scrollTool.put("input_schema", scrollSchema);
        tools.put(scrollTool);

        // navigate
        tools.put(buildTool("navigate",
                "Navigate the browser to a URL.",
                new String[]{"url"}, new String[]{"string"},
                new String[]{"Full URL to navigate to"},
                new String[]{"url"}));

        // go_back
        JSONObject goBackTool = new JSONObject();
        goBackTool.put("name", "go_back");
        goBackTool.put("description", "Go back to the previous page.");
        JSONObject emptySchema = new JSONObject();
        emptySchema.put("type", "object");
        emptySchema.put("properties", new JSONObject());
        goBackTool.put("input_schema", emptySchema);
        tools.put(goBackTool);

        // wait
        JSONObject waitTool = new JSONObject();
        waitTool.put("name", "wait");
        waitTool.put("description", "Wait for seconds for the page to load.");
        JSONObject waitSchema = new JSONObject();
        waitSchema.put("type", "object");
        JSONObject waitProps = new JSONObject();
        JSONObject secProp = new JSONObject();
        secProp.put("type", "number");
        secProp.put("description", "Seconds to wait (1-10, default 2)");
        waitProps.put("seconds", secProp);
        waitSchema.put("properties", waitProps);
        waitTool.put("input_schema", waitSchema);
        tools.put(waitTool);

        // screenshot
        JSONObject screenshotTool = new JSONObject();
        screenshotTool.put("name", "screenshot");
        screenshotTool.put("description", "Take a fresh screenshot to observe the current state.");
        screenshotTool.put("input_schema", emptySchema);
        tools.put(screenshotTool);

        // save_memory
        tools.put(buildTool("save_memory",
                "Save a navigation insight for future visits (e.g. where search box is, user preferences, corrections from user).",
                new String[]{"key", "content"}, new String[]{"string", "string"},
                new String[]{"Memory key", "What to remember"},
                new String[]{"key", "content"}));

        // recall_memory
        JSONObject recallTool = new JSONObject();
        recallTool.put("name", "recall_memory");
        recallTool.put("description", "Recall all saved memories about the current website.");
        recallTool.put("input_schema", emptySchema);
        tools.put(recallTool);

        return tools;
    }

    private JSONObject buildTool(String name, String description,
                                 String[] propNames, String[] propTypes,
                                 String[] propDescs, String[] required) throws JSONException {
        JSONObject tool = new JSONObject();
        tool.put("name", name);
        tool.put("description", description);

        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        for (int i = 0; i < propNames.length; i++) {
            JSONObject prop = new JSONObject();
            prop.put("type", propTypes[i]);
            prop.put("description", propDescs[i]);
            properties.put(propNames[i], prop);
        }
        schema.put("properties", properties);

        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        schema.put("required", req);

        tool.put("input_schema", schema);
        return tool;
    }

    // ====== Tool Execution ======

    private String executeTool(String name, JSONObject input, String domain) {
        try {
            switch (name) {
                case "click": {
                    float x = (float) input.optDouble("x", 0);
                    float y = (float) input.optDouble("y", 0);
                    automation.click(x, y);
                    return "Clicked at (" + (int) x + ", " + (int) y + ")";
                }
                case "type_text": {
                    String text = input.optString("text", "");
                    automation.typeText(text);
                    return "Typed \"" + text + "\"";
                }
                case "press_key": {
                    String key = input.optString("key", "");
                    automation.pressKey(key);
                    return "Pressed key: " + key;
                }
                case "scroll": {
                    String direction = input.optString("direction", "down");
                    int amount = input.optInt("amount", 400);
                    automation.scroll(direction, amount);
                    return "Scrolled " + direction + " by " + amount + "px";
                }
                case "navigate": {
                    String url = input.optString("url", "");
                    automation.navigate(url);
                    return "Navigated to " + url;
                }
                case "go_back": {
                    boolean wentBack = automation.goBack();
                    return wentBack ? "Went back" : "Cannot go back";
                }
                case "wait": {
                    int seconds = Math.min(input.optInt("seconds", 2), 10);
                    automation.waitSeconds(seconds);
                    return "Waited " + seconds + "s";
                }
                case "screenshot": {
                    return "Screenshot taken";
                }
                case "save_memory": {
                    String key = input.optString("key", "");
                    String content = input.optString("content", "");
                    memoryStore.saveEntry(domain, key, content);
                    return "Memory saved: " + key;
                }
                case "recall_memory": {
                    String memories = memoryStore.getFormattedMemory(domain);
                    return memories.isEmpty() ? "No memories found for this domain" : memories;
                }
                default:
                    return "Unknown tool: " + name;
            }
        } catch (Exception e) {
            Log.e(TAG, "executeTool " + name + " error", e);
            return "Error: " + e.getMessage();
        }
    }

    // ====== Message Compaction ======

    private void compactMessages(JSONArray messages) {
        // Find user messages with image blocks and strip old ones
        int imageCount = 0;
        // Count from the end
        for (int i = messages.length() - 1; i >= 0; i--) {
            try {
                JSONObject msg = messages.getJSONObject(i);
                if (!"user".equals(msg.optString("role"))) continue;

                JSONArray content = msg.optJSONArray("content");
                if (content == null) continue;

                boolean hasImage = false;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject block = content.getJSONObject(j);
                    if ("image".equals(block.optString("type"))) {
                        hasImage = true;
                        break;
                    }
                    if ("tool_result".equals(block.optString("type"))) {
                        JSONArray rc = block.optJSONArray("content");
                        if (rc != null) {
                            for (int k = 0; k < rc.length(); k++) {
                                if ("image".equals(rc.getJSONObject(k).optString("type"))) {
                                    hasImage = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (hasImage) break;
                }

                if (hasImage) {
                    imageCount++;
                    if (imageCount > 2) {
                        // Strip images from this old message
                        stripImages(content);
                    }
                }
            } catch (JSONException e) {
                // skip
            }
        }
    }

    private void stripImages(JSONArray content) throws JSONException {
        for (int j = 0; j < content.length(); j++) {
            JSONObject block = content.getJSONObject(j);
            if ("image".equals(block.optString("type"))) {
                JSONObject replacement = new JSONObject();
                replacement.put("type", "text");
                replacement.put("text", "[screenshot removed]");
                content.put(j, replacement);
            }
            if ("tool_result".equals(block.optString("type"))) {
                JSONArray rc = block.optJSONArray("content");
                if (rc != null) {
                    for (int k = 0; k < rc.length(); k++) {
                        if ("image".equals(rc.getJSONObject(k).optString("type"))) {
                            JSONObject replacement = new JSONObject();
                            replacement.put("type", "text");
                            replacement.put("text", "[screenshot removed]");
                            rc.put(k, replacement);
                        }
                    }
                }
            }
        }
    }

    // ====== System Prompt ======

    private String buildSystemPrompt(String url, String memoryContext) {
        String vw = "1280";
        String vh = "720";
        return "你是一个电视遥控器AI助手。用户通过语音给你下达指令，你需要用工具操控浏览器来执行。\n\n"
                + "你可以看到浏览器的截图（" + vw + "x" + vh + "像素），根据截图内容精确操作。\n\n"
                + "操作规则:\n"
                + "- 首先用 recall_memory 回忆此网站的操作经验，优先按记忆中的路径操作\n"
                + "- 仔细观察截图，理解当前页面状态后再行动\n"
                + "- 一步一步执行，每步操作后会自动获得新截图\n"
                + "- 需要打开新网站时，用 navigate 工具\n"
                + "- 搜索时优先策略：直接用 navigate 跳转搜索URL（如 https://search.bilibili.com/all?keyword=关键词），比在搜索框输入更可靠\n"
                + "- 如果必须用搜索框：click 点击 → type_text 输入 → press_key Enter。若2次无效则换 navigate\n"
                + "- 常见搜索URL: B站 search.bilibili.com/all?keyword=, 百度 www.baidu.com/s?wd=, Google www.google.com/search?q=, YouTube www.youtube.com/results?search_query=\n"
                + "- 如果输入框有旧文字，先 press_key(\"Control+a\") 全选，然后 type_text\n"
                + "- 点击坐标要精确，仔细看截图中元素的位置\n"
                + "- 页面跳转后用 wait 等待加载，再 screenshot 观察\n"
                + "- 遇到弹窗/广告/登录框时，用 press_key(\"Escape\") 关闭，或点击关闭按钮，找不到就继续操作\n"
                + "- 操作完成后简短总结，并用 save_memory 保存成功的操作路径（如\"搜索视频: navigate到search.bilibili.com → 点击结果 → 选集\"），下次可以更快\n"
                + "- 如果用户纠正了你的操作，立即用 save_memory 记住纠正\n"
                + "- 如果你发现自己在反复执行相同操作没效果，立即换策略\n\n"
                + "当前URL: " + url
                + (memoryContext != null && !memoryContext.isEmpty() ? "\n\n网站记忆:\n" + memoryContext : "");
    }

    // ====== Helpers ======

    private String describeAction(String toolName, JSONObject input) {
        switch (toolName) {
            case "click":
                return "点击 (" + input.optInt("x") + ", " + input.optInt("y") + ")";
            case "type_text":
                String text = input.optString("text", "");
                return "输入 \"" + (text.length() > 30 ? text.substring(0, 30) + "..." : text) + "\"";
            case "press_key":
                return "按键 " + input.optString("key", "");
            case "scroll":
                return ("up".equals(input.optString("direction")) ? "向上" : "向下") + "滚动";
            case "navigate":
                return "打开 " + input.optString("url", "");
            case "go_back":
                return "返回上一页";
            case "wait":
                return "等待 " + input.optInt("seconds", 2) + "秒";
            case "screenshot":
                return "截图观察";
            case "save_memory":
                return "记忆: " + input.optString("key", "");
            case "recall_memory":
                return "回忆网站经验";
            default:
                return toolName;
        }
    }

    private String getLastTextFromContent(JSONArray content) {
        String lastText = "";
        for (int i = 0; i < content.length(); i++) {
            try {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    String t = block.optString("text", "").trim();
                    if (!t.isEmpty()) lastText = t;
                }
            } catch (JSONException e) {
                // skip
            }
        }
        return lastText.length() > 120 ? lastText.substring(0, 120) : lastText;
    }

    private String extractDomain(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
