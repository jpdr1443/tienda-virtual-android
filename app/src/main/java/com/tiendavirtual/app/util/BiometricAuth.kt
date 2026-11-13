package com.tiendavirtual.app.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tiendavirtual.app.R
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Inicio de sesión con huella dactilar.
 *
 * Cómo funciona:
 * 1. Al activar la huella se genera en el Android Keystore una llave AES que
 *    solo se puede usar después de una autenticación biométrica fuerte.
 * 2. El correo y la contraseña se cifran con esa llave dentro de BiometricPrompt
 *    (CryptoObject) y solo el texto cifrado se guarda en SharedPreferences.
 * 3. Al ingresar con huella, BiometricPrompt desbloquea la llave, se descifran
 *    las credenciales y se inicia sesión en Firebase con ellas.
 *
 * Así la contraseña nunca queda en texto plano en el teléfono, y si se
 * registra una huella nueva la llave se invalida automáticamente.
 */
class BiometricAuth(private val activity: FragmentActivity) {

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** true si el teléfono tiene sensor y al menos una huella registrada. */
    fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** true si el usuario ya activó el ingreso con huella en este dispositivo. */
    fun isEnabled(): Boolean = prefs.contains(KEY_DATA) && prefs.contains(KEY_IV)

    /** Correo asociado a la huella, para mostrarlo en el botón del login. */
    fun enabledEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Borra las credenciales cifradas y la llave del Keystore. */
    fun disable() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /**
     * Pide la huella y, si es correcta, cifra y guarda las credenciales.
     * @param onResult true si quedó activada; false si se canceló o falló.
     */
    fun enable(email: String, password: String, onResult: (Boolean) -> Unit) {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // La llave anterior quedó inválida (cambiaron las huellas): se regenera.
            disable()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        }

        authenticate(cipher, R.string.biometric_enable_title, { unlocked ->
            val json = JSONObject().put("e", email).put("p", password).toString()
            val encrypted = unlocked.doFinal(json.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(unlocked.iv, Base64.NO_WRAP))
                .putString(KEY_EMAIL, email)
                .apply()
            onResult(true)
        }, { onResult(false) })
    }

    /**
     * Pide la huella y entrega el correo y la contraseña descifrados.
     * @param onError recibe el mensaje a mostrar si no se pudo autenticar.
     */
    fun login(onCredentials: (email: String, password: String) -> Unit, onError: (String) -> Unit) {
        val data = prefs.getString(KEY_DATA, null)
        val iv = prefs.getString(KEY_IV, null)
        if (data == null || iv == null) {
            onError(activity.getString(R.string.biometric_not_enabled))
            return
        }

        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            onError(activity.getString(R.string.biometric_invalidated))
            return
        }

        authenticate(cipher, R.string.biometric_title, { unlocked ->
            val json = JSONObject(String(unlocked.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8))
            onCredentials(json.getString("e"), json.getString("p"))
        }, { message -> onError(message) })
    }

    /** Muestra el diálogo del sistema y devuelve el Cipher desbloqueado por la huella. */
    private fun authenticate(
        cipher: Cipher,
        titleRes: Int,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val unlocked = result.cryptoObject?.cipher
                if (unlocked == null) onError(activity.getString(R.string.biometric_failed))
                else runCatching { onSuccess(unlocked) }
                    .onFailure { onError(activity.getString(R.string.biometric_failed)) }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(titleRes))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Obtiene la llave del Keystore o la crea exigiendo autenticación biométrica para usarla. */
    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tienda_virtual_biometric"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "biometric_login"
        const val KEY_DATA = "data"
        const val KEY_IV = "iv"
        const val KEY_EMAIL = "email"
    }
}
