package com.clipboard.sync

class ClipboardSync {

    companion object {
        init {
            System.loadLibrary("clipboard_sync")
        }
    }

    external fun startSync(config: String, ): String;

    external fun stopSync(): String;

    external fun status(): String;

    external fun queue(clipboard: String): String;
}