package com.example.model

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem varsayılanı"),
    LIGHT("Açık"),
    DARK("Koyu")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val downloadOnlyOnWifi: Boolean = true,
    val autoDeletePlayed: Boolean = true,
    val defaultSpeed: Float = 1.0f
)
