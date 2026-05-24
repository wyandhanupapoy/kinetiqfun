package org.example.kinetiqfun

data class GameItem(
    val id: Int,
    val title: String,
    val screenshotResId: Int? = null // For future use if we have actual screenshots
)
