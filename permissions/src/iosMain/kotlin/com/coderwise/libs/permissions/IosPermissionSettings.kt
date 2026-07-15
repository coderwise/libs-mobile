package com.coderwise.libs.permissions

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * Deep-links into this app's page in the Settings app. Once iOS has decided a
 * permission (denied/restricted), re-requesting it is a silent no-op — this is
 * the only remaining way to let the user change it.
 */
internal fun openIosAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
