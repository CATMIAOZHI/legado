package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBookCachedReadTest {
    @Test
    fun `cached read stops restoration before accessing WebDAV configuration`() {
        LocalBook.withoutRemoteRestore {
            assertFalse(LocalBook.downloadRemoteBook(Book()))
        }
    }

    @Test
    fun `failed nested read keeps outer read offline`() {
        LocalBook.withoutRemoteRestore {
            assertThrows(IllegalStateException::class.java) {
                LocalBook.withoutRemoteRestore { error("parser failure") }
            }
            assertFalse(LocalBook.downloadRemoteBook(Book()))
        }
    }
}
