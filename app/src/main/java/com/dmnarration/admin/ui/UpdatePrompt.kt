package com.dmnarration.admin.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UpdatePrompt"

/**
 * Offer the update, once, on launch.
 *
 * ── WHY THIS EXISTS ────────────────────────────────────────────────────────
 *
 * Dean asked how to update the app on his phone and the honest answer was "open
 * Play and look". A server-side change can also make an installed build wrong in
 * ways the build cannot detect — see DecoderExposureTest — so "how does the
 * phone in his pocket get the new one" is a real question, not polish. It was
 * deferred to the next release that had something worth prompting for, and this
 * is that release.
 *
 * ── FLEXIBLE, NOT IMMEDIATE ────────────────────────────────────────────────
 *
 * IMMEDIATE blocks the app behind a full-screen updater. Nothing here is urgent
 * enough to take the board away from him mid-session, and a blocking updater on
 * a bad connection is an app that cannot be opened. FLEXIBLE offers, downloads
 * in the background, and lets him carry on.
 *
 * ── SILENT ON FAILURE, AND THAT IS DELIBERATE ──────────────────────────────
 *
 * The flow only exists for a build INSTALLED FROM PLAY. On a debug build, a
 * sideload, or a device with no Play services, the manager throws or answers
 * UPDATE_NOT_AVAILABLE — none of which the person holding the phone can act on.
 * An error banner there would be noise about a mechanism they never invoked.
 *
 * That makes this the one place in the app where swallowing a failure is right,
 * so it is logged rather than discarded: "no update offered" and "the check
 * could not run" are different, and logcat can tell them apart even though the
 * screen deliberately does not.
 *
 * IT CANNOT BE VERIFIED ON AN EMULATOR. Confirming it works means installing
 * from a Play track with a higher versionCode available. Until that happens this
 * is wired, compiled, and unproven — and saying so is more useful than a test
 * that only proves the manager can be constructed.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val activity = context as? Activity

    val launcher: ActivityResultLauncher<IntentSenderRequest> =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // Declining is a normal outcome, not an error. Play will ask again on
            // a later launch; nagging within one session would be worse than the
            // problem.
            Log.i(TAG, "update flow finished with resultCode=${it.resultCode}")
        }

    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            val info = withContext(Dispatchers.IO) {
                // getAppUpdateInfo returns a Task; awaiting it off the main
                // thread keeps a slow or absent Play service from stalling the
                // first frame.
                com.google.android.gms.tasks.Tasks.await(manager.appUpdateInfo)
            }
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            if (available && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                manager.startUpdateFlowForResult(
                    info,
                    launcher,
                    com.google.android.play.core.appupdate.AppUpdateOptions
                        .newBuilder(AppUpdateType.FLEXIBLE)
                        .build(),
                )
            } else {
                Log.i(TAG, "no update offered (availability=${info.updateAvailability()})")
            }
        }.onFailure {
            // Expected on debug, sideloads and emulators without Play.
            Log.i(TAG, "update check could not run: ${it.message}")
        }
    }
}
