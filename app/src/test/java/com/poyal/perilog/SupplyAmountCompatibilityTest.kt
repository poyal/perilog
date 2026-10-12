package com.poyal.perilog

import com.poyal.perilog.data.SupplyAmount
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = android.app.Application::class)
class SupplyAmountCompatibilityTest {
    @Test fun reductionKeepsExactFractionsAndLongBoundaries() {
        assertEquals(SupplyAmount(5, 6), SupplyAmount(1, 2) + SupplyAmount(1, 3))
        assertEquals(SupplyAmount(1), SupplyAmount(Long.MAX_VALUE, Long.MAX_VALUE) * 2 - SupplyAmount(1))
        assertEquals(SupplyAmount(Long.MAX_VALUE), SupplyAmount(Long.MAX_VALUE) + SupplyAmount())
        assertEquals(SupplyAmount(Long.MIN_VALUE), SupplyAmount(Long.MIN_VALUE) + SupplyAmount())
        assertEquals(SupplyAmount(), SupplyAmount(Long.MIN_VALUE) * 0)
    }

    @Test fun overflowFailsInsteadOfTruncatingInventoryQuantities() {
        assertThrows(ArithmeticException::class.java) { SupplyAmount(Long.MAX_VALUE) + SupplyAmount(1) }
        assertThrows(ArithmeticException::class.java) { SupplyAmount(Long.MIN_VALUE) + SupplyAmount(-1) }
        assertThrows(ArithmeticException::class.java) { SupplyAmount(1, Long.MAX_VALUE) + SupplyAmount(1, 2) }
    }
}
