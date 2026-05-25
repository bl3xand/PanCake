package ru.bl3xand.pancake.utils.permissions

import android.Manifest
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity


data class PermissionsRequestManager(
    private val activity: AppCompatActivity,
    private val permissionResultLauncher: ActivityResultLauncher<Array<String>>
) {
    companion object {
        private const val TAG = "PermissionsRequestManager"

        private val permissionList = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            )
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun permissionRequest() =
        // check permissions
        PermissionRequest(
            activity,
            permissionList,
            permissionResultLauncher,
        ).acquirePermissions()
}
