package com.autodj

import android.content.Context
import android.content.Intent

/**
 * The intent that opens this app's own home screen: the phone app's launcher activity, or the
 * TV app's (TVs list apps through the leanback launcher, which the plain lookup doesn't see).
 */
fun Context.openAppIntent(): Intent =
    packageManager.getLaunchIntentForPackage(packageName)
        ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
        ?: Intent().setPackage(packageName)
