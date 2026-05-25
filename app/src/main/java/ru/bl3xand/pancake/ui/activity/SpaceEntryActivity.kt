package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.bl3xand.pancake.data.model.SpaceMeta
import ru.bl3xand.pancake.databinding.ActivitySpaceEntryBinding
import ru.bl3xand.pancake.ui.viewmodel.ChooseCharacterViewModel
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.security.SpaceCrypto
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.user.UserNameNormalizer
import java.util.UUID

class SpaceEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpaceEntryBinding
    private val viewModel: ChooseCharacterViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpaceEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                clearUserDataAndSignOut {
                    val intent =
                        Intent(this@SpaceEntryActivity, ChooseCharacterActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })

        setupUi()
    }

    private fun setupUi() {
        binding.buttonCreateSpace.setOnClickListener {
            it.performAppHapticTap()
            val spaceId = UUID.randomUUID().toString()
            createSpaceInFirebase(spaceId)
            viewModel.saveSpaceId(spaceId)
            startActivity(Intent(this, SpaceWelcomeShareActivity::class.java))
            finish()
        }

        binding.buttonJoinSpace.setOnClickListener {
            it.performAppHapticTap()
            startActivity(Intent(this, JoinSpaceActivity::class.java))
            finish()
        }
    }

    private fun createSpaceInFirebase(spaceId: String) {
        val userName = getAppPreferences(this)
            .getString(
                ru.bl3xand.pancake.config.AppConfig.Preferences.CHARACTER_KEY,
                ru.bl3xand.pancake.config.AppConfig.Characters.DEFAULT
            )
            .let { UserNameNormalizer.normalize(it) }
        val meta = SpaceMeta(
            id = spaceId,
            createdBy = userName,
            createdAt = System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference
            .child("spaces")
            .child(spaceId)
            .child("meta")
            .setValue(SpaceCrypto.encryptModel(spaceId, meta))
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun clearUserDataAndSignOut(onComplete: (() -> Unit)? = null) {
        // Clear local preferences
        val prefs = getAppPreferences(this)
        prefs.edit {
            remove(ru.bl3xand.pancake.config.AppConfig.Preferences.CHARACTER_KEY)
            remove(ru.bl3xand.pancake.config.AppConfig.Preferences.SPACE_ID_KEY)
        }

        // Sign out from Firebase Auth
        FirebaseAuth.getInstance().signOut()

        // Навигация сразу, не ждём очистку credential state
        onComplete?.invoke()

        // Clear credential state в фоне
        CoroutineScope(Dispatchers.IO).launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                ru.bl3xand.pancake.utils.logs.Logger.logError(
                    tag = "SpaceEntryActivity",
                    msg = "Failed to clear credential state: ${e.message}"
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) return
        // User left without choosing an action — clear data
        clearUserDataAndSignOut()
    }
}