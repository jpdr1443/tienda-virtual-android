package com.tiendavirtual.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.databinding.ActivityLoginBinding
import com.tiendavirtual.app.ui.common.BaseActivity
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

        // Si la huella está activada se muestra el botón y, al abrir la app
        // (no después de cerrar sesión), se lanza el diálogo de inmediato.
        if (biometric.isEnabled() && biometric.isAvailable()) {
            binding.btnBiometric.visibility = View.VISIBLE
            binding.btnBiometric.text = getString(R.string.btn_biometric_for, biometric.enabledEmail())
            binding.inputEmail.setText(biometric.enabledEmail())
            if (savedInstanceState == null && !intent.getBooleanExtra(EXTRA_FROM_LOGOUT, false)) {
                loginWithFingerprint()
            }
        } else {
            binding.btnBiometric.visibility = View.GONE
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
            offerFingerprint(user, email, password)
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

    /**
     * Después del primer ingreso con contraseña ofrece activar la huella,
     * si el teléfono tiene sensor y aún no está activada para este correo.
     */
    private fun offerFingerprint(user: User, email: String, password: String) {
        if (!biometric.isAvailable() || biometric.enabledEmail() == email) {
            Navigator.goHome(this, user.roleEnum)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_message)
            .setCancelable(false)
            .setNegativeButton(R.string.not_now) { _, _ -> Navigator.goHome(this, user.roleEnum) }
            .setPositiveButton(R.string.activate) { _, _ ->
                biometric.enable(email, password) { enabled ->
                    toast(if (enabled) R.string.biometric_enabled else R.string.biometric_not_activated)
                    Navigator.goHome(this, user.roleEnum)
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_FROM_LOGOUT = "from_logout"
        private const val MIN_PASSWORD = 6
    }
}
