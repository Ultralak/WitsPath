package com.example.witspath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.witspath.companion.CompanionEndpoint;

import org.junit.Test;

public class CompanionEndpointTest {

    @Test
    public void bareAddressGetsTheApiPath() {
        assertEquals("https://abc-def.trycloudflare.com/api/companion/message",
                CompanionEndpoint.normalise("https://abc-def.trycloudflare.com"));
        assertEquals("https://abc-def.trycloudflare.com/api/companion/message",
                CompanionEndpoint.normalise("  https://abc-def.trycloudflare.com/  "));
    }

    @Test
    public void aFullAddressIsKept() {
        String full = "https://abc.trycloudflare.com/api/companion/message";
        assertEquals(full, CompanionEndpoint.normalise(full));
    }

    @Test
    public void onlyHttpsIsAccepted() {
        assertNull(CompanionEndpoint.normalise("http://192.168.0.5:5173"));
        assertNull(CompanionEndpoint.normalise("ftp://example.com"));
        assertNull(CompanionEndpoint.normalise("abc.trycloudflare.com"));
    }

    @Test
    public void junkIsRejected() {
        assertNull(CompanionEndpoint.normalise(null));
        assertNull(CompanionEndpoint.normalise(""));
        assertNull(CompanionEndpoint.normalise("   "));
        assertNull(CompanionEndpoint.normalise("https://"));
        assertNull(CompanionEndpoint.normalise("https://user:pass@example.com"));
        assertNull(CompanionEndpoint.normalise("https://example.com/?key=secret"));
        assertNull(CompanionEndpoint.normalise("not a url"));
    }
}
