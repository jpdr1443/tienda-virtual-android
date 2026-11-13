package com.tiendavirtual.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivitySplashBinding
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.util.BiometricAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de presentación. Mientras se muestra decide a dónde ir:
 *  - Sin sesión, o con huella activada (se pide la huella como desbloqueo) → Login.
 *  - Con sesión vigente y perfil activo → inicio del rol correspondiente.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)

        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            val hasSession = FirebaseAuth.getInstance().currentUser != null
            val biometric = BiometricAuth(this@SplashActivity)

            if (hasSession && !biometric.isEnabled()) {
                // Sesión persistida por Firebase: se recarga el perfil para conocer el rol.
                val user = runCatching { UserRepository().currentProfile(forceRefresh = true) }.getOrNull()
                if (user != null && user.active) {
                    Navigator.goHome(this@SplashActivity, user.roleEnum)
                    return@launch
                }
                FirebaseAuth.getInstance().signOut()
            }
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
}
