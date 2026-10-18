package fr.projo.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.ui.DiagnosticScreen

/**
 * Activité unique du launcher : c'est elle que le bouton Maison ouvre.
 * Étape 1 : écran de diagnostic (liste des apps + état « écran d'accueil »).
 */
class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(applicationContext)

        // Le bouton Retour ne doit jamais quitter l'écran d'accueil.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* on reste sur l'accueil */ }
        })

        setContent {
            DiagnosticScreen(
                repo = repo,
                ourPackage = packageName,
                onOpenHomeSettings = ::ouvrirChoixAccueil,
            )
        }
    }

    /** Ouvre l'écran système « Application d'accueil », sinon les réglages généraux. */
    private fun ouvrirChoixAccueil() {
        val essais = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in essais) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                Log.w(AppRepository.TAG, "Indisponible : ${intent.action}")
            }
        }
    }
}
