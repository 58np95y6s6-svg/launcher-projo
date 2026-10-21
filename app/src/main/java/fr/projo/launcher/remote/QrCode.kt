package fr.projo.launcher.remote

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code (bibliothèque zxing, partie « encodeur » seulement après R8). */
object QrCode {

    /** Zone de silence obligatoire autour du QR code, en modules (norme ISO 18004). */
    const val ZONE_DE_SILENCE = 4

    /**
     * Modules du QR code, sans marge : true = noir. La marge blanche (zone de silence) est
     * dessinée par l'interface.
     *
     * [niveau] : correction d'erreur. M (15 %) pour le grand dialogue. Le module de l'accueil
     * prend L (7 %) : un écran n'a ni salissure ni logo à reconstituer, et à 3 m c'est la taille
     * des modules qui compte. L'URL d'appairage (141 caractères) tient alors en version 7
     * (45 × 45 modules) au lieu de 8 (49 × 49) : modules 9 % plus grands à taille égale.
     */
    fun matrice(texte: String, niveau: ErrorCorrectionLevel = ErrorCorrectionLevel.M): Array<BooleanArray> {
        val bits = QRCodeWriter().encode(
            texte, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to niveau,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        return Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits.get(x, y) } }
    }
}
