package com.example.witspath.companion;

/** Wraps raw 16-bit little-endian mono PCM in a WAV header, the format the transcription service accepts. */
public final class WavEncoder {
    private static final int HEADER_BYTES = 44;

    private WavEncoder() {}

    public static byte[] toWav(byte[] pcm, int sampleRate) {
        int dataLength = pcm.length;
        byte[] out = new byte[HEADER_BYTES + dataLength];
        int byteRate = sampleRate * 2; // mono, 16-bit

        put(out, 0, "RIFF");
        putInt(out, 4, 36 + dataLength);
        put(out, 8, "WAVE");
        put(out, 12, "fmt ");
        putInt(out, 16, 16);            // size of the fmt chunk
        putShort(out, 20, 1);           // PCM
        putShort(out, 22, 1);           // mono
        putInt(out, 24, sampleRate);
        putInt(out, 28, byteRate);
        putShort(out, 32, 2);           // block align
        putShort(out, 34, 16);          // bits per sample
        put(out, 36, "data");
        putInt(out, 40, dataLength);
        System.arraycopy(pcm, 0, out, HEADER_BYTES, dataLength);
        return out;
    }

    /** Length of the recording in milliseconds. */
    public static long durationMillis(int pcmBytes, int sampleRate) {
        return pcmBytes * 1000L / (sampleRate * 2L);
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }
}
