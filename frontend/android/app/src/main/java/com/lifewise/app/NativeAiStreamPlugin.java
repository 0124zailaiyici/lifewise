package com.lifewise.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "NativeAiStream")
public class NativeAiStreamPlugin extends Plugin {
    private final Map<String, HttpURLConnection> activeConnections = new ConcurrentHashMap<>();

    @PluginMethod
    public void start(PluginCall call) {
        String requestId = call.getString("requestId", "");
        String url = call.getString("url", "");
        String apiKey = call.getString("apiKey", "");
        String body = call.getString("body", "{}");
        if (requestId.isEmpty() || url.isEmpty()) {
            call.reject("缺少流式请求参数");
            return;
        }
        call.resolve();
        new Thread(() -> execute(requestId, url, apiKey, body), "lifewise-ai-stream").start();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        String requestId = call.getString("requestId", "");
        HttpURLConnection connection = activeConnections.remove(requestId);
        if (connection != null) connection.disconnect();
        call.resolve();
    }

    private void execute(String requestId, String urlValue, String apiKey, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlValue).openConnection();
            activeConnections.put(requestId, connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream, application/json");
            if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
                output.flush();
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                emitError(requestId, status, "接口没有返回内容");
                return;
            }

            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    raw.append(line).append('\n');
                    if (status >= 200 && status < 300) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("data:")) {
                            String payload = trimmed.substring(5).trim();
                            if (!payload.isEmpty() && !"[DONE]".equals(payload)) emitChunk(requestId, payload);
                        }
                    }
                }
            }

            if (status >= 200 && status < 300) {
                JSObject data = new JSObject();
                data.put("requestId", requestId);
                data.put("rawText", raw.toString());
                notifyListeners("streamComplete", data, true);
            } else {
                emitError(requestId, status, raw.toString().trim());
            }
        } catch (Exception e) {
            emitError(requestId, 0, e.getMessage() == null ? "原生流式请求失败" : e.getMessage());
        } finally {
            activeConnections.remove(requestId);
            if (connection != null) connection.disconnect();
        }
    }

    private void emitChunk(String requestId, String payload) {
        JSObject data = new JSObject();
        data.put("requestId", requestId);
        data.put("payload", payload);
        notifyListeners("streamChunk", data, true);
    }

    private void emitError(String requestId, int status, String message) {
        JSObject data = new JSObject();
        data.put("requestId", requestId);
        data.put("status", status);
        data.put("message", message == null || message.isEmpty() ? "流式请求失败" : message);
        notifyListeners("streamError", data, true);
    }

    @Override
    protected void handleOnDestroy() {
        for (HttpURLConnection connection : activeConnections.values()) connection.disconnect();
        activeConnections.clear();
        super.handleOnDestroy();
    }
}
