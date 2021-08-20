package com.clipboard.sync

import java.nio.ByteBuffer

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

    external fun send(config: String, clipboard: ByteBuffer, messageType: String): String

    external fun receive(): String

    external fun queue(clipboard: ByteBuffer, messageType: String): String
}