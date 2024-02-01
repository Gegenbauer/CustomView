package me.gegenbauer.customview.entrance

interface DemoCollector {
    suspend fun collect(): List<Demo>
}