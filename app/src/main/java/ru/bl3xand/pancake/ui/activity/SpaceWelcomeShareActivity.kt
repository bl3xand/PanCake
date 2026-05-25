package ru.bl3xand.pancake.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.databinding.ActivitySpaceWelcomeShareBinding
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.ui.performAppHapticTap

class SpaceWelcomeShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpaceWelcomeShareBinding
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpaceWelcomeShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        binding.buttonShare.isEnabled = false

        val spaceId = getAppPreferences(this)
            .getString(AppConfig.Preferences.SPACE_ID_KEY, null)
            .orEmpty()

        if (spaceId.isBlank()) {
            finish()
            return
        }

        binding.spaceCodeText.text = spaceId

        // Generate QR in background
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = withContext(Dispatchers.Default) {
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
            binding.buttonContinue.visibility = View.VISIBLE
        }

        binding.buttonShare.setOnClickListener { view ->
            view.performAppHapticTap()
            val ok = ru.bl3xand.pancake.utils.share.ShareHelper.shareSpace(
                activity = this,
                spaceId = spaceId,
                qrBitmap = qrBitmap,
                spaceCodeLabel = getString(R.string.space_code_label),
                chooserTitle = getString(R.string.share)
            )
            if (ok) {
                binding.buttonContinue.visibility = View.VISIBLE
            } else {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    R.string.error_unknown,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.buttonContinue.setOnClickListener { view ->
            view.performAppHapticTap()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
