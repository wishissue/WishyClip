package org.wishyclip.app.canvas

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wishyclip.app.model.LayerBlend

@RunWith(RobolectricTestRunner::class)
class LayerBlendingTest {

    @Test fun normalHasNoXfermodeEveryOtherModeHasOne() {
        assertNull(LayerBlending.xfermode(LayerBlend.NORMAL))
        for (b in LayerBlend.values()) if (b != LayerBlend.NORMAL) assertNotNull(b.name, LayerBlending.xfermode(b))
    }

    /** The UI thread and the export thread share this cache; concurrent first use must not corrupt it. */
    @Test fun concurrentLookupsReturnOneSharedInstancePerMode() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val seen = java.util.concurrent.ConcurrentHashMap<LayerBlend, android.graphics.PorterDuffXfermode>()
        repeat(8) {
            pool.execute {
                try {
                    start.await()
                    repeat(2000) {
                        for (b in LayerBlend.values()) {
                            val x = LayerBlending.xfermode(b) ?: continue
                            val first = seen.putIfAbsent(b, x)
                            if (first != null) assertSame(first, x)
                        }
                    }
                } catch (t: Throwable) { failure.set(t) }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)
        failure.get()?.let { throw it }
    }
}
