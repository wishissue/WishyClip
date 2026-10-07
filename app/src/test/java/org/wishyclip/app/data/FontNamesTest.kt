package org.wishyclip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FontNamesTest {

    /** A minimal sfnt container holding just a Windows-platform `name` table with IDs 1 and 2. */
    private fun fakeFont(family: String, style: String): ByteArray {
        val f = family.toByteArray(Charsets.UTF_16BE)
        val s = style.toByteArray(Charsets.UTF_16BE)
        val name = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                writeShort(0); writeShort(2); writeShort(6 + 2 * 12)
                writeShort(3); writeShort(1); writeShort(0x409); writeShort(1); writeShort(f.size); writeShort(0)
                writeShort(3); writeShort(1); writeShort(0x409); writeShort(2); writeShort(s.size); writeShort(f.size)
                write(f); write(s)
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                writeInt(0x00010000); writeShort(1); writeShort(16); writeShort(0); writeShort(0)
                writeInt(0x6E616D65); writeInt(0); writeInt(28); writeInt(name.size)
                write(name)
            }
        }.toByteArray()
    }

    @Test fun regularStyleIsOmitted() {
        assertEquals("Pacifico", FontNames.displayName(fakeFont("Pacifico", "Regular")))
    }

    @Test fun nonRegularStyleIsAppended() {
        assertEquals("Roboto Bold", FontNames.displayName(fakeFont("Roboto", "Bold")))
    }

    @Test fun garbageIsNotAFont() {
        val junk = "this is definitely not a font file".toByteArray()
        assertFalse(FontNames.isFontFile(junk))
        assertNull(FontNames.displayName(junk))
    }

    @Test fun truncatedFontDoesNotCrash() {
        assertNull(FontNames.displayName(fakeFont("Roboto", "Bold").copyOf(40)))
    }

    @Test fun recognisesFontContainers() {
        assertTrue(FontNames.isFontFile(fakeFont("A", "Regular")))
        assertEquals("ttf", FontNames.extensionFor(fakeFont("A", "Regular")))
    }

    @Test fun fileNamesBecomeReadableLabels() {
        assertEquals("My Font Bold", FontLibrary.prettifyFileName("My_Font-Bold.ttf"))
        assertNull(FontLibrary.prettifyFileName("   "))
        assertEquals("MyFont-Bold", FontLibrary.sanitizeStem("My Font/../-Bold.ttf"))
    }
}
