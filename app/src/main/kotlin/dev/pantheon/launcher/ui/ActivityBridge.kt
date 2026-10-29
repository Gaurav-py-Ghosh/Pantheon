package dev.pantheon.launcher.ui

import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.pantheon.launcher.MainActivity

/** Lets the (non-composable) launcher controller start pickers, permission prompts and system dialogs. */
class ActivityBridge {
    internal var onImages: ((List<Uri>) -> Unit)? = null
    internal var onPermissions: ((Boolean) -> Unit)? = null
    internal var onResult: ((Boolean) -> Unit)? = null

    internal lateinit var images: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>
    internal lateinit var permissions: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
    internal lateinit var results: ManagedActivityResultLauncher<Intent, ActivityResult>
    internal lateinit var senders: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>

    fun pickImages(callback: (List<Uri>) -> Unit) {
        onImages = callback
        images.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** [callback] gets true if any of [perms] was granted. */
    fun requestPermissions(perms: Array<String>, callback: (Boolean) -> Unit) {
        onPermissions = callback
        permissions.launch(perms)
    }

    fun startForResult(intent: Intent, callback: (Boolean) -> Unit) {
        onResult = callback
        results.launch(intent)
    }

    fun startIntentSender(sender: IntentSender, callback: (Boolean) -> Unit) {
        onResult = callback
        senders.launch(IntentSenderRequest.Builder(sender).build())
    }

    internal var activity: MainActivity? = null

    /** Runs a widget's own setup screen; the result comes back through [MainActivity.onActivityResult]. */
    fun configureWidget(host: AppWidgetHost, appWidgetId: Int, callback: (Boolean) -> Unit) {
        val a = activity ?: return callback(true)
        a.onConfigureResult = callback
        runCatching {
            host.startAppWidgetConfigureActivityForResult(a, appWidgetId, 0, MainActivity.REQUEST_CONFIGURE_WIDGET, null)
        }.onFailure {
            // Some widgets declare a setup screen they won't let us start; place them anyway.
            a.onConfigureResult = null
            callback(true)
        }
    }
}

@Composable
fun rememberActivityBridge(): ActivityBridge {
    val bridge = remember { ActivityBridge() }
    bridge.activity = LocalContext.current as? MainActivity
    bridge.images = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        bridge.onImages?.invoke(uris)
        bridge.onImages = null
    }
    bridge.permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        bridge.onPermissions?.invoke(result.values.any { it })
        bridge.onPermissions = null
    }
    bridge.results = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        bridge.onResult?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
        bridge.onResult = null
    }
    bridge.senders = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        bridge.onResult?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
        bridge.onResult = null
    }
    return bridge
}
