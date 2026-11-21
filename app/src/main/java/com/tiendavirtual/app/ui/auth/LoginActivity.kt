package com.tiendavirtual.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.databinding.ActivityLoginBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.BiometricOffer
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.util.BiometricAuth

/**
 * Inicio de sesión con correo y contraseña (Firebase Authentication) o con
 * huella dactilar. El rol NO se elige aquí: se lee del perfil en Firestore,
 * así cada usuario entra automáticamente al panel que le corresponde.
 */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = AuthRepository()
    private lateinit var biometric: BiometricAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        biometric = BiometricAuth(this)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnBiometric.setOnClickListener { loginWithFingerprint() }
        binding.linkForgot.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java)
                .putExtra(RecoverPasswordActivity.EXTRA_EMAIL, binding.inputEmail.text?.toString()))
        }
        binding.linkRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Si el teléfono tiene sensor de huella se muestra el botón. Cuando la huella
        // ya está activada, al abrir la app (no después de cerrar sesión) se pide de inmediato.
        when {
            biometric.isEnabled() && biometric.isAvailable() -> {
                binding.btnBiometric.visibility = View.VISIBLE
                binding.btnBiometric.text = getString(R.string.btn_biometric_for, biometric.enabledEmail())
                binding.inputEmail.setText(biometric.enabledEmail())
                if (savedInstanceState == null && !intent.getBooleanExtra(EXTRA_FROM_LOGOUT, false)) {
                    loginWithFingerprint()
                }
            }
            biometric.isAvailable() -> binding.btnBiometric.visibility = View.VISIBLE
            else -> binding.btnBiometric.visibility = View.GONE
        }
    }

    /** Valida el formulario y autentica contra Firebase. */
    private fun attemptLogin() {
        val email = binding.inputEmail.text?.toString().orEmpty().trim()
        val password = binding.inputPassword.text?.toString().orEmpty()

        binding.layoutEmail.error =
            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else getString(R.string.error_email)
        binding.layoutPassword.error =
            if (password.length >= MIN_PASSWORD) null else getString(R.string.error_password)
        if (binding.layoutEmail.error != null || binding.layoutPassword.error != null) return

        launchUi(binding.progress, binding.btnLogin, binding.btnBiometric) {
            val user = auth.login(email, password)
            BiometricOffer.offerThenGoHome(this, email, password, user.roleEnum)
        }
    }

    /** Descifra las credenciales con la huella y autentica con ellas. */
    private fun loginWithFingerprint() {
        biometric.login(
            onCredentials = { email, password ->
                launchUi(binding.progress, binding.btnLogin, binding.btnBiometric) {
                    val user = auth.login(email, password)
                    Navigator.goHome(this, user.roleEnum)
                }
            },
            onError = { message -> toast(message) }
        )
    }

    companion object {
        const val EXTRA_FROM_LOGOUT = "from_logout"
        private const val MIN_PASSWORD = 6
    }
}
