package fr.projo.launcher.remote

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code (bibliothèque zxing, partie « encodeur » seulement après R8). */
object QrCode {

    /** Modules du QR code, sans marge : true = noir. La marge blanche est dessinée par l'interface. */
    fun matrice(texte: String): Array<BooleanArray> {
        val bits = QRCodeWriter().encode(
            texte, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        return Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits.get(x, y) } }
    }
}
