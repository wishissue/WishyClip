package org.wishyclip.app.brush

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test: BrushStore only uses java.io. */
class BrushStoreSafetyTest {
    private fun tempRoot(): File = File.createTempFile("brushes", "").let { it.delete(); it.mkdirs(); it }

    private fun parsed(name: String) = ParsedBrush(name, TipMask(2, 2, byteArrayOf(0, 10, 20, 30)))

    @Test fun addThenListRoundTripsAndLeavesNoTempFiles() {
        val root = tempRoot()
        val store = BrushStore(root)
        val added = store.add(parsed("Ink"))
        assertNotNull(added)
        assertEquals(listOf("Ink"), store.list().map { it.name })
        assertEquals(0, root.walkTopDown().count { it.name.endsWith(".tmp") })
    }

    @Test fun veryLongNamesAreTruncatedOnRead() {
        val store = BrushStore(tempRoot())
        store.add(parsed("n".repeat(1000)))
        assertTrue(store.list().all { it.name.length <= 64 })
    }

    @Test fun truncatedOrOversizedJsonIsSkippedNotFatal() {
        val root = tempRoot()
        val store = BrushStore(root)
        val good = store.add(parsed("Good"))!!
        val bad = File(root, "b1").apply { mkdirs() }
        File(bad, "brush.json").writeText("{\"name\":\"x\",\"tipWid")          // truncated by a crash
        val huge = File(root, "b2").apply { mkdirs() }
        File(huge, "brush.json").writeText("{\"name\":\"" + "a".repeat(10_000) + "\"}")
        assertEquals(listOf(good.id), store.list().map { it.id })
    }

    @Test fun updateReplacesAtomically() {
        val store = BrushStore(tempRoot())
        val b = store.add(parsed("Old"))!!
        store.update(b.copy(name = "New"))
        assertEquals("New", store.list().single().name)
    }
}
