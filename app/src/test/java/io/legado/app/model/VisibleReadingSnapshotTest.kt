package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleReadingSnapshotTest {
    @Test fun visiblePageIncludesTextAfterPageStart() {
        assertEquals(100 until 300, visibleBodyRange(120, 320, 20, 1000))
    }
    @Test fun titleAndFinalPageAreClampedToBody() {
        assertEquals(0 until 30, visibleBodyRange(5, 50, 20, 30))
        assertEquals(0 until 0, visibleBodyRange(0, 10, 20, 100))
    }
    @Test fun unprovenOrReversedPositionsAreRejected() {
        assertNull(visibleBodyRange(100, 200, -1, 1000))
        assertNull(visibleBodyRange(200, 100, 10, 1000))
    }
}
