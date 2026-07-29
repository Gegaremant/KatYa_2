package com.katya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.tools.BatteryOptimizationPermissionController
import com.katya.app.tools.ExactAlarmPermissionController
import com.katya.app.tools.NotificationPermissionController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StartupPermissionFlow(
    onComplete: () -> Unit
) {
    if (currentPlatform !is Platform.Mobile.Android) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val notificationController = koinInject<NotificationPermissionController>()
    val exactAlarmController = koinInject<ExactAlarmPermissionController>()
    val batteryController = koinInject<BatteryOptimizationPermissionController>()
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentStep) {
        when (currentStep) {
            0 -> {
                if (notificationController.hasPermission()) {
                    currentStep++
                } else {
                    showDialog = true
                }
            }
            1 -> {
                if (exactAlarmController.hasPermission()) {
                    currentStep++
                } else {
                    showDialog = true
                }
            }
            2 -> {
                if (batteryController.hasPermission()) {
                    currentStep++
                } else {
                    showDialog = true
                }
            }
            else -> {
                onComplete()
            }
        }
    }

    if (showDialog) {
        val title = when (currentStep) {
            0 -> "Уведомления от Кати"
            1 -> "Точные будильники"
            2 -> "Фоновая работа"
            else -> ""
        }
        val message = when (currentStep) {
            0 -> "Мне нужно разрешение на отправку уведомлений, чтобы я могла сообщать тебе о результатах фоновых задач и напоминаниях."
            1 -> "Для того чтобы я могла просыпаться ровно в срок (с точностью до минуты), мне нужно специальное разрешение на точные будильники."
            2 -> "Android очень не любит, когда приложения работают в фоне, и часто усыпляет их. Чтобы я не отключалась, пожалуйста, разреши мне игнорировать оптимизацию батареи."
            else -> ""
        }

        AlertDialog(
            onDismissRequest = {
                // Force user to make a choice or skip
            },
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            showDialog = false
                            when (currentStep) {
                                0 -> notificationController.requestPermission()
                                1 -> exactAlarmController.requestPermission()
                                2 -> batteryController.requestPermission()
                            }
                            currentStep++
                        }
                    }
                ) {
                    Text("Разрешить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        currentStep++
                    }
                ) {
                    Text("Позже")
                }
            }
        )
    }
}
