package com.lifewise.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeechFallbackPolicyTest {
    @Test
    public void retriesWhenEndpointDoesNotSupportTranscription() {
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(400, false));
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(404, false));
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(415, false));
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(422, false));
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(501, false));
    }

    @Test
    public void retriesWhenSuccessfulResponseContainsNoTranscript() {
        assertTrue(SpeechFallbackPolicy.shouldTryAlternative(200, false));
        assertFalse(SpeechFallbackPolicy.shouldTryAlternative(200, true));
    }

    @Test
    public void doesNotHideAuthenticationOrServerFailuresBehindFallbacks() {
        assertFalse(SpeechFallbackPolicy.shouldTryAlternative(401, false));
        assertFalse(SpeechFallbackPolicy.shouldTryAlternative(403, false));
        assertFalse(SpeechFallbackPolicy.shouldTryAlternative(500, false));
    }
}
