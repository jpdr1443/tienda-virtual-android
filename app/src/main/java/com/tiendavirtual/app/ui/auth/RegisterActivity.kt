package com.tiendavirtual.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import com.tiendavirtual.app.BuildConfig
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.databinding.ActivityRegisterBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator

/**
 * Registro de cuentas nuevas para los tres roles. Comprador y Vendedor se
 * registran libremente; para Administrador se exige un código de
 * autorización (definido en local.properties) para que cualquiera no pueda
 * crearse una cuenta con permisos totales.
 */
class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.register_title)

        // Campos que dependen del rol elegido.
        binding.roleGroup.setOnCheckedStateChangeListener { _, _ ->
            val role = selectedRole()
            binding.layoutStore.visibility = if (role == Role.SELLER) View.VISIBLE else View.GONE
            binding.layoutAdminCode.visibility = if (role == Role.ADMIN) View.VISIBLE else View.GONE
        }
        binding.btnRegister.setOnClickListener { register() }
    }

    private fun selectedRole(): Role = when (binding.roleGroup.checkedChipId) {
        R.id.chip_admin -> Role.ADMIN
        R.id.chip_seller -> Role.SELLER
        else -> Role.BUYER
    }

    /** Valida cada campo, muestra los errores en el formulario y crea la cuenta. */
    private fun register() {
        val name = binding.inputName.text?.toString().orEmpty().trim()
        val email = binding.inputEmail.text?.toString().orEmpty().trim()
        val phone = binding.inputPhone.text?.toString().orEmpty().trim()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val confirm = binding.inputConfirm.text?.toString().orEmpty()
        val role = selectedRole()

        binding.layoutName.error = if (name.length >= 3) null else getString(R.string.error_name)
        binding.layoutEmail.error =
            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else getString(R.string.error_email)
        binding.layoutPassword.error = if (password.length >= 6) null else getString(R.string.error_password)
        binding.layoutConfirm.error = if (confirm == password) null else getString(R.string.error_password_match)
        binding.layoutAdminCode.error =
            if (role != Role.ADMIN || binding.inputAdminCode.text?.toString() == BuildConfig.ADMIN_REGISTRATION_CODE) null
            else getString(R.string.error_admin_code)

        val hasError = listOf(
            binding.layoutName, binding.layoutEmail, binding.layoutPassword,
            binding.layoutConfirm, binding.layoutAdminCode
        ).any { it.error != null }
        if (hasError) return

        launchUi(binding.progress, binding.btnRegister) {
            val store = binding.inputStore.text?.toString().orEmpty().trim()
            auth.register(name, email, phone, password, role, if (role == Role.SELLER) store else "")
            toast(R.string.msg_registered)
            Navigator.goHome(this, role)
        }
    }
}
