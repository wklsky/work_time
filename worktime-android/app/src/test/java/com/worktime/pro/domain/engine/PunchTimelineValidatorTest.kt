/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/engine/PunchTimelineValidatorTest.kt
 * @Description: 多段打卡时间线校验测试，覆盖上班/加班不可重叠与同类段必须闭合
 */
package com.worktime.pro.domain.engine

import com.worktime.pro.domain.model.PunchType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PunchTimelineValidatorTest {

    private fun at(hour: Int): Long = hour * 60L * 60_000L

    private fun moment(type: PunchType, hour: Int) = PunchMoment(type, at(hour))

    @Test
    fun `multiple work and overtime segments are valid`() {
        val result = PunchTimelineValidator.validate(
            listOf(
                moment(PunchType.WORK_START, 9),
                moment(PunchType.WORK_END, 12),
                moment(PunchType.WORK_START, 13),
                moment(PunchType.WORK_END, 18),
                moment(PunchType.OT_START, 19),
                moment(PunchType.OT_END, 21),
                moment(PunchType.OT_START, 22),
                moment(PunchType.OT_END, 23)
            )
        )
        assertNull(result)
    }

    @Test
    fun `work start while work is open is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.WORK_START, 9),
                    moment(PunchType.WORK_START, 10)
                )
            )
        )
    }

    @Test
    fun `overtime start while work is open is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.WORK_START, 9),
                    moment(PunchType.OT_START, 10)
                )
            )
        )
    }

    @Test
    fun `work start while overtime is open is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.OT_START, 19),
                    moment(PunchType.WORK_START, 20)
                )
            )
        )
    }

    @Test
    fun `overtime start while overtime is open is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.OT_START, 19),
                    moment(PunchType.OT_START, 20)
                )
            )
        )
    }

    @Test
    fun `lone ends without any start are ignored`() {
        assertNull(
            PunchTimelineValidator.validate(
                listOf(moment(PunchType.WORK_END, 18))
            )
        )
        assertNull(
            PunchTimelineValidator.validate(
                listOf(moment(PunchType.OT_END, 21))
            )
        )
    }

    @Test
    fun `end before its start is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.WORK_END, 8),
                    moment(PunchType.WORK_START, 9)
                )
            )
        )
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.OT_END, 18),
                    moment(PunchType.OT_START, 19)
                )
            )
        )
    }

    @Test
    fun `extra end after a closed segment is rejected`() {
        assertNotNull(
            PunchTimelineValidator.validate(
                listOf(
                    moment(PunchType.WORK_START, 9),
                    moment(PunchType.WORK_END, 12),
                    moment(PunchType.WORK_END, 13)
                )
            )
        )
    }

    @Test
    fun `end and start at the same time are allowed`() {
        val result = PunchTimelineValidator.validate(
            listOf(
                moment(PunchType.WORK_START, 9),
                moment(PunchType.WORK_END, 12),
                moment(PunchType.OT_START, 12),
                moment(PunchType.OT_END, 14)
            )
        )
        assertNull(result)
    }

    @Test
    fun `a single open segment at the end is allowed`() {
        assertNull(
            PunchTimelineValidator.validate(
                listOf(moment(PunchType.WORK_START, 9))
            )
        )
    }
}
