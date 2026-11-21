package com.tiendavirtual.app.ui.common

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.util.BiometricAuth

/**
 * Después de un registro o de un ingreso con contraseña, ofrece activar el
 * inicio de sesión con huella y luego abre el panel del rol.
 *
 * No se ofrece si el teléfono no tiene sensor (o huellas registradas) o si
 * la huella ya está activada para ese mismo correo.
 */
object BiometricOffer {

    fun offerThenGoHome(activity: FragmentActivity, email: String, password: String, role: Role) {
        val biometric = BiometricAuth(activity)
        if (!biometric.isAvailable() || biometric.enabledEmail() == email) {
            Navigator.goHome(activity, role)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_message)
            .setCancelable(false)
            .setNegativeButton(R.string.not_now) { _, _ -> Navigator.goHome(activity, role) }
            .setPositiveButton(R.string.activate) { _, _ ->
                biometric.enable(email, password) { enabled ->
                    Toast.makeText(
                        activity,
                        if (enabled) R.string.biometric_enabled else R.string.biometric_not_activated,
                        Toast.LENGTH_LONG
                    ).show()
                    Navigator.goHome(activity, role)
                }
            }
            .show()
    }
}
