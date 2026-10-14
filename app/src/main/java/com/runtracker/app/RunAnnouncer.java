package com.runtracker.app;

import android.content.Context;
import android.media.*;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public final class RunAnnouncer {
    private TextToSpeech speech;
    private boolean ready, closed;
    private ToneGenerator tone;

    public RunAnnouncer(Context context) {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
        } catch (RuntimeException ignored) {
        }
        try {
            speech =
                    new TextToSpeech(
                            context,
                            status -> {
                                if (!closed && status == TextToSpeech.SUCCESS) {
                                    try {
                                        int language = speech.setLanguage(Locale.ENGLISH);
                                        ready =
                                                language != TextToSpeech.LANG_MISSING_DATA
                                                        && language
                                                                != TextToSpeech.LANG_NOT_SUPPORTED;
                                        speech.setAudioAttributes(
                                                new AudioAttributes.Builder()
                                                        .setUsage(
                                                                AudioAttributes
                                                                        .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                                        .setContentType(
                                                                AudioAttributes.CONTENT_TYPE_SPEECH)
                                                        .build());
                                    } catch (RuntimeException error) {
                                        ready = false;
                                    }
                                }
                            });
        } catch (RuntimeException error) {
            ready = false;
        }
    }

    public void say(String message) {
        if (closed) return;
        try {
            if (ready
                    && speech.speak(
                                    message,
                                    TextToSpeech.QUEUE_ADD,
                                    null,
                                    "run-" + System.nanoTime())
                            != TextToSpeech.ERROR) return;
        } catch (RuntimeException ignored) {
        }
        try {
            if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
        } catch (RuntimeException ignored) {
        }
    }

    public void stop() {
        try {
            if (speech != null) speech.stop();
        } catch (RuntimeException ignored) {
        }
        try {
            if (tone != null) tone.stopTone();
        } catch (RuntimeException ignored) {
        }
    }

    public void close() {
        closed = true;
        stop();
        try {
            if (speech != null) speech.shutdown();
        } catch (RuntimeException ignored) {
        }
        try {
            if (tone != null) tone.release();
        } catch (RuntimeException ignored) {
        }
    }
}
