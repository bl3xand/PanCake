package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.databinding.ActivityAuthBinding
import ru.bl3xand.pancake.ui.viewmodel.ChooseCharacterViewModel
import ru.bl3xand.pancake.utils.ui.performAppHapticTap

class AuthActivity : AppCompatActivity() {
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private val chooseCharacterViewModel: ChooseCharacterViewModel by viewModels()

    companion object {
        private const val TAG = "AuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()

        setupUi()
    }

    private fun setupUi() {
        binding.buttonSignIn.setOnClickListener {
            it.performAppHapticTap()
            val email = binding.inputEmail.text?.toString()?.trim() ?: ""
            val password = binding.inputPassword.text?.toString() ?: ""
            binding.layoutEmail.error = null
            binding.layoutPassword.error = null

            if (email.isBlank()) {
                binding.layoutEmail.error = getString(R.string.error_email_required)
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                binding.layoutEmail.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.layoutPassword.error = getString(R.string.error_password_required)
                return@setOnClickListener
            }
            if (password.length < 8) {
                binding.layoutPassword.error = getString(R.string.error_password_too_short)
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userName =
                            email.substringBefore('@').takeIf { it.isNotBlank() } ?: email
                        chooseCharacterViewModel.saveUserName(userName)
                        openSpaceEntry()
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                        val userMsg = if (errorMsg?.contains("auth credential is incorrect", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential is malformed", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential has expired", ignoreCase = true) == true) {
                            getString(R.string.error_auth_credential)
                        } else if (errorMsg?.contains("network error", ignoreCase = true) == true) {
                            getString(R.string.error_network)
                        } else {
                            errorMsg ?: "Sign In failed"
                        }
                        Snackbar.make(binding.root, userMsg, Snackbar.LENGTH_SHORT).show()
                    }
                }
        }

        binding.buttonSignUp.setOnClickListener {
            it.performAppHapticTap()
            val email = binding.inputEmail.text?.toString()?.trim() ?: ""
            val password = binding.inputPassword.text?.toString() ?: ""
            binding.layoutEmail.error = null
            binding.layoutPassword.error = null

            if (email.isBlank()) {
                binding.layoutEmail.error = getString(R.string.error_email_required)
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                binding.layoutEmail.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.layoutPassword.error = getString(R.string.error_password_required)
                return@setOnClickListener
            }
            if (password.length < 8) {
                binding.layoutPassword.error = getString(R.string.error_password_too_short)
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userName =
                            email.substringBefore('@').takeIf { it.isNotBlank() } ?: email
                        chooseCharacterViewModel.saveUserName(userName)
                        Snackbar.make(binding.root, getString(R.string.sign_up_success), Snackbar.LENGTH_SHORT).show()
                        openSpaceEntry()
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                        val userMsg = if (errorMsg?.contains("auth credential is incorrect", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential is malformed", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential has expired", ignoreCase = true) == true) {
                            getString(R.string.error_auth_credential)
                        } else if (errorMsg?.contains("email address is already in use", ignoreCase = true) == true) {
                            getString(R.string.email_already_in_use)
                        } else if (errorMsg?.contains("network error", ignoreCase = true) == true) {
                            getString(R.string.error_network)
                        } else {
                            errorMsg ?: "Sign Up failed"
                        }
                        Snackbar.make(binding.root, userMsg, Snackbar.LENGTH_SHORT).show()
                    }
                }
        }

        binding.buttonBack.setOnClickListener {
            it.performAppHapticTap()
            finish()
        }

        binding.buttonForgotPassword.setOnClickListener {
            it.performAppHapticTap()
            val email = binding.inputEmail.text?.toString()?.trim() ?: ""
            binding.layoutEmail.error = null

            if (email.isBlank()) {
                binding.layoutEmail.error = getString(R.string.error_email_required)
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                binding.layoutEmail.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.reset_password_email_sent, email),
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                        val userMsg = if (errorMsg?.contains("auth credential is incorrect", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential is malformed", ignoreCase = true) == true ||
                            errorMsg?.contains("auth credential has expired", ignoreCase = true) == true) {
                            getString(R.string.error_auth_credential)
                        } else if (errorMsg?.contains("email address is already in use", ignoreCase = true) == true) {
                            getString(R.string.email_already_in_use)
                        } else if (errorMsg?.contains("network error", ignoreCase = true) == true) {
                            getString(R.string.error_network)
                        } else {
                            errorMsg ?: getString(R.string.reset_password_email_error)
                        }
                        Snackbar.make(binding.root, userMsg, Snackbar.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun openSpaceEntry() {
        val intent = Intent(this, SpaceEntryActivity::class.java)
        startActivity(intent)
        finish()
    }
}
