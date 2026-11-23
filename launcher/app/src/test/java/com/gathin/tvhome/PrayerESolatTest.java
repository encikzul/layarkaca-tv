package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Reading JAKIM e-solat's dates and clock times, the fallback when waktusolat.app is down. */
public class PrayerESolatTest {

    @Test
    public void readsMalayAndEnglishMonths() {
        String[] malay = {"Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ogos", "Sep", "Okt", "Nov", "Dis"};
        for (int i = 0; i < 12; i++) {
            assertArrayEquals(malay[i], new int[]{2026, i + 1, 1}, Prayer.esolatDate("01-" + malay[i] + "-2026"));
        }
        assertArrayEquals(new int[]{2026, 3, 15}, Prayer.esolatDate("15-Mar-2026"));
        assertArrayEquals(new int[]{2026, 8, 31}, Prayer.esolatDate("31-Aug-2026"));
        assertArrayEquals(new int[]{2026, 12, 31}, Prayer.esolatDate("31-Dec-2026"));
    }

    @Test
    public void rejectsWhatIsNotADate() {
        assertNull(Prayer.esolatDate("2026-01-01"));
        assertNull(Prayer.esolatDate("01-Xyz-2026"));
        assertNull(Prayer.esolatDate("00-Jan-2026"));
        assertNull(Prayer.esolatDate("32-Jan-2026"));
        assertNull(Prayer.esolatDate(""));
        assertNull(Prayer.esolatDate(null));
    }

    @Test
    public void clockTimesAreMalaysianTime() {
        // 1 Jan 2026, 06:06 in Malaysia (GMT+8) is 31 Dec 2025, 22:06 UTC.
        assertEquals(1767218760000L, Prayer.esolatMillis("01-Jan-2026", "06:06:00"));
        // Matches waktusolat.app's own timestamp for Subuh in SGR01 on 1 December 2026 (05:52).
        assertEquals(1796075520000L, Prayer.esolatMillis("01-Dis-2026", "05:52:00"));
        assertEquals(1796075520000L, Prayer.esolatMillis("01-Dis-2026", "05:52"));
    }

    @Test
    public void unreadableTimesAreMinusOne() {
        assertEquals(-1, Prayer.esolatMillis("01-Jan-2026", ""));
        assertEquals(-1, Prayer.esolatMillis("01-Jan-2026", "25:00:00"));
        assertEquals(-1, Prayer.esolatMillis("01-Jan-2026", "06:06:00 PM"));
        assertEquals(-1, Prayer.esolatMillis("bad", "06:06:00"));
    }
}
