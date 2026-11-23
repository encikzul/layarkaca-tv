package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Deciding whether GitHub's latest release is newer than the app on the TV. */
public class UpdatesTest {

    @Test
    public void readsReleaseTags() {
        assertEquals("1.2", Updates.clean("v1.2"));
        assertEquals("1.2", Updates.clean("1.2"));
        assertEquals("1.10.3", Updates.clean(" v1.10.3 "));
        assertNull(Updates.clean("v1.2-beta"));
        assertNull(Updates.clean("latest"));
        assertNull(Updates.clean(""));
        assertNull(Updates.clean(null));
    }

    @Test
    public void comparesNumbersNotText() {
        assertTrue(Updates.isNewer("v1.2", "1.1"));
        assertTrue(Updates.isNewer("v1.10", "1.9"));
        assertTrue(Updates.isNewer("v2.0", "1.99"));
        assertTrue(Updates.isNewer("v1.1.1", "1.1"));
        assertFalse(Updates.isNewer("v1.1", "1.1"));
        assertFalse(Updates.isNewer("v1.1", "1.1.0"));
        assertFalse(Updates.isNewer("v1.0", "1.1"));
    }

    @Test
    public void unknownVersionsShowNothing() {
        assertFalse(Updates.isNewer(null, "1.1"));
        assertFalse(Updates.isNewer("v1.2", ""));
        assertFalse(Updates.isNewer("nightly", "1.1"));
        assertFalse(Updates.isNewer("v1.2", "1.1-debug"));
    }
}
