package com.lifewise.app;

final class SpeechFallbackPolicy {
    private SpeechFallbackPolicy() {}

    static boolean shouldTryAlternative(int status, boolean hasTranscript) {
        if (status >= 200 && status < 300) return !hasTranscript;
        return isUnsupportedStatus(status);
    }

    static boolean isUnsupportedStatus(int status) {
        return status == 400 || status == 404 || status == 405 || status == 415
            || status == 422 || status == 501;
    }
}
