package com.clipboard.sync

class ClipboardSync {

    companion object {
        init {
            System.loadLibrary("clipboard_sync")
        }
    }

    external fun startSync(config: String): String

    external fun stopSync(): String

    external fun status(): String

    external fun isRunning(): Boolean

    external fun send(config: String, clipboard: ByteArray, messageType: String, timeoutMs: Long): String
    external fun sendFiles(config: String, map: Map<String, ByteArray>, timeoutMs: Long): String

    external fun receive(): String

    external fun generateCertificates(): String

    external fun queue(clipboard: ByteArray, messageType: String): String
    external fun queueFiles(map: Map<String, ByteArray>): String
}