package com.tiendavirtual.app.ui.admin

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityAdminUserFormBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.util.Session

/**
 * Formulario de usuario del Administrador.
 *  - Sin EXTRA_UID: crea una cuenta nueva (Authentication + perfil en Firestore).
 *  - Con EXTRA_UID: edita nombre, teléfono, rol, tienda y estado activo.
 */
class AdminUserFormActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminUserFormBinding
    private val users = UserRepository()
    private var editing: User? = null
    private val roles = Role.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lista desplegable de roles.
        binding.inputRole.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, roles.map { getString(it.label) })
        )
        binding.inputRole.setText(getString(Role.BUYER.label), false)

        val uid = intent.getStringExtra(EXTRA_UID)
        if (uid == null) {
            setupToolbar(binding.toolbar, R.string.user_new_title)
            binding.btnDelete.visibility = View.GONE
        } else {
            setupToolbar(binding.toolbar, R.string.user_form_title)
            binding.layoutPassword.visibility = View.GONE
            binding.inputEmail.isEnabled = false // El correo está ligado a la cuenta de Authentication.
            loadUser(uid)
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
    }

    private fun loadUser(uid: String) = launchUi(binding.progress) {
        val user = users.get(uid) ?: run { toast(R.string.error_profile_missing); finish(); return@launchUi }
        editing = user
        binding.inputName.setText(user.name)
        binding.inputEmail.setText(user.email)
        binding.inputPhone.setText(user.phone)
        binding.inputStore.setText(user.storeName)
        binding.inputRole.setText(getString(user.roleEnum.label), false)
        binding.switchActive.isChecked = user.active
    }

    private fun selectedRole(): Role =
        roles.firstOrNull { getString(it.label) == binding.inputRole.text.toString() } ?: Role.BUYER

    /** Valida y guarda: crea la cuenta si es nueva o actualiza el perfil si se está editando. */
    private fun save() {
        val name = binding.inputName.text?.toString().orEmpty().trim()
        val email = binding.inputEmail.text?.toString().orEmpty().trim()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val isNew = editing == null

        binding.layoutName.error = if (name.length >= 3) null else getString(R.string.error_name)
        binding.layoutEmail.error =
            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else getString(R.string.error_email)
        binding.layoutPassword.error =
            if (!isNew || password.length >= 6) null else getString(R.string.error_password)
        if (binding.layoutName.error != null || binding.layoutEmail.error != null ||
            binding.layoutPassword.error != null) return

        // Evita que el administrador se quite a sí mismo el rol o se desactive.
        if (editing?.uid == Session.user?.uid &&
            (selectedRole() != Role.ADMIN || !binding.switchActive.isChecked)) {
            toast(R.string.error_edit_self)
            return
        }

        val user = (editing ?: User()).copy(
            name = name,
            email = email,
            phone = binding.inputPhone.text?.toString().orEmpty().trim(),
            storeName = binding.inputStore.text?.toString().orEmpty().trim(),
            role = selectedRole().name,
            active = binding.switchActive.isChecked
        )

        launchUi(binding.progress, binding.btnSave) {
            if (isNew) AuthRepository().createUserAsAdmin(user, password) else users.update(user)
            toast(R.string.msg_saved)
            finish()
        }
    }

    private fun delete() {
        val user = editing ?: return
        if (user.uid == Session.user?.uid) {
            toast(R.string.error_delete_self)
            return
        }
        confirm(R.string.delete_user_title, getString(R.string.delete_user_message, user.name)) {
            launchUi(binding.progress) {
                users.delete(user.uid)
                toast(R.string.msg_deleted)
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_UID = "uid"
    }
}
