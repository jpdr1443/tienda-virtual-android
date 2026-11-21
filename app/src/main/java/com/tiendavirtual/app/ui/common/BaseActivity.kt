package com.tiendavirtual.app.ui.common

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiendavirtual.app.R
import com.tiendavirtual.app.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Activity base de la app. Centraliza tres cosas que repiten todas las pantallas:
 * configurar la barra superior, ejecutar operaciones de Firebase en corrutinas
 * mostrando un indicador de carga, y mostrar mensajes / confirmaciones.
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Pone el título en la barra superior y, si [showBack], la flecha de regreso.
     * En las pantallas principales ([showLogout]) agrega el botón de cerrar sesión,
     * para poder cambiar de usuario desde cualquier rol.
     */
    protected fun setupToolbar(
        toolbar: MaterialToolbar,
        title: Int,
        showBack: Boolean = true,
        showLogout: Boolean = !showBack
    ) {
        toolbar.setTitle(title)
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { finish() }
        } else {
            toolbar.navigationIcon = null
        }
        toolbar.menu.clear()
        if (showLogout) {
            toolbar.inflateMenu(R.menu.menu_toolbar_session)
            toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_logout) {
                    confirm(R.string.logout_title, getString(R.string.logout_message), R.string.btn_logout) {
                        Navigator.logout(this)
                    }
                    true
                } else false
            }
        }
    }

    /**
     * Ejecuta [block] en el ciclo de vida de la Activity (se cancela si la
     * pantalla se cierra). Mientras corre muestra [progress] y deshabilita
     * [disable]; si ocurre un error lo traduce y lo muestra en un Toast.
     */
    protected fun launchUi(
        progress: View? = null,
        vararg disable: View,
        block: suspend () -> Unit
    ): Job = lifecycleScope.launch {
        progress?.visibility = View.VISIBLE
        disable.forEach { it.isEnabled = false }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(e.toUserMessage(this@BaseActivity))
        } finally {
            progress?.visibility = View.GONE
            disable.forEach { it.isEnabled = true }
        }
    }

    protected fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    protected fun toast(messageRes: Int) = toast(getString(messageRes))

    /** Diálogo de confirmación para acciones destructivas (eliminar, etc.). */
    protected fun confirm(title: Int, message: String, positive: Int = R.string.delete, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .show()
    }
}
