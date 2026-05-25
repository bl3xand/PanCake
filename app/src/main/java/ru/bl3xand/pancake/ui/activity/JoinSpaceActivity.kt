package ru.bl3xand.pancake.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.databinding.ActivityJoinSpaceBinding
import ru.bl3xand.pancake.ui.viewmodel.ChooseCharacterViewModel
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.logs.Logger
import java.util.UUID

class JoinSpaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinSpaceBinding
    private val viewModel: ChooseCharacterViewModel by viewModels()
    private var qrHandled = false
    private var lastScanAt: Long = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanner()
        } else {
            Snackbar.make(binding.root, R.string.camera_permission_required, Snackbar.LENGTH_LONG).show()
        }
    }

    private val qrCallback = BarcodeCallback { result: BarcodeResult? ->
        val now = System.currentTimeMillis()
        if (now - lastScanAt < 500L) return@BarcodeCallback
        lastScanAt = now
        if (qrHandled) return@BarcodeCallback
        val text = result?.text?.trim().orEmpty()
        Logger.logDebug(tag = "JoinSpaceActivity", msg = "QR raw: $text")
        if (text.isBlank()) return@BarcodeCallback
        applySpaceCode(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinSpaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@JoinSpaceActivity, SpaceEntryActivity::class.java))
                finish()
            }
        })

        binding.spaceBarcodeView.barcodeView.decoderFactory =
            DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))

        binding.buttonApplyJoinSpace.setOnClickListener {
            it.performAppHapticTap()
            val code = binding.inputJoinSpaceCode.text?.toString().orEmpty().trim()
            applySpaceCode(code)
        }

        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) startScanner()
    }

    override fun onPause() {
        super.onPause()
        binding.spaceBarcodeView.pause()
    }

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) {
            startScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startScanner() {
        binding.spaceBarcodeView.decodeContinuous(qrCallback)
        binding.spaceBarcodeView.resume()
    }

    private fun applySpaceCode(rawCode: String) {
        val code = normalizeSpaceIdCandidate(rawCode)
        if (code == null) {
            binding.layoutJoinSpaceCode.error = getString(R.string.space_code_invalid)
            return
        }
        binding.layoutJoinSpaceCode.error = null
        qrHandled = true
        binding.spaceBarcodeView.pause()
        FirebaseDatabase.getInstance().reference
            .child("spaces")
            .child(code)
            .child("meta")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    binding.layoutJoinSpaceCode.error = getString(R.string.space_code_invalid)
                    qrHandled = false
                    binding.spaceBarcodeView.resume()
                    return@addOnSuccessListener
                }
                viewModel.saveSpaceId(code)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                binding.layoutJoinSpaceCode.error = getString(R.string.error_unknown)
                qrHandled = false
                binding.spaceBarcodeView.resume()
            }
    }

    private fun isValidSpaceId(value: String): Boolean {
        val s = value.trim()
        if (runCatching { UUID.fromString(s) }.isSuccess) return true
        val hex = s.replace("-", "")
        return hex.length == 32 && hex.all { it.isDigit() || (it.lowercaseChar() in 'a'..'f') }
    }

    private fun normalizeSpaceIdCandidate(raw: String): String? {
        val s = raw.trim()
            .replace("[\\u2011\\u2012\\u2013\\u2014\\u2212]".toRegex(), "-")
            .replace(" ", "")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        return when {
            runCatching { UUID.fromString(s) }.isSuccess -> s
            s.length == 32 && s.all { it.isDigit() || (it.lowercaseChar() in 'a'..'f') } -> formatUuidFrom32Hex(s)
            else -> null
        }
    }

    private fun formatUuidFrom32Hex(hex: String): String {
        val l = hex.lowercase()
        return l.substring(0,8) + "-" +
               l.substring(8,12) + "-" +
               l.substring(12,16) + "-" +
               l.substring(16,20) + "-" +
               l.substring(20,32)
    }
}

