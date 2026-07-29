---
name: Hardware Control (Termux)
description: Fallback to Termux API for Bluetooth and Infrared control when native tools are insufficient.
---

# Управление устройствами через Termux

Если нативных инструментов `manage_bluetooth` или `transmit_ir` недостаточно для выполнения задачи, вы можете использовать возможности `Termux` (с плагином Termux:API) для управления оборудованием Android-устройства, на котором запущена Катя.

## ИК-порт (Infrared)
Для передачи ИК-сигнала используйте `execute_shell_command` с вызовом `termux-infrared-transmit`.

Синтаксис: `termux-infrared-transmit -f <частота> -p <паттерн>`

Пример:
```bash
termux-infrared-transmit -f 38000 -p 9000,4500,560,560,560
```

Узнать поддерживаемые частоты можно с помощью `termux-infrared-frequencies`.

## Bluetooth
Для управления Bluetooth:

- **Сканирование устройств:**
```bash
termux-bluetooth-scan
```
Ответ будет в формате JSON.

- **Спряжение с устройством (Pairing):**
```bash
termux-bluetooth-pair <MAC-адрес>
```

- **Отключение:**
```bash
termux-bluetooth-unpair <MAC-адрес>
```

> **Важно:** Убедитесь, что у Кати есть доступ к `Termux:API`. Если команда возвращает ошибку, предложите пользователю установить `Termux:API` из F-Droid.
