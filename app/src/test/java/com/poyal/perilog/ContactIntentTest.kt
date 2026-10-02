package com.poyal.perilog

import android.content.Intent
import com.poyal.perilog.ui.contactIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=android.app.Application::class)
class ContactIntentTest {
    @Test fun phoneAndSmsOpenComposersWithOnlyNormalizedRecipient() {
        val dial=contactIntent("032-000-0000",false)
        assertEquals(Intent.ACTION_DIAL,dial.action);assertEquals("tel:0320000000",dial.data.toString())
        val sms=contactIntent("+82 (10) 0000-0000",true)
        assertEquals(Intent.ACTION_SENDTO,sms.action)
        assertEquals("smsto",sms.data!!.scheme);assertEquals("+821000000000",sms.data!!.schemeSpecificPart)
        assertNull(sms.extras)
    }
}
