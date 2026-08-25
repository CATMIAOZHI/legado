package io.legado.app.api.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadableContentBoundaryTest {

    @Test
    fun `layout title is removed before clamping to body`() {
        assertEquals(80, resolveReadableBodyEnd(100, 20, 500))
    }

    @Test
    fun `position is clamped to available body`() {
        assertEquals(50, resolveReadableBodyEnd(100, 20, 50))
        assertEquals(0, resolveReadableBodyEnd(10, 20, 50))
    }

    @Test
    fun `unknown title length fails closed`() {
        assertNull(resolveReadableBodyEnd(100, -1, 500))
    }
}
