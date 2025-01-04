package com.clipboard.sync

import android.content.Intent
import android.os.Build
import android.os.Parcelable

@Suppress("DEPRECATION")
inline fun <reified T: Parcelable>Intent.getParcelableExtraProvider(identifierParameter: String): T? {

    return if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelableExtra(identifierParameter, T::class.java)
    } else {
        this.getParcelableExtra(identifierParameter)
    }

}

@Suppress("DEPRECATION")
inline fun <reified T: Parcelable>Intent.getParcelableArrayListExtraProvider(identifierParameter: String): java.util.ArrayList<T>? {

    return if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelableArrayListExtra(identifierParameter, T::class.java)
    } else {
        this.getParcelableArrayListExtra(identifierParameter)
    }

}

@Suppress("DEPRECATION")
inline fun <reified T: java.io.Serializable>Intent.getSerializableExtraProvider(identifierParameter: String): T? {

    return if (Build.VERSION.SDK_INT >= 33) {
        this.getSerializableExtra(identifierParameter, T::class.java)
    } else {
        this.getSerializableExtra(identifierParameter) as T?
    }

}