package ru.bl3xand.pancake.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.databinding.ActivitySpaceShareBinding
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.share.ShareHelper
import ru.bl3xand.pancake.ui.dialogs.Dialogs

class SpaceShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpaceShareBinding
    private lateinit var credentialManager: CredentialManager
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpaceShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        binding.buttonShare.isEnabled = false

        credentialManager = CredentialManager.create(this)

        val spaceId = getAppPreferences(this)
            .getString(AppConfig.Preferences.SPACE_ID_KEY, null)
            .orEmpty()

        if (spaceId.isBlank()) {
            finish()
            return
        }

        binding.spaceCodeText.text = spaceId

        binding.buttonMoreOptions.setOnClickListener { view ->
            view.performAppHapticTap()
            showOptionsMenu(view)
        }

        // ✅ QR генерируется в фоне — убираем "Skipped 50 frames!"
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = kotlinx.coroutines.withContext(Dispatchers.Default) {
                createQr(spaceId, 1200)
            }
            qrBitmap = bitmap
            binding.spaceQrImage.setImageBitmap(bitmap)
            binding.buttonShare.isEnabled = true
        }

        binding.buttonCopySpaceCode.setOnClickListener { view ->
            view.performAppHapticTap()
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("spaceId", spaceId))
            Snackbar.make(binding.root, R.string.space_code_copied, Snackbar.LENGTH_SHORT).show()
        }

        binding.buttonShare.setOnClickListener { view ->
            view.performAppHapticTap()
            val ok = ShareHelper.shareSpace(
                activity = this,
                spaceId = spaceId,
                qrBitmap = qrBitmap,
                spaceCodeLabel = getString(R.string.space_code_label),
                chooserTitle = getString(R.string.share)
            )
            if (!ok) {
                Snackbar.make(binding.root, R.string.error_unknown, Snackbar.LENGTH_SHORT).show()
            }
        }

        // ✅ Подтверждение выхода из пространства
        binding.buttonLogout.setOnClickListener {
            it.performAppHapticTap()
            Dialogs.showExitSpaceConfirmationDialog(this) {
                clearUserDataAndSignOut()
            }
        }
    }

    private fun applySystemInsets() {
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft,
                top = initialTop + systemBars.top,
                right = initialRight,
                bottom = initialBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun showOptionsMenu(anchor: android.view.View) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(R.menu.menu_space_share_options, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_contact_developer -> {
                    openDeveloperProfile()
                    true
                }

                R.id.action_open_licenses -> {
                    startActivity(Intent(this, OpenSourceLicensesActivity::class.java))
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun openDeveloperProfile() {
        val url = getString(R.string.developer_github_url)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }


    private fun clearUserDataAndSignOut() {
        // 1. Очищаем локальные данные (мгновенно)
        val prefs = getAppPreferences(this)
        prefs.edit {
            remove(AppConfig.Preferences.CHARACTER_KEY)
            remove(AppConfig.Preferences.SPACE_ID_KEY)
        }

        // 2. Sign out из Firebase (мгновенно)
        FirebaseAuth.getInstance().signOut()

        // 3. Сразу переходим — НЕ ждём clearCredentialState
        val intent = Intent(this@SpaceShareActivity, ChooseCharacterActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()

        // 4. Очистка credential state в фоне (не критична)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Logger.logError(
                    tag = "SpaceShareActivity",
                    msg = "Failed to clear credential state: ${e.message}"
                )
            }
        }
    }

    private fun createQr(content: String, size: Int): Bitmap {
        val matrix: BitMatrix =
            MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = createBitmap(size, size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return bitmap
    }
}