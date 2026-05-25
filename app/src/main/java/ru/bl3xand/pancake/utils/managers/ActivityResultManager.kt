package ru.bl3xand.pancake.utils.managers

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import ru.bl3xand.pancake.ui.activity.MainActivity
import ru.bl3xand.pancake.utils.logs.Logger


object ActivityResultManager {
    private const val TAG = "ActivityResultManager"

    fun permissionResultLauncher(activity: MainActivity): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach { permission ->
                if (permission.value) {
                    Logger.logDebug(
                        tag = TAG,
                        msg = "PermissionResultLauncher: Permission ${permission.key} granted"
                    )
                } else {
                    Logger.logError(
                        tag = TAG,
                        msg = "PermissionResultLauncher: Permission ${permission.key} denied"
                    )
                }
            }
        }
    }
}