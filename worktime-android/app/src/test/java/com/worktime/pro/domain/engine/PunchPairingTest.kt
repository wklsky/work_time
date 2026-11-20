/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/engine/PunchPairingTest.kt
 * @Description: 打卡配对测试，覆盖乱序、重复、缺失与非法区间
 */
package com.worktime.pro.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchPairingTest {

    @Test
    fun `pairs each start with the nearest following end`() {
        val pairs = PunchPairing.pair(listOf(9L, 13L), listOf(12L, 18L)) { it }
        assertEquals(listOf(9L to 12L, 13L to 18L), pairs)
    }

    @Test
    fun `sorts unordered input before pairing`() {
        val pairs = PunchPairing.pair(listOf(13L, 9L), listOf(18L, 12L)) { it }
        assertEquals(listOf(9L to 12L, 13L to 18L), pairs)
    }

    @Test
    fun `duplicate starts use distinct ends`() {
        val pairs = PunchPairing.pair(listOf(9L, 9L), listOf(12L, 18L)) { it }
        assertEquals(2, pairs.size)
        assertEquals(listOf(12L, 18L), pairs.map { it.second })
    }

    @Test
    fun `start without a following end is skipped`() {
        val pairs = PunchPairing.pair(listOf(9L, 19L), listOf(18L)) { it }
        assertEquals(listOf(9L to 18L), pairs)
    }

    @Test
    fun `end not after start is ignored`() {
        val pairs = PunchPairing.pair(listOf(9L), listOf(8L)) { it }
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `extra ends are ignored`() {
        val pairs = PunchPairing.pair(listOf(9L), listOf(12L, 18L)) { it }
        assertEquals(listOf(9L to 12L), pairs)
    }
}
