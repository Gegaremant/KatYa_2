package com.katya.app.tools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactorTest {

    /**
     * Feedback 07.10: the app writes the command it runs in the sandbox into the log, and
     * that command carries the DeepSeek authorisation — `ds_session_id`, the AWS session
     * token and the signature — base64-encoded. The log is then handed over for review, so
     * the secret left the device. These tests pin the behaviour that stops it.
     */
    @Test
    fun `base64 session blob is removed`() {
        val line =
            "bash -c echo 'eyJ0b2tlbiI6ImlMUkplZ3VjYjdYUTJrRUZpOEJBSWd6aDljR0NEOEc5TkhMSU1DVC1TRVM" +
                "9Uk9KQTVobjA4K0Y0VDZ0OEFyTzZIZ215VkY2WVl0ZXZIMjZZ' | base64 -d > /root/deepseek-auth.json"
        val redacted = SecretRedactor.redact(line)
        assertFalse(redacted.contains("eyJ"), "токен остался в журнале: $redacted")
        assertTrue(redacted.contains("base64 -d"), "полезная часть команды потерялась")
    }

    @Test
    fun `named secrets are removed`() {
        val line = "cookie=ds_session_id=dk270ccf7ab8d42 aws_session_token=short sid=abc123"
        val redacted = SecretRedactor.redact(line)
        assertFalse(redacted.contains("dk270ccf7ab8d42"), "значение утекло: $redacted")
        assertFalse(redacted.contains("aws_session_token=short"), "значение утекло: $redacted")
    }

    @Test
    fun `authorization header is removed`() {
        val redacted = SecretRedactor.redact("Authorization: Bearer abcdefghijklmnopqrstuvwxyz")
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"), "заголовок утек: $redacted")
    }

    @Test
    fun `ordinary paths and words survive`() {
        val line =
            "--bind=/data/user/0/com.inspiredandroid.katya/files/linux-sandbox/rootfs:/ " +
                "-0 -w /root /usr/bin/bash -c test -d /root/FreeDeepSeekAPI"
        assertTrue(SecretRedactor.redact(line).contains("FreeDeepSeekAPI"))
    }

    @Test
    fun `short base64 looking words are not masked`() {
        // false positives are acceptable in a log, but masking ordinary paths would make
        // the diagnostics useless, which is the whole point of the argv in the log.
        val redacted = SecretRedactor.redact("proot ld.so.cache libtalloc.so.2 /system/bin/linker64")
        assertTrue(redacted.contains("libtalloc.so.2"))
        assertTrue(redacted.contains("/system/bin/linker64"))
    }
}
