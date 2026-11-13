package com.tiendavirtual.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.databinding.ActivityRecoverPasswordBinding
import com.tiendavirtual.app.ui.common.BaseActivity

/**
 * Recuperación de contraseña: Firebase envía al correo un enlace para crear
 * una contraseña nueva. Funciona igual para los tres roles.
 */
class RecoverPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityRecoverPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.recover_title)
        binding.inputEmail.setText(intent.getStringExtra(EXTRA_EMAIL))

        binding.btnSend.setOnClickListener {
            val email = binding.inputEmail.text?.toString().orEmpty().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.layoutEmail.error = getString(R.string.error_email)
                return@setOnClickListener
            }
            binding.layoutEmail.error = null
            launchUi(binding.progress, binding.btnSend) {
                AuthRepository().sendPasswordReset(email)
                toast(getString(R.string.msg_recover_sent_to, email))
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_EMAIL = "email"
    }
}
