package com.frontr.app.feature.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsHandleTest {

    @Test
    fun `a typed sub name is read as is, lowercased`() {
        assertEquals("android", AccountsViewModel.asHandle("Android "))
        assertEquals("android", AccountsViewModel.asHandle("r/Android"))
        assertEquals("android", AccountsViewModel.asHandle("/r/android/"))
    }

    @Test
    fun `a name Reddit would refuse is not a sub`() {
        assertNull(AccountsViewModel.asHandle("a"))
        assertNull(AccountsViewModel.asHandle("two words"))
        assertNull(AccountsViewModel.asHandle("_hidden"))
    }

    @Test
    fun `a pasted sub or post link gives the sub`() {
        assertEquals("android", AccountsViewModel.asHandle("https://www.reddit.com/r/Android/"))
        assertEquals("android", AccountsViewModel.asHandle("old.reddit.com/r/Android/comments/abc123/a_title/"))
    }

    @Test
    fun `a link to something else is not a sub`() {
        assertNull(AccountsViewModel.asHandle("https://www.reddit.com/user/someone"))
        assertNull(AccountsViewModel.asHandle("https://example.com/r/android"))
    }
}
