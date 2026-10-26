package com.example.model

enum class PlaybackSpeed(val speed: Float, val label: String) {
    SPEED_0_5(0.5f, "0.5x"),
    SPEED_0_8(0.8f, "0.8x"),
    SPEED_1_0(1.0f, "1.0x"),
    SPEED_1_2(1.2f, "1.2x"),
    SPEED_1_5(1.5f, "1.5x"),
    SPEED_2_0(2.0f, "2.0x"),
    SPEED_3_0(3.0f, "3.0x (Plus)")
}

enum class SleepTimerDuration(val minutes: Int, val label: String) {
    OFF(0, "Kapalı"),
    MIN_15(15, "15 Dakika"),
    MIN_30(30, "30 Dakika"),
    MIN_45(45, "45 Dakika"),
    MIN_60(60, "1 Saat"),
    END_OF_EPISODE(-1, "Bölüm Bittiğinde")
}
