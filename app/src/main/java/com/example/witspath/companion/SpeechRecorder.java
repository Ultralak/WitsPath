package com.example.witspath.companion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;

/**
 * Records a short voice clip from the microphone as 16 kHz mono WAV, for the cloud transcription service.
 * Tap once to start, call {@link #stop()} (or let the time limit pass) to finish.
 */
public final class SpeechRecorder {

    public interface Listener {
        /** Called on the main thread with the finished WAV file. */
        void onRecorded(byte[] wav, long millis);

        /** Called on the main thread if the microphone could not be used. */
        void onError();
    }

    public static final int SAMPLE_RATE = 16000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean stopRequested;
    private Thread thread;

    public boolean isRecording() {
        return thread != null && thread.isAlive();
    }

    /** @return false if the microphone could not be opened (no permission, or in use) */
    public boolean start(Context context, long maxMillis, Listener listener) {
        if (isRecording()) return false;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) return false;

        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuffer, SAMPLE_RATE * 2));
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            return false;
        }

        stopRequested = false;
        final AudioRecord rec = record;
        thread = new Thread(() -> {
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            long startedAt = System.currentTimeMillis();
            boolean failed = false;
            try {
                rec.startRecording();
                while (!stopRequested && System.currentTimeMillis() - startedAt < maxMillis) {
                    int n = rec.read(buffer, 0, buffer.length);
                    if (n > 0) {
                        pcm.write(buffer, 0, n);
                    } else if (n < 0) {
                        failed = true;
                        break;
                    }
                }
            } catch (IllegalStateException e) {
                failed = true;
            } finally {
                try {
                    rec.stop();
                } catch (IllegalStateException ignored) {
                    // never started
                }
                rec.release();
            }
            final byte[] data = pcm.toByteArray();
            if (failed && data.length == 0) {
                main.post(listener::onError);
            } else {
                final byte[] wav = WavEncoder.toWav(data, SAMPLE_RATE);
                final long millis = WavEncoder.durationMillis(data.length, SAMPLE_RATE);
                main.post(() -> listener.onRecorded(wav, millis));
            }
        }, "speech-recorder");
        thread.start();
        return true;
    }

    /** Finish the clip; the result arrives through the listener. */
    public void stop() {
        stopRequested = true;
    }

    /** Stop and discard (the activity is going away). */
    public void release() {
        stopRequested = true;
    }
}
