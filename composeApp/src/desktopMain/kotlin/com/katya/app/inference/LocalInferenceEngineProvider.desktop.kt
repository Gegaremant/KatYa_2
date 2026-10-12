package com.katya.app.inference

import com.katya.app.tools.AppLogger

/**
 * Локальный движок на десктопе не подключён.
 *
 * На Android это LiteRT-LM, и библиотека под Android-ABI. Для JVM есть
 * `litertlm-jvm`, но модель, скачанная на телефон в формате `.task`/`.litertlm`,
 * на компьютере не откроется, а переучивать её нечем: пользователь качает
 * модели на телефон и переносит их туда же.
 *
 * Возвращаем null, и UI честно показывает «модель не настроена» вместо того,
 * чтобы перечислять устройства, которых тут нет.
 */
actual fun createLocalInferenceEngine(): LocalInferenceEngine? {
    AppLogger.d("Локальная модель", "на десктопе не поддерживается — работают облачные сервисы")
    return null
}
