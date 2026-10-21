package dev.renova.app

import dev.renova.app.ui.limitMinuteOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitPickerTest {
    @Test fun savedOffStepMinuteGetsItsOwnItem() {
        assertEquals((0..55 step 5).toList(), limitMinuteOptions(null))
        assertEquals((0..55 step 5).toList(), limitMinuteOptions(15))
        assertEquals(listOf(0, 3, 5, 10), limitMinuteOptions(3).take(4))
        assertEquals(13, limitMinuteOptions(17).size)
    }
}
