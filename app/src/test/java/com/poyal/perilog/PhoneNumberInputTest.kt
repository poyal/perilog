package com.poyal.perilog

import com.poyal.perilog.domain.dialNumber
import com.poyal.perilog.domain.validPhone
import com.poyal.perilog.ui.formattedPhone
import com.poyal.perilog.ui.phoneInputDigits
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=android.app.Application::class)
class PhoneNumberInputTest {
    @Test fun familiarNumbersHaveOneConsistentDisplayRegardlessOfPastedSeparators() {
        listOf(
            "15771111" to "1577-1111", "1577 1111" to "1577-1111", "1577-1111" to "1577-1111",
            " 010 (1234) 5678 " to "010-1234-5678", "01012345678" to "010-1234-5678",
            "021234567" to "02-123-4567", "0212345678" to "02-1234-5678",
            "0321234567" to "032-123-4567", "03112345678" to "031-1234-5678",
            "07012345678" to "070-1234-5678", "050712345678" to "0507-1234-5678",
            "114" to "114", "112" to "112", "119" to "119"
        ).forEach { (input,expected) ->
            assertEquals(input,expected,formattedPhone(input))
            assertTrue(input,validPhone(formattedPhone(input)))
            assertEquals(input,dialNumber(input),dialNumber(formattedPhone(input)))
        }
    }
    @Test fun partialAndInternationalNumbersNeverLoseOrInventDigits() {
        listOf("01012345678","0212345678","15771111","050712345678","+821012345678","+12025550123",
            "12345678901234567890").forEach { complete ->
            for(length in 0..complete.length) {
                val input=complete.take(length)
                val formatted=formattedPhone(input)
                assertEquals(input,input,phoneInputDigits(formatted))
                assertFalse(input,formatted.endsWith('-'))
                assertEquals(input,formatted,formattedPhone(formatted))
            }
        }
    }
    @Test fun unsupportedTextIsNotSilentlyConvertedToAValidRecipient() {
        listOf("전화 114","010abc12345678","++821012345678","12").forEach {
            assertFalse(it,validPhone(formattedPhone(it)))
        }
        assertEquals("010abc12345678",formattedPhone("010abc12345678"))
        assertEquals("",phoneInputDigits(" - ( ) "))
    }
}
