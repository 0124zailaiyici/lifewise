package com.lifewise.app;

import android.Manifest;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "NativeSpeech",
    permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microphone") }
)
public class NativeSpeechPlugin extends Plugin implements RecognitionListener {
    private static final String LOG_TAG = "LifeWiseSpeech";
    private SpeechRecognizer recognizer;
    private MediaRecorder recorder;
    private File recordingFile;
    private PluginCall pendingStartCall;
    private String transcribeUrl = "";
    private String audioUrl = "";
    private String chatUrl = "";
    private String apiKey = "";
    private String speechModel = "whisper-1";
    private String chatAudioModel = "";
    private String transcribeMode = "auto";
    private boolean allowAiTranscription;
    private boolean fallbackRecording;

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("microphone") != PermissionState.GRANTED) {
            pendingStartCall = call;
            requestPermissionForAlias("microphone", call, "microphonePermissionResult");
            return;
        }
        startRecognition(call);
    }

    @PermissionCallback
    public void microphonePermissionResult(PluginCall call) {
        pendingStartCall = null;
        if (getPermissionState("microphone") == PermissionState.GRANTED) {
            startRecognition(call);
        } else {
            call.reject("麦克风权限被拒绝，请在系统设置中允许 LifeWise 使用麦克风");
        }
    }

    private void startRecognition(PluginCall call) {
        transcribeUrl = call.getString("transcribeUrl", "");
        audioUrl = call.getString("audioUrl", transcribeUrl);
        chatUrl = call.getString("chatUrl", "");
        apiKey = call.getString("apiKey", "");
        speechModel = call.getString("speechModel", "whisper-1");
        chatAudioModel = call.getString("chatAudioModel", "");
        transcribeMode = call.getString("transcribeMode", "auto");
        allowAiTranscription = call.getBoolean("allowAiTranscription", false);
        boolean preferAiTranscription = call.getBoolean("preferAiTranscription", false);
        String language = call.getString("language", Locale.getDefault().toLanguageTag());

        getActivity().runOnUiThread(() -> {
            stopRecognizer();
            stopRecorder(false);
            if (preferAiTranscription) {
                startFallbackRecording(call);
                return;
            }
            if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
                startFallbackRecording(call);
                return;
            }

            fallbackRecording = false;
            recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
            JSObject result = new JSObject();
            result.put("mode", "system");
            call.resolve(result);
            emitState("listening", false);
        });
    }

    private void startFallbackRecording(PluginCall call) {
        if (!allowAiTranscription) {
            String message = "手机系统语音服务不可用。请点输入框后使用系统输入法的语音按钮；如需应用内麦克风，请在“我的”页面高级设置配置语音转写";
            if (call != null) call.reject(message);
            else emitErrorMessage(message, -2);
            return;
        }
        boolean hasEndpoint = "qwen-chat".equals(transcribeMode)
            ? !effectiveChatUrl().isEmpty()
            : !effectiveAudioUrl().isEmpty() || !effectiveChatUrl().isEmpty();
        if (!hasEndpoint || apiKey.isEmpty()) {
            if (call != null) call.reject("手机没有系统语音服务，且 AI 语音识别接口未配置");
            else emitErrorMessage("手机没有系统语音服务，且 AI 语音识别接口未配置", -2);
            return;
        }
        try {
            recordingFile = new File(getContext().getCacheDir(), "lifewise-voice-" + System.currentTimeMillis() + ".m4a");
            recorder = createMediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setMaxDuration(60000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.setOnInfoListener((mediaRecorder, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecorder(true);
                }
            });
            recorder.prepare();
            recorder.start();
            fallbackRecording = true;
            if (call != null) {
                JSObject result = new JSObject();
                result.put("mode", "recording");
                call.resolve(result);
            }
            emitState("recording", true);
        } catch (Exception e) {
            releaseRecorder();
            deleteRecording();
            if (call != null) call.reject("无法启动录音：" + safeMessage(e));
            else emitErrorMessage("无法启动录音：" + safeMessage(e), -3);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            JSObject result = new JSObject();
            if (fallbackRecording && recorder != null) {
                stopRecorder(true);
                result.put("mode", "transcribing");
            } else if (recognizer != null) {
                try { recognizer.stopListening(); } catch (Exception ignored) {}
                result.put("mode", "system");
            } else {
                result.put("mode", "idle");
            }
            call.resolve(result);
        });
    }

    private void stopRecorder(boolean transcribe) {
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            releaseRecorder();
            fallbackRecording = false;
            deleteRecording();
            if (transcribe) emitErrorMessage("录音时间太短，请重新点击麦克风并说完后再停止", -1);
            return;
        }
        releaseRecorder();
        fallbackRecording = false;
        if (transcribe && recordingFile != null && recordingFile.exists()) {
            File audio = recordingFile;
            emitState("transcribing", true);
            new Thread(() -> transcribeAudio(audio), "lifewise-speech-transcribe").start();
        } else {
            deleteRecording();
        }
    }

    private void transcribeAudio(File audio) {
        try {
            byte[] audioBytes = readAudioBytes(audio);
            TranscriptionAttempt attempt;
            if ("qwen-chat".equals(transcribeMode)) {
                attempt = requestChatTranscription(
                    effectiveChatUrl(),
                    speechModel,
                    audioBytes,
                    true
                );
                logAttempt("qwen-chat", speechModel, attempt);
            } else if ("auto".equals(transcribeMode)) {
                attempt = requestAutomaticTranscription(audio, audioBytes);
            } else {
                attempt = requestOpenAiTranscription(audio, effectiveAudioUrl(), speechModel);
                logAttempt("audio-transcriptions", speechModel, attempt);
            }
            deliverTranscription(attempt, "auto".equals(transcribeMode));
        } catch (Exception e) {
            Log.w(LOG_TAG, "transcription request failed: " + e.getClass().getSimpleName());
            emitErrorMessage("AI 语音识别请求失败，请检查网络后重试", 0);
        } finally {
            finishAudioFile(audio);
        }
    }

    private TranscriptionAttempt requestAutomaticTranscription(File audio, byte[] audioBytes) throws Exception {
        TranscriptionAttempt attempt = TranscriptionAttempt.unsupported();
        String resolvedAudioUrl = effectiveAudioUrl();
        if (!resolvedAudioUrl.isEmpty()) {
            attempt = requestOpenAiTranscription(audio, resolvedAudioUrl, speechModel);
            logAttempt("audio-transcriptions", speechModel, attempt);
            if (attempt.isSuccess() || !SpeechFallbackPolicy.shouldTryAlternative(
                attempt.status,
                !attempt.transcript.isEmpty()
            )) return attempt;
        }

        String resolvedChatUrl = effectiveChatUrl();
        if (!resolvedChatUrl.isEmpty()) {
            attempt = requestChatTranscription(resolvedChatUrl, "qwen3-asr-flash", audioBytes, true);
            logAttempt("qwen-chat", "qwen3-asr-flash", attempt);
            if (attempt.isSuccess() || !SpeechFallbackPolicy.shouldTryAlternative(
                attempt.status,
                !attempt.transcript.isEmpty()
            )) return attempt;

            if (!chatAudioModel.isEmpty() && !"qwen3-asr-flash".equalsIgnoreCase(chatAudioModel)) {
                attempt = requestChatTranscription(resolvedChatUrl, chatAudioModel, audioBytes, false);
                logAttempt("chat-audio", chatAudioModel, attempt);
            }

        }
        return attempt;
    }

    private TranscriptionAttempt requestOpenAiTranscription(File audio, String url, String model) throws Exception {
        HttpURLConnection connection = null;
        String boundary = "----LifeWise" + System.currentTimeMillis();
        try {
            connection = openPostConnection(url, "multipart/form-data; boundary=" + boundary);
            try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                writeFormField(output, boundary, "model", model);
                writeFormField(output, boundary, "language", "zh");
                output.writeBytes("--" + boundary + "\r\n");
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\n");
                output.writeBytes("Content-Type: audio/mp4\r\n\r\n");
                try (FileInputStream input = new FileInputStream(audio)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.writeBytes("\r\n--" + boundary + "--\r\n");
                output.flush();
            }
            return readTranscriptionResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private TranscriptionAttempt requestChatTranscription(
        String url,
        String model,
        byte[] audioBytes,
        boolean qwenStyle
    ) throws Exception {
        HttpURLConnection connection = null;
        try {
            String encoded = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
            JSONObject inputAudio = new JSONObject().put(
                "data",
                qwenStyle ? "data:audio/mp4;base64," + encoded : encoded
            );
            if (!qwenStyle) inputAudio.put("format", "mp4");

            JSONArray content = new JSONArray();
            if (!qwenStyle) {
                content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", "请准确转写这段语音，只返回转写文字。"));
            }
            content.put(new JSONObject().put("type", "input_audio").put("input_audio", inputAudio));

            JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("content", content)))
                .put("stream", false);
            if (qwenStyle) {
                body.put("asr_options", new JSONObject().put("language", "zh").put("enable_itn", true));
            }

            connection = openPostConnection(url, "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                output.write(payload);
                output.flush();
            }
            return readTranscriptionResponse(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection openPostConnection(String urlValue, String contentType) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", contentType);
        return connection;
    }

    private TranscriptionAttempt readTranscriptionResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String raw = readAll(stream);
        if (status < 200 || status >= 300) {
            return new TranscriptionAttempt(status, "", extractError(raw));
        }
        String transcript = extractTranscript(raw);
        if (transcript.isEmpty()) {
            return new TranscriptionAttempt(status, "", "接口没有返回转写文字");
        }
        return new TranscriptionAttempt(status, transcript, "");
    }

    private void deliverTranscription(TranscriptionAttempt attempt, boolean automatic) {
        if (!attempt.isSuccess()) {
            if (attempt.status == 401 || attempt.status == 403) {
                emitErrorMessage("AI 语音识别鉴权失败，请检查 API Key", attempt.status);
            } else if (attempt.status == 429) {
                emitErrorMessage("AI 语音识别请求过于频繁，请稍后重试", attempt.status);
            } else if (automatic || SpeechFallbackPolicy.isUnsupportedStatus(attempt.status)) {
                emitErrorMessage("当前接口不支持语音转写，请在“我的”页面填写语音接口地址、Key 和语音模型", attempt.status);
            } else {
                emitErrorMessage("AI 语音接口没有返回文字，请在“我的”页面检查语音模型", attempt.status);
            }
            return;
        }
        JSObject data = new JSObject();
        data.put("text", attempt.transcript);
        data.put("isFinal", true);
        notifyListeners("speechResult", data, true);
        notifyListeners("speechEnd", new JSObject(), true);
    }

    private byte[] readAudioBytes(File audio) throws Exception {
        byte[] audioBytes = new byte[(int) audio.length()];
        try (FileInputStream input = new FileInputStream(audio)) {
            int offset = 0;
            while (offset < audioBytes.length) {
                int count = input.read(audioBytes, offset, audioBytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
        }
        return audioBytes;
    }

    private void logAttempt(String transport, String model, TranscriptionAttempt attempt) {
        Log.i(
            LOG_TAG,
            "transport=" + transport + " model=" + model + " status=" + attempt.status
                + " transcript=" + !attempt.transcript.isEmpty()
        );
    }

    private String effectiveAudioUrl() {
        return audioUrl.isEmpty() ? transcribeUrl : audioUrl;
    }

    private String effectiveChatUrl() {
        return chatUrl.isEmpty() && "qwen-chat".equals(transcribeMode) ? transcribeUrl : chatUrl;
    }

    private static final class TranscriptionAttempt {
        private final int status;
        private final String transcript;
        private final String error;

        private TranscriptionAttempt(int status, String transcript, String error) {
            this.status = status;
            this.transcript = transcript;
            this.error = error;
        }

        private static TranscriptionAttempt unsupported() {
            return new TranscriptionAttempt(0, "", "没有可用的语音转写接口");
        }

        private boolean isSuccess() {
            return status >= 200 && status < 300 && !transcript.isEmpty();
        }
    }

    private void finishAudioFile(File audio) {
        if (audio.exists()) audio.delete();
        recordingFile = null;
    }

    private void writeFormField(DataOutputStream output, String boundary, String name, String value) throws Exception {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private String extractTranscript(String raw) {
        try {
            JSONObject data = new JSONObject(raw);
            String direct = firstText(data, "text", "transcript", "transcription");
            if (!direct.isEmpty()) return direct;
            JSONObject output = data.optJSONObject("output");
            if (output != null) {
                String value = firstText(output, "text", "transcript", "transcription");
                if (!value.isEmpty()) return value;
            }
            JSONObject result = data.optJSONObject("result");
            if (result != null) {
                String value = firstText(result, "text", "transcript", "transcription");
                if (!value.isEmpty()) return value;
            }
            JSONArray choices = data.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject message = choices.optJSONObject(0) == null ? null : choices.optJSONObject(0).optJSONObject("message");
                if (message != null) return message.optString("content", "").trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String firstText(JSONObject data, String... keys) {
        for (String key : keys) {
            String value = data.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String extractError(String raw) {
        try {
            JSONObject data = new JSONObject(raw);
            JSONObject error = data.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) return message;
            }
            String message = data.optString("message", "");
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {}
        String text = raw == null ? "" : raw.trim();
        return text.isEmpty() ? "接口未返回错误详情" : text.substring(0, Math.min(text.length(), 240));
    }

    private void emitResult(Bundle results, boolean isFinal) {
        if (results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        JSObject data = new JSObject();
        data.put("text", matches.get(0));
        data.put("isFinal", isFinal);
        notifyListeners("speechResult", data, true);
    }

    private void emitState(String state, boolean fallback) {
        JSObject data = new JSObject();
        data.put("state", state);
        data.put("fallback", fallback);
        notifyListeners("speechState", data, true);
    }

    private void emitError(int code) {
        boolean systemServiceFailed = code == SpeechRecognizer.ERROR_CLIENT
            || code == SpeechRecognizer.ERROR_SERVER
            || code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            || code == SpeechRecognizer.ERROR_NETWORK
            || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT;
        if (systemServiceFailed && !transcribeUrl.isEmpty() && !apiKey.isEmpty()) {
            stopRecognizer();
            getActivity().runOnUiThread(() -> startFallbackRecording(null));
            return;
        }
        String message;
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: message = "麦克风录音失败"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "没有麦克风权限"; break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "系统语音识别网络不可用"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "没有识别到清晰语音，请重试"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "没有听到说话"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "语音识别正在忙，请稍后重试"; break;
            default: message = "语音识别失败（" + code + "）";
        }
        emitErrorMessage(message, code);
    }

    private void emitErrorMessage(String message, int code) {
        JSObject data = new JSObject();
        data.put("message", message);
        data.put("code", code);
        notifyListeners("speechError", data, true);
    }

    private void stopRecognizer() {
        if (recognizer == null) return;
        try { recognizer.stopListening(); } catch (Exception ignored) {}
        try { recognizer.destroy(); } catch (Exception ignored) {}
        recognizer = null;
    }

    @SuppressWarnings("deprecation")
    private MediaRecorder createMediaRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new MediaRecorder(getContext());
        }
        return new MediaRecorder();
    }

    private void releaseRecorder() {
        if (recorder == null) return;
        try { recorder.reset(); } catch (Exception ignored) {}
        try { recorder.release(); } catch (Exception ignored) {}
        recorder = null;
    }

    private void deleteRecording() {
        if (recordingFile != null && recordingFile.exists()) recordingFile.delete();
        recordingFile = null;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().trim().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onError(int error) { emitError(error); stopRecognizer(); }
    @Override public void onResults(Bundle results) {
        emitResult(results, true);
        notifyListeners("speechEnd", new JSObject(), true);
        stopRecognizer();
    }
    @Override public void onPartialResults(Bundle partialResults) { emitResult(partialResults, false); }
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void handleOnDestroy() {
        stopRecognizer();
        stopRecorder(false);
        deleteRecording();
        super.handleOnDestroy();
    }
}
