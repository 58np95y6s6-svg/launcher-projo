package fr.projo.launcher.modules.minuteur

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.projo.launcher.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Étape du minuteur. */
enum class ModeMinuteur { ARRET, EN_COURS, PAUSE, SONNE }

/**
 * État du minuteur, enregistré dans les préférences de l'app : il survit à la fermeture du
 * launcher (l'échéance est confiée à AlarmManager, voir [Minuteur]).
 */
data class EtatMinuteur(
    val mode: ModeMinuteur = ModeMinuteur.ARRET,
    /** true : réveil à une heure donnée ; false : compte à rebours. */
    val reveil: Boolean = false,
    /** EN_COURS : échéance (ms, horloge murale) ; SONNE : échéance qui vient de sonner. */
    val fin: Long = 0L,
    /** PAUSE : temps restant (ms). */
    val restant: Long = 0L,
    /** Durée totale du compte à rebours (barre de progression), ms. */
    val duree: Long = 0L,
    /** Durée réglée (minutes), proposée quand le minuteur est arrêté. */
    val minutesChoisies: Int = 15,
    /** Heure du réveil, en minutes depuis minuit. */
    val heureReveil: Int = 7 * 60,
) {
    /** Temps restant à [maintenant] (0 si arrêté ou échu). */
    fun restantA(maintenant: Long): Long = when (mode) {
        ModeMinuteur.EN_COURS -> (fin - maintenant).coerceAtLeast(0L)
        ModeMinuteur.PAUSE -> restant.coerceAtLeast(0L)
        else -> 0L
    }
}

/**
 * Minuteur et réveil : état persistant, échéance par AlarmManager, notifications.
 *  - compte à rebours : notification discrète et permanente avec le décompte (visible
 *    depuis les autres apps, dans le panneau des notifications) ;
 *  - à l'échéance ([MinuteurReceiver]) : notification sonore sur le canal d'alarme et écran
 *    plein écran [AlarmeActivity] par-dessus l'app en cours, avec « Arrêter ».
 * Toutes les opérations sont rapides (préférences + AlarmManager) : appelables depuis
 * l'interface comme depuis le récepteur.
 */
object Minuteur {

    private const val TAG = "ProjoMinuteur"

    const val ACTION_ECHEANCE = "fr.projo.launcher.minuteur.ECHEANCE"
    const val ACTION_ARRETER = "fr.projo.launcher.minuteur.ARRETER"
    const val ACTION_ANNULER = "fr.projo.launcher.minuteur.ANNULER"
    const val ACTION_REPETER = "fr.projo.launcher.minuteur.REPETER"

    const val CANAL_ALARME = "minuteur_alarme"
    const val CANAL_DECOMPTE = "minuteur_decompte"
    const val NOTIF_DECOMPTE = 4101
    const val NOTIF_ALARME = 4102

    /** Durée maximale d'un compte à rebours (minutes). */
    const val MINUTES_MAX = 240

    /** Une échéance dépassée de plus que ça (appareil éteint…) ne sonne plus. */
    private const val RETARD_MAX = 10 * 60_000L

    /** Durée de la sonnerie avant arrêt automatique. */
    const val DUREE_SONNERIE = 5 * 60_000L

    private const val FICHIER = "projo_minuteur"

    private var prefs: SharedPreferences? = null
    private val etat = MutableStateFlow(EtatMinuteur())

    /** État observable (chargé depuis les préférences au premier appel). */
    fun flux(context: Context): StateFlow<EtatMinuteur> {
        charger(context)
        return etat
    }

    fun etat(context: Context): EtatMinuteur {
        charger(context)
        return etat.value
    }

    @Synchronized
    private fun charger(context: Context): SharedPreferences {
        val p = context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        // Nouvelle instance (premier appel, ou tests) : on relit l'état enregistré
        if (p !== prefs) {
            prefs = p
            etat.value = lire(p)
        }
        return p
    }

    private fun lire(p: SharedPreferences): EtatMinuteur = try {
        EtatMinuteur(
            mode = ModeMinuteur.entries.firstOrNull { it.name == p.getString("mode", null) } ?: ModeMinuteur.ARRET,
            reveil = p.getBoolean("reveil", false),
            fin = p.getLong("fin", 0L),
            restant = p.getLong("restant", 0L),
            duree = p.getLong("duree", 0L),
            minutesChoisies = p.getInt("minutes", 15).coerceIn(1, MINUTES_MAX),
            heureReveil = p.getInt("heure_reveil", 7 * 60).coerceIn(0, 24 * 60 - 1),
        )
    } catch (e: Exception) {
        Log.w(TAG, "État du minuteur illisible : remis à zéro", e)
        EtatMinuteur()
    }

    /** Enregistre [e] (écriture immédiate : le récepteur peut tourner dans un processus éphémère). */
    @Synchronized
    internal fun ecrire(context: Context, e: EtatMinuteur) {
        val p = charger(context)
        try {
            p.edit()
                .putString("mode", e.mode.name)
                .putBoolean("reveil", e.reveil)
                .putLong("fin", e.fin)
                .putLong("restant", e.restant)
                .putLong("duree", e.duree)
                .putInt("minutes", e.minutesChoisies)
                .putInt("heure_reveil", e.heureReveil)
                .commit()
        } catch (ex: Exception) {
            Log.w(TAG, "Enregistrement du minuteur impossible", ex)
        }
        etat.value = e
    }

    /** Oublie l'état en mémoire (tests : simule un redémarrage du launcher). */
    @Synchronized
    internal fun oublier() {
        prefs = null
        etat.value = EtatMinuteur()
    }

    private fun maintenant() = System.currentTimeMillis()

    // ------------------------------------------------------------------ opérations

    /** Lance un compte à rebours de [minutes]. */
    fun demarrer(context: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MINUTES_MAX)
        val d = m * 60_000L
        val e = etat(context).copy(
            mode = ModeMinuteur.EN_COURS, reveil = false, fin = maintenant() + d, restant = 0L,
            duree = d, minutesChoisies = m,
        )
        ecrire(context, e)
        annulerNotification(context, NOTIF_ALARME)
        programmer(context, e.fin)
        notifierDecompte(context, e)
    }

    /** Durée proposée à l'arrêt (▲▼ sur l'afficheur). */
    fun regler(context: Context, minutes: Int) {
        val e = etat(context)
        if (e.mode == ModeMinuteur.ARRET) ecrire(context, e.copy(minutesChoisies = minutes.coerceIn(1, MINUTES_MAX)))
    }

    /** Heure du réveil proposée (minutes depuis minuit). */
    fun reglerReveil(context: Context, minutesDuJour: Int) {
        val e = etat(context)
        val h = ((minutesDuJour % (24 * 60)) + 24 * 60) % (24 * 60)
        if (e.mode == ModeMinuteur.ARRET) ecrire(context, e.copy(heureReveil = h))
    }

    /** Arme le réveil à [minutesDuJour] (aujourd'hui, ou demain si l'heure est passée). */
    fun armerReveil(context: Context, minutesDuJour: Int) {
        val h = ((minutesDuJour % (24 * 60)) + 24 * 60) % (24 * 60)
        val fin = prochaineOccurrence(h, maintenant())
        val e = etat(context).copy(
            mode = ModeMinuteur.EN_COURS, reveil = true, fin = fin, restant = 0L,
            duree = fin - maintenant(), heureReveil = h,
        )
        ecrire(context, e)
        annulerNotification(context, NOTIF_ALARME)
        programmer(context, fin)
        notifierDecompte(context, e)
    }

    fun pause(context: Context) {
        val e = etat(context)
        if (e.mode != ModeMinuteur.EN_COURS || e.reveil) return
        val p = e.copy(mode = ModeMinuteur.PAUSE, restant = (e.fin - maintenant()).coerceAtLeast(1000L))
        ecrire(context, p)
        annulerProgrammation(context)
        notifierDecompte(context, p)
    }

    fun reprendre(context: Context) {
        val e = etat(context)
        if (e.mode != ModeMinuteur.PAUSE) return
        val r = e.copy(mode = ModeMinuteur.EN_COURS, fin = maintenant() + e.restant, restant = 0L)
        ecrire(context, r)
        programmer(context, r.fin)
        notifierDecompte(context, r)
    }

    /** Ajoute (ou retire, si négatif) du temps au compte à rebours ; au moins 10 s restent. */
    fun ajouter(context: Context, ms: Long) {
        val e = etat(context)
        if (e.reveil) return
        when (e.mode) {
            ModeMinuteur.EN_COURS -> {
                val reste = e.fin - maintenant()
                val nouveau = (reste + ms).coerceIn(10_000L, MINUTES_MAX * 60_000L)
                val n = e.copy(fin = maintenant() + nouveau, duree = (e.duree + (nouveau - reste)).coerceAtLeast(nouveau))
                ecrire(context, n)
                programmer(context, n.fin)
                notifierDecompte(context, n)
            }
            ModeMinuteur.PAUSE -> {
                val nouveau = (e.restant + ms).coerceIn(10_000L, MINUTES_MAX * 60_000L)
                val n = e.copy(restant = nouveau, duree = (e.duree + (nouveau - e.restant)).coerceAtLeast(nouveau))
                ecrire(context, n)
                notifierDecompte(context, n)
            }
            else -> Unit
        }
    }

    /** Annule le compte à rebours ou le réveil (sans sonner). */
    fun annuler(context: Context) {
        val e = etat(context)
        ecrire(context, e.copy(mode = ModeMinuteur.ARRET, fin = 0L, restant = 0L, duree = 0L))
        annulerProgrammation(context)
        annulerNotification(context, NOTIF_DECOMPTE)
        annulerNotification(context, NOTIF_ALARME)
    }

    /** Arrête l'alarme qui sonne. */
    fun arreter(context: Context) {
        val e = etat(context)
        if (e.mode == ModeMinuteur.SONNE) {
            ecrire(context, e.copy(mode = ModeMinuteur.ARRET, fin = 0L, restant = 0L, duree = 0L))
        }
        annulerNotification(context, NOTIF_ALARME)
    }

    /** Alarme qui sonne : « encore [minutes] min ». */
    fun repeter(context: Context, minutes: Int) {
        val e = etat(context)
        val choisies = e.minutesChoisies
        demarrer(context, minutes)
        // La durée réglée par l'utilisateur reste celle qu'il avait choisie
        ecrire(context, etat(context).copy(minutesChoisies = choisies))
    }

    /** Alarme qui sonne : relance la même durée (minuteur) ou le même réveil demain. */
    fun relancer(context: Context) {
        val e = etat(context)
        if (e.reveil) armerReveil(context, e.heureReveil) else demarrer(context, e.minutesChoisies)
    }

    /**
     * Échéance atteinte (AlarmManager, ou l'afficheur du module s'il est à l'écran) : passe en
     * SONNE et alerte. Sans effet si le minuteur a été annulé ou repoussé entre-temps.
     * [forcer] : sonne même avant l'échéance (tests).
     * @return true si l'alarme a été déclenchée.
     */
    fun echeance(context: Context, forcer: Boolean = false): Boolean {
        val e: EtatMinuteur
        synchronized(this) {
            val actuel = etat(context)
            if (actuel.mode != ModeMinuteur.EN_COURS) return false
            if (!forcer && actuel.fin > maintenant() + 3000L) {
                // Alarme en avance (horloge modifiée…) : on reprogramme
                programmer(context, actuel.fin)
                return false
            }
            e = actuel.copy(mode = ModeMinuteur.SONNE)
            ecrire(context, e)
        }
        annulerNotification(context, NOTIF_DECOMPTE)
        sonner(context, e)
        return true
    }

    /**
     * À l'affichage du module (et donc au démarrage du launcher) : ré-arme l'échéance (perdue
     * après un redémarrage de l'appareil), ou constate une échéance passée.
     */
    fun verifier(context: Context) {
        val e = etat(context)
        val t = maintenant()
        when (e.mode) {
            ModeMinuteur.EN_COURS -> when {
                e.fin <= t - RETARD_MAX -> annuler(context)
                e.fin <= t -> echeance(context)
                else -> programmer(context, e.fin)
            }
            ModeMinuteur.SONNE -> if (e.fin <= t - RETARD_MAX) arreter(context)
            else -> Unit
        }
    }

    /** Prochaine fois qu'il sera [minutesDuJour] (heure locale), au moins 30 s après [depuis]. */
    fun prochaineOccurrence(minutesDuJour: Int, depuis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = depuis
            set(Calendar.HOUR_OF_DAY, minutesDuJour / 60)
            set(Calendar.MINUTE, minutesDuJour % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= depuis + 30_000L) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    // ------------------------------------------------------------------ AlarmManager

    private fun drapeauxImmuables(): Int =
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun intentionRecepteur(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext, code,
            Intent(context.applicationContext, MinuteurReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or drapeauxImmuables(),
        )

    /**
     * Échéance confiée à AlarmManager (réveille l'appareil en veille) : exacte si possible
     * (setExactAndAllowWhileIdle), sinon inexacte (Android 12+ sans SCHEDULE_EXACT_ALARM).
     */
    private fun programmer(context: Context, fin: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = intentionRecepteur(context, ACTION_ECHEANCE, 1)
        try {
            when {
                Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, pi)
                Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, pi)
                else -> am.setExact(AlarmManager.RTC_WAKEUP, fin, pi)
            }
        } catch (e: SecurityException) {
            // Permission d'alarme exacte retirée entre-temps : repli inexact
            runCatching { am.set(AlarmManager.RTC_WAKEUP, fin, pi) }
        } catch (e: Exception) {
            Log.w(TAG, "Programmation de l'alarme impossible", e)
        }
    }

    private fun annulerProgrammation(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(intentionRecepteur(context, ACTION_ECHEANCE, 1)) }
    }

    // ------------------------------------------------------------------ notifications

    private fun sonAlarme(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** Canaux (Android 8+) : décompte silencieux, alarme sonore. */
    private fun creerCanaux(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.createNotificationChannel(
                NotificationChannel(CANAL_DECOMPTE, "Décompte du minuteur", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Temps restant, pendant que le minuteur tourne"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CANAL_ALARME, "Alarme du minuteur et du réveil", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sonne à la fin du minuteur ou à l'heure du réveil"
                    setSound(
                        sonAlarme(),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(false)
                    setBypassDnd(true)
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Canaux de notification impossibles", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifier(context: Context, id: Int, n: Notification) {
        try {
            val nm = NotificationManagerCompat.from(context)
            if (nm.areNotificationsEnabled()) nm.notify(id, n)
        } catch (e: SecurityException) {
            // Android 13+ sans POST_NOTIFICATIONS : l'écran d'alarme suffit
            Log.w(TAG, "Notification refusée", e)
        } catch (e: Exception) {
            Log.w(TAG, "Notification impossible", e)
        }
    }

    private fun annulerNotification(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun heure(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

    /** Re-publie la notification de décompte (ex. après l'accord de la permission). */
    fun rafraichirNotification(context: Context) {
        val e = etat(context)
        if (e.mode == ModeMinuteur.EN_COURS || e.mode == ModeMinuteur.PAUSE) notifierDecompte(context, e)
    }

    /** Notification permanente avec le décompte : visible depuis n'importe quelle app. */
    private fun notifierDecompte(context: Context, e: EtatMinuteur) {
        creerCanaux(context)
        val ouvrir = PendingIntent.getActivity(
            context.applicationContext, 3,
            Intent(context.applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or drapeauxImmuables(),
        )
        val b = NotificationCompat.Builder(context, CANAL_DECOMPTE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(ouvrir)
            .addAction(0, "Annuler", intentionRecepteur(context, ACTION_ANNULER, 4))
        if (e.reveil) {
            b.setContentTitle("Réveil à " + heure(e.fin))
                .setContentText("Projo sonnera à " + heure(e.fin))
                .setWhen(e.fin).setShowWhen(true)
        } else if (e.mode == ModeMinuteur.PAUSE) {
            b.setContentTitle("Minuteur en pause")
                .setContentText(formaterDuree(e.restant) + " restantes")
                .setShowWhen(false)
        } else {
            b.setContentTitle("Minuteur · fin à " + heure(e.fin))
                .setContentText("Compte à rebours de " + formaterMinutes((e.duree / 60_000L).toInt()))
                .setWhen(e.fin).setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        notifier(context, NOTIF_DECOMPTE, b.build())
    }

    /** Échéance : notification sonore (canal d'alarme) + écran plein écran par-dessus. */
    private fun sonner(context: Context, e: EtatMinuteur) {
        creerCanaux(context)
        val app = context.applicationContext
        val ecran = Intent(app, AlarmeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pleinEcran = PendingIntent.getActivity(app, 2, ecran, PendingIntent.FLAG_UPDATE_CURRENT or drapeauxImmuables())
        val titre = if (e.reveil) "Réveil · " + heure(e.fin) else "Temps écoulé"
        val texte = if (e.reveil) "Il est " + heure(e.fin) else "Minuteur de " + formaterMinutes((e.duree / 60_000L).toInt().coerceAtLeast(1)) + " terminé"
        @Suppress("DEPRECATION")
        val n = NotificationCompat.Builder(context, CANAL_ALARME)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titre)
            .setContentText(texte)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pleinEcran)
            .setFullScreenIntent(pleinEcran, true)
            .setSound(sonAlarme(), AudioManager.STREAM_ALARM)
            .setTimeoutAfter(DUREE_SONNERIE)
            .addAction(0, "Arrêter", intentionRecepteur(context, ACTION_ARRETER, 5))
            .addAction(0, "+5 min", intentionRecepteur(context, ACTION_REPETER, 6))
            .build()
        // Son répété jusqu'à « Arrêter » (Android < 8 : son de la notification elle-même)
        n.flags = n.flags or Notification.FLAG_INSISTENT
        notifier(context, NOTIF_ALARME, n)
        // Écran d'alarme par-dessus l'app en cours (autorisé pour l'écran d'accueil par défaut ;
        // sinon la notification plein écran prend le relais)
        try {
            app.startActivity(ecran)
        } catch (ex: Exception) {
            Log.w(TAG, "Écran d'alarme impossible à ouvrir", ex)
        }
    }

    // ------------------------------------------------------------------ textes

    /** 754 000 ms → « 12:34 » ; 3 723 000 → « 1:02:03 ». */
    fun formaterDuree(ms: Long): String {
        val s = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.FRENCH, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.FRENCH, "%02d:%02d", m, sec)
    }

    /** 90 → « 1 h 30 », 60 → « 1 h », 15 → « 15 min ». */
    fun formaterMinutes(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> String.format(Locale.FRENCH, "%d h %02d", minutes / 60, minutes % 60)
    }

    /** « dans 9 h 12 », « dans 25 min ». */
    fun formaterDans(ms: Long): String {
        val min = ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
        return if (min < 60) "dans $min min" else String.format(Locale.FRENCH, "dans %d h %02d", min / 60, min % 60)
    }
}
