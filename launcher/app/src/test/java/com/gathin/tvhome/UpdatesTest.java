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

    @Test
    public void onlyThisRepositorysReleaseFiles() {
        String base = "https://github.com/encikzul/layarkaca-tv/releases/download/";
        assertTrue(Updates.isReleaseFile(base + "v1.3/layarkaca-v1.3.apk"));
        assertTrue(Updates.isReleaseFile(base + "v1.3/layarkaca-v1.3.apk.sha256"));
        assertFalse(Updates.isReleaseFile("https://github.com/someone/layarkaca-tv/releases/download/v1.3/x.apk"));
        assertFalse(Updates.isReleaseFile("http://github.com/encikzul/layarkaca-tv/releases/download/v1.3/x.apk"));
        assertFalse(Updates.isReleaseFile(base + "v1.3/../../evil/x.apk"));
        assertFalse(Updates.isReleaseFile(base + "v1.3/sub/x.apk"));
        assertFalse(Updates.isReleaseFile(base + "latest/x.apk"));
        assertFalse(Updates.isReleaseFile(""));
        assertFalse(Updates.isReleaseFile(null));
    }

    @Test
    public void readsTheSha256File() {
        String hash = "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b";
        assertEquals(hash, Updates.shaOf(hash + "  layarkaca-v1.3.apk\n"));
        assertEquals(hash, Updates.shaOf(hash.toUpperCase()));
        assertEquals(hash, Updates.shaOf(hash + "\n"));
        assertNull(Updates.shaOf(hash.substring(1) + "  x.apk"));
        assertNull(Updates.shaOf(hash + "0  x.apk"));
        assertNull(Updates.shaOf("not a hash"));
        assertNull(Updates.shaOf(null));
    }
}
