package com.lifewise.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeSpeechPlugin.class);
        registerPlugin(NativeAiStreamPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
