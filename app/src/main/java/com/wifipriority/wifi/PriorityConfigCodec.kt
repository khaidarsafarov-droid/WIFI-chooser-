package com.wifipriority.wifi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Length-prefixed encoding of [PriorityConfig] before it is encrypted. */
internal object PriorityConfigCodec {
    private const val VERSION = 1

    fun encode(config: PriorityConfig): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(VERSION)
            out.writeBoolean(config.enabled)
            writeCredential(out, config.phone)
            writeCredential(out, config.truck)
        }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): PriorityConfig {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            check(version == VERSION) { "Unsupported credential payload version $version" }
            return PriorityConfig(
                enabled = input.readBoolean(),
                phone = readCredential(input),
                truck = readCredential(input),
            )
        }
    }

    private fun writeCredential(out: DataOutputStream, credential: NetworkCredential?) {
        if (credential == null) {
            out.writeBoolean(false)
            return
        }
        out.writeBoolean(true)
        writeBytes(out, credential.ssid)
        writeBytes(out, credential.password)
    }

    private fun readCredential(input: DataInputStream): NetworkCredential? {
        if (!input.readBoolean()) return null
        return NetworkCredential(
            ssid = readBytes(input),
            password = readBytes(input),
        )
    }

    private fun writeBytes(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBytes(input: DataInputStream): String {
        val size = input.readInt()
        check(size in 0..MAX_FIELD_BYTES) { "Credential field length $size is invalid" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private const val MAX_FIELD_BYTES = 64 * 1024
}
