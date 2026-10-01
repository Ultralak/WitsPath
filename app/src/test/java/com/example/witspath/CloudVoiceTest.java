package com.example.witspath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.witspath.companion.CompanionEndpoint;
import com.example.witspath.companion.CompanionLanguage;
import com.example.witspath.companion.WavEncoder;

import org.junit.Test;

public class CloudVoiceTest {

    private static int intAt(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
    }

    private static int shortAt(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8;
    }

    private static String text(byte[] b, int at, int len) {
        return new String(b, at, len, java.nio.charset.StandardCharsets.US_ASCII);
    }

    @Test
    public void wavHasAValidHeader() {
        byte[] pcm = new byte[32000]; // one second at 16 kHz mono 16-bit
        pcm[0] = 1;
        byte[] wav = WavEncoder.toWav(pcm, 16000);

        assertEquals(44 + 32000, wav.length);
        assertEquals("RIFF", text(wav, 0, 4));
        assertEquals(36 + 32000, intAt(wav, 4));
        assertEquals("WAVE", text(wav, 8, 4));
        assertEquals("fmt ", text(wav, 12, 4));
        assertEquals(16, intAt(wav, 16));
        assertEquals(1, shortAt(wav, 20));      // PCM
        assertEquals(1, shortAt(wav, 22));      // mono
        assertEquals(16000, intAt(wav, 24));
        assertEquals(32000, intAt(wav, 28));    // byte rate
        assertEquals(2, shortAt(wav, 32));
        assertEquals(16, shortAt(wav, 34));
        assertEquals("data", text(wav, 36, 4));
        assertEquals(32000, intAt(wav, 40));
        assertEquals(1, wav[44]);               // the audio follows the header unchanged
    }

    @Test
    public void durationIsMeasuredFromTheBytes() {
        assertEquals(1000, WavEncoder.durationMillis(32000, 16000));
        assertEquals(500, WavEncoder.durationMillis(16000, 16000));
        assertEquals(0, WavEncoder.durationMillis(0, 16000));
    }

    @Test
    public void speechAddressesComeFromTheCompanionAddress() {
        String endpoint = "https://name.trycloudflare.com/api/companion/message";
        assertEquals("https://name.trycloudflare.com/api/speech/transcribe?lang=zu",
                CompanionEndpoint.transcribeUrl(endpoint, "zu"));
        assertEquals("https://name.trycloudflare.com/api/companion/config", CompanionEndpoint.configUrl(endpoint));
        assertEquals("https://host.example:8443", CompanionEndpoint.baseUrl("https://host.example:8443/api/companion/message"));
    }

    @Test
    public void unusableAddressesGiveNoSpeechAddress() {
        assertNull(CompanionEndpoint.transcribeUrl("", "zu"));
        assertNull(CompanionEndpoint.transcribeUrl(null, "zu"));
        assertNull(CompanionEndpoint.transcribeUrl("http://192.168.0.5:5173/api/companion/message", "zu"));
        assertNull(CompanionEndpoint.configUrl("not a url"));
    }

    @Test
    public void languageCodeIsCheckedBeforeItGoesIntoTheAddress() {
        String endpoint = "https://name.trycloudflare.com/api/companion/message";
        assertNull(CompanionEndpoint.transcribeUrl(endpoint, "zu&x=1"));
        assertNull(CompanionEndpoint.transcribeUrl(endpoint, "../zu"));
        assertNull(CompanionEndpoint.transcribeUrl(endpoint, null));
        assertNull(CompanionEndpoint.transcribeUrl(endpoint, "ZULU1"));
    }

    @Test
    public void onlyIsiZuluAndSesothoUseTheCloudRecogniser() {
        assertTrue(CompanionLanguage.ZU.usesCloudVoice());
        assertTrue(CompanionLanguage.ST.usesCloudVoice());
        assertFalse(CompanionLanguage.EN.usesCloudVoice());
        assertFalse(CompanionLanguage.AF.usesCloudVoice());
        assertFalse(CompanionLanguage.XH.usesCloudVoice());
    }
}
