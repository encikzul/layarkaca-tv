package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The home screen layouts (Tetapan › Paparan), MET forecasts as icons, and the football tags. */
public class DisplaysTest {

    @Test
    public void unknownLayoutFallsBackToKlasik() {
        assertEquals("bola", Display.valid("bola"));
        assertEquals("klasik", Display.valid("something-old"));
        assertEquals("klasik", Display.valid(null));
        assertEquals("Fokus Solat", Display.name("fokus"));
        assertTrue(Display.ownPanel("maklumat"));
        assertFalse(Display.ownPanel("minimal"));
        assertFalse(Display.ownPanel("klasik"));
    }

    @Test
    public void forecastKinds() {
        assertEquals(2, Weather.kind("Ribut petir di beberapa tempat"));
        assertEquals(0, Weather.kind("Tiada hujan"));
        assertEquals(0, Weather.kind("Tiada Hujan"));
        assertEquals(1, Weather.kind("Hujan di kebanyakan tempat"));
        assertEquals(3, Weather.kind("Berjerebu"));
        assertEquals(3, Weather.kind(null));
    }

    @Test
    public void competitionTags() {
        assertEquals("LSM", AltPanel.code("Liga Super"));
        assertEquals("UCL", AltPanel.code("Liga Juara-Juara"));
        assertEquals("AFF", AltPanel.code("AFF Ch."));
        assertEquals("FRIE", AltPanel.code("Friendlies"));
    }
}
