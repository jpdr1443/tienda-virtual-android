package com.tiendavirtual.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityAdminUsersBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.UserAdapter
import com.tiendavirtual.app.util.Session

/** CRUD de usuarios (lectura y eliminación); crear/editar se hace en [AdminUserFormActivity]. */
class AdminUsersActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminUsersBinding
    private val repo = UserRepository()
    private var allUsers: List<User> = emptyList()
    private val adapter = UserAdapter(onEdit = ::openForm, onDelete = ::confirmDelete)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.admin_users_title)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnNew.setOnClickListener { openForm(null) }
        binding.inputSearch.doAfterTextChanged { applyFilter() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = launchUi(binding.progress) {
        allUsers = repo.getAll()
        applyFilter()
    }

    /** Filtra por nombre, correo o rol mientras se escribe en el buscador. */
    private fun applyFilter() {
        val query = binding.inputSearch.text?.toString().orEmpty().trim().lowercase()
        val list = if (query.isEmpty()) allUsers else allUsers.filter {
            it.name.lowercase().contains(query) || it.email.lowercase().contains(query) ||
                getString(it.roleEnum.label).lowercase().contains(query)
        }
        adapter.submit(list)
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.textCount.text = getString(R.string.users_count, list.size)
    }

    private fun openForm(user: User?) {
        startActivity(Intent(this, AdminUserFormActivity::class.java)
            .putExtra(AdminUserFormActivity.EXTRA_UID, user?.uid))
    }

    private fun confirmDelete(user: User) {
        if (user.uid == Session.user?.uid) {
            toast(R.string.error_delete_self)
            return
        }
        confirm(R.string.delete_user_title, getString(R.string.delete_user_message, user.name)) {
            launchUi(binding.progress) {
                repo.delete(user.uid)
                toast(R.string.msg_deleted)
                load()
            }
        }
    }
}
