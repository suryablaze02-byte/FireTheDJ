package com.autodj.tv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Music on the TV itself: its storage and any USB drive plugged into it, as Android's media
 * library has indexed them. TVs usually have no folder picker, so this is how music gets in.
 */
object DeviceMusic {
    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Songs a minute or longer (ringtones, notification sounds and voice notes left out). Blocking. */
    fun scan(context: Context): List<Uri> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            // Every volume: internal storage and USB drives alike.
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 60000"
        val out = mutableListOf<Uri>()
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Audio.Media._ID), selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) out += ContentUris.withAppendedId(collection, c.getLong(id))
        }
        return out
    }
}
