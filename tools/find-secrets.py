#!/usr/bin/env python3
"""Ищет в дереве проекта токены, логины, пароли и IP-адреса.

Скрипт ничего не печатает из найденного целиком: показывается только место и
тип находки, а значение обрезается. Задача — понять, где лежит секрет, а не
разнести его по журналу.

Запуск:  python3 tools/find-secrets.py [путь]
"""

import os
import re
import sys

SKIP_DIRS = {
    ".git", ".gradle", ".idea", "build", "node_modules", "trash", "out",
    ".tmp", "captures", ".kotlin",
}

# Подозрительные ключи в исходниках и конфигах.
SECRET_PATTERNS = [
    ("private key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("github token", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}")),
    ("github fine-grained token", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}")),
    ("openai-style key", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}")),
    ("aws access key id", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("slack token", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}")),
    ("telegram token", re.compile(r"\b\d{8,10}:[A-Za-z0-9_-]{30,}\b")),
    ("jwt", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")),
    ("vless uri with credentials", re.compile(r"\bvless://[^\s\"']{8,}@[^\s\"']+")),
]

# Ключи вида password = "…", api_key = "…" и подобные.
ASSIGNMENT_PATTERN = re.compile(
    r"(?i)\b(password|passwd|pwd|api[_-]?key|apikey|secret|token|"
    r"server_password|encryption_key|private_key)\b\s*[:=]\s*"
    r"[\"']([^\"'\s]{4,})[\"']"
)

# Логины и адреса: host = "…", user = "…", login = "…".
LOGIN_PATTERN = re.compile(
    r"(?i)\b(user|username|login|host|hostname|server_ip|ip_address|address)\b\s*[:=]\s*"
    r"[\"']([^\"'\s]{2,})[\"']"
)

IP_PATTERN = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")

# Значения, которые не являются секретом.
ALLOWED_VALUES = {
    "", "null", "none", "true", "false", "0", "1", "127.0.0.1", "0.0.0.0",
    "localhost", "255.255.255.255", "example.com", "your_api_key", "changeme",
    "placeholder", "test", "dummy",
}

SKIP_FILES = {".env.katya", ".env", "secret.txt"}


def looks_like_placeholder(value: str) -> bool:
    lowered = value.strip().lower()
    if lowered in ALLOWED_VALUES:
        return True
    if any(marker in lowered for marker in ("your_", "<", ">", "xxx", "example", "placeholder")):
        return True
    return False


def is_local_ip(value: str) -> bool:
    return value.startswith(("127.", "0.0.0.", "10.", "192.168.", "172.16.", "172.17.",
                             "172.18.", "172.19.", "172.2", "172.30.", "172.31."))


def iter_files(root: str):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            if name in SKIP_FILES:
                continue
            path = os.path.join(dirpath, name)
            if os.path.islink(path):
                continue
            yield path


def scan(root: str):
    findings = []
    for path in iter_files(root):
        try:
            with open(path, "r", encoding="utf-8") as handle:
                lines = handle.readlines()
        except (UnicodeDecodeError, OSError):
            continue
        for number, line in enumerate(lines, start=1):
            for kind, pattern in SECRET_PATTERNS:
                if pattern.search(line):
                    findings.append((path, number, kind, "скрыто"))
            for match in ASSIGNMENT_PATTERN.finditer(line):
                value = match.group(2)
                if looks_like_placeholder(value):
                    continue
                findings.append((path, number, f"присвоение {match.group(1).lower()}", mask(value)))
            for match in LOGIN_PATTERN.finditer(line):
                value = match.group(2)
                if looks_like_placeholder(value):
                    continue
                findings.append((path, number, f"логин/хост {match.group(1).lower()}", mask(value)))
            for match in IP_PATTERN.finditer(line):
                value = match.group(0)
                if is_local_ip(value) or looks_like_placeholder(value):
                    continue
                findings.append((path, number, "ip-адрес", value))
    return findings


def mask(value: str) -> str:
    if len(value) <= 4:
        return "•" * len(value)
    return value[:2] + "•" * (len(value) - 4) + value[-2:]


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    findings = scan(root)
    if not findings:
        print("Ничего подозрительного не найдено.")
        return 0
    for path, number, kind, value in findings:
        print(f"{path}:{number}\t{kind}\t{value}")
    print(f"\nВсего: {len(findings)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
