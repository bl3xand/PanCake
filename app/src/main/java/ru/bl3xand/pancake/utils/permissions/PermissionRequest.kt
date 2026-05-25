package ru.bl3xand.pancake.utils.permissions

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.bl3xand.pancake.utils.logs.Logger


class PermissionRequest(
    private val activity: AppCompatActivity,
    private val permissionArray: ArrayList<String>,
    private val permissionResultLauncher: ActivityResultLauncher<Array<String>>,
) {
    companion object {
        private const val TAG = "PermissionRequest"
    }

    // Returns true if all permissions in permissionArray are granted, false otherwise
    private fun checkPermissions(permissions: ArrayList<String>): Boolean {
        for (permission in permissions) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(activity, permission) -> {
                    Logger.logDebug(
                        tag = TAG,
                        msg = "CheckPermissions: Permission $permission is granted"
                    )
                }

                else -> {
                    Logger.logError(
                        tag = TAG,
                        msg = "CheckPermissions: Permission $permission is denied"
                    )
                    return false
                }
            }
        }
        return true
    }

    // Closes the program if some permissions are still not acquired after request
    @SuppressLint("CommitPrefEdits")
    fun acquirePermissions() {
        if (!checkPermissions(permissionArray)) {
            permissionResultLauncher.launch(
                permissionArray.toTypedArray()
            )
        } else {
            return
        }
    }
}
