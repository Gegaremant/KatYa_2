package com.katya.app.data

import kotlinx.serialization.Serializable

@Serializable
data class QuickAction(
    val id: String,
    val text: String,
    val prompt: String,
)
