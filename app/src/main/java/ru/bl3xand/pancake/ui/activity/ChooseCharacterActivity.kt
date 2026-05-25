package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.databinding.ActivityChooseCharacterBinding
import ru.bl3xand.pancake.ui.viewmodel.ChooseCharacterViewModel
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.user.UserNameNormalizer

class ChooseCharacterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChooseCharacterBinding
    private val chooseCharacterViewModel: ChooseCharacterViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "ChooseCharacterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseCharacterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        val prefs = getAppPreferences(this)
        val userName = prefs.getString(AppConfig.Preferences.CHARACTER_KEY, null)
        val normalizedLocalUserName = UserNameNormalizer.normalize(userName, fallback = "")
        if (userName != null && normalizedLocalUserName.isNotBlank() && normalizedLocalUserName != userName) {
            chooseCharacterViewModel.saveUserName(normalizedLocalUserName)
        }
        val spaceId = prefs.getString(AppConfig.Preferences.SPACE_ID_KEY, null)
        if (normalizedLocalUserName.isNotBlank() && !spaceId.isNullOrBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (normalizedLocalUserName.isNotBlank()) {
            openSpaceEntry()
            return
        }

        setupUi()
    }

    private fun setupUi() {
        binding.loginGoogleButton.setOnClickListener {
            it.performAppHapticTap()
            signInWithGoogle()
        }

        binding.buttonSignInWithEmail.setOnClickListener {
            it.performAppHapticTap()
            startActivity(Intent(this, AuthActivity::class.java))
        }
    }

    private fun signInWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@ChooseCharacterActivity
                )
                handleSignInResult(result)
            } catch (e: GetCredentialCancellationException) {
                Logger.logError(TAG, "Google Sign-In cancelled by user: $e")
            } catch (e: GetCredentialException) {
                Logger.logError(TAG, "Google Sign-In failed: ${e.type}, ${e.message}")
                Snackbar.make(
                    binding.root,
                    R.string.google_sign_in_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Logger.logError(TAG, "Unexpected credential type: ${credential.type}")
            Snackbar.make(binding.root, R.string.google_sign_in_failed, Snackbar.LENGTH_LONG)
                .show()
            return
        }

        try {
            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(credential.data)

            firebaseAuthWithGoogle(
                idToken = googleIdTokenCredential.idToken,
                displayName = googleIdTokenCredential.displayName,
                email = googleIdTokenCredential.id
            )
        } catch (e: GoogleIdTokenParsingException) {
            Logger.logError(TAG, "Failed to parse Google ID Token: ${e.message}")
            Snackbar.make(binding.root, R.string.google_sign_in_failed, Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun firebaseAuthWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?
    ) {
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(authCredential)
            .addOnSuccessListener {
                val userName = UserNameNormalizer.normalize(displayName, fallback = "")
                    .takeIf { it.isNotBlank() }
                    ?: email?.substringBefore('@')?.takeIf { part -> part.isNotBlank() }
                    ?: email?.takeIf { mail -> mail.isNotBlank() }
                    ?: AppConfig.Characters.DEFAULT

                chooseCharacterViewModel.saveUserName(userName)
                openSpaceEntry()
            }
            .addOnFailureListener { exception ->
                Logger.logError(
                    TAG,
                    "FirebaseAuth signInWithCredential failed: ${exception.message}"
                )
                Snackbar.make(
                    binding.root,
                    R.string.google_sign_in_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
    }

    private fun openSpaceEntry() {
        val intent = Intent(this, SpaceEntryActivity::class.java)
        startActivity(intent)
        finish()
    }

}