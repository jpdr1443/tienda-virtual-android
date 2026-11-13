package com.tiendavirtual.app.util

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tiendavirtual.app.R

/**
 * Error de negocio con un mensaje pensado para el usuario final
 * (por ejemplo "cuenta desactivada" o "stock insuficiente").
 */
class AppException(val messageRes: Int, val arg: String? = null) : Exception()

/**
 * Traduce cualquier excepción (Firebase, red, negocio) a un texto en español
 * que se pueda mostrar en un Toast o diálogo, sin detalles técnicos.
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is AppException ->
        if (arg != null) context.getString(messageRes, arg) else context.getString(messageRes)
    is FirebaseAuthWeakPasswordException -> context.getString(R.string.error_password)
    is FirebaseAuthUserCollisionException -> context.getString(R.string.error_email_in_use)
    is FirebaseAuthInvalidUserException -> context.getString(R.string.error_user_not_found)
    is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.error_bad_credentials)
    is FirebaseNetworkException -> context.getString(R.string.error_network)
    is FirebaseFirestoreException ->
        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            context.getString(R.string.error_permission)
        else context.getString(R.string.error_generic, localizedMessage ?: "")
    else -> context.getString(R.string.error_generic, localizedMessage ?: "")
}
