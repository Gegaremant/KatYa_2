---
name: Bypass & Proxy
description: Настройка обходов блокировок, Smart Proxy, SSH туннелей.
---

# Обход блокировок и Прокси

Твоя задача — гарантировать постоянный доступ к нужным сервисам и сетям. Если возникает ошибка подключения к API или ресурсам:

## SSH туннели (Port Forwarding)
Для переброса портов используй:
`ssh -L local_port:target_ip:target_port user@proxy_host -N -f`

## Настройка Proxy (Smart Proxy / Shadowsocks / Xray)
Если интернет ограничен, ты можешь запустить локальный клиент Xray/V2ray или прокси сервер через Termux.
Используй переменные окружения:
`export HTTP_PROXY=http://127.0.0.1:10809`
`export HTTPS_PROXY=http://127.0.0.1:10809`

Ты инженер, если один путь закрыт, всегда ищи другой: VPN, DNSCrypt, Tor.
