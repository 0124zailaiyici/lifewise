package com.lifewise.app;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Locale;

@CapacitorPlugin(
    name = "NativeSpeech",
    permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microphone") }
)
public class NativeSpeechPlugin extends Plugin implements RecognitionListener {
    private SpeechRecognizer recognizer;
    private PluginCall pendingStartCall;

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
        getActivity().runOnUiThread(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
                call.reject("手机没有可用的系统语音识别服务");
                return;
            }
            stopRecognizer();
            recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, call.getString("language", Locale.getDefault().toLanguageTag()));
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
            call.resolve();
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getActivity().runOnUiThread(this::stopRecognizer);
        call.resolve();
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

    private void emitError(int code) {
        String message;
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: message = "麦克风录音失败"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "没有麦克风权限"; break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "语音识别网络不可用"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "没有识别到清晰语音，请重试"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "没有听到说话"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "语音识别正在忙，请稍后重试"; break;
            default: message = "语音识别失败（" + code + "）";
        }
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
        super.handleOnDestroy();
    }
}
