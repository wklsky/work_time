/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/PunchPairing.kt
 * @Description: 打卡配对工具，按时间顺序为每个开始事件匹配其后最近的未用结束事件
 */
package com.worktime.pro.domain.engine

object PunchPairing {

    /**
     * 配对开始/结束事件。
     *
     * 旧实现按数组下标一一对应，遇到重复打卡或乱序时会错配、漏配。
     * 这里改为贪心：开始事件按时间升序，各自匹配其后时间最近且尚未使用的结束事件；
     * 找不到合法结束的开始事件会被跳过，多余的结束事件自然忽略。
     */
    fun <T> pair(starts: List<T>, ends: List<T>, time: (T) -> Long): List<Pair<T, T>> {
        val sortedStarts = starts.sortedBy(time)
        val sortedEnds = ends.sortedBy(time)
        val used = BooleanArray(sortedEnds.size)
        val result = mutableListOf<Pair<T, T>>()

        sortedStarts.forEach { start ->
            var bestIndex = -1
            for (index in sortedEnds.indices) {
                if (used[index]) continue
                if (time(sortedEnds[index]) <= time(start)) continue
                if (bestIndex == -1 || time(sortedEnds[index]) < time(sortedEnds[bestIndex])) {
                    bestIndex = index
                }
            }
            if (bestIndex >= 0) {
                used[bestIndex] = true
                result += start to sortedEnds[bestIndex]
            }
        }
        return result
    }
}
