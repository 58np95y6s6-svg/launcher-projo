# Télécommande smartphone : règles R8 (build release).

# WebRTC natif : le code C++ appelle des classes et méthodes Java par leur nom (JNI).
# La bibliothèque fournit déjà « -keep class org.webrtc.** » ; on le répète pour ne pas
# dépendre de ses règles, et on fait taire les avertissements de ses classes facultatives.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp : fournisseurs TLS facultatifs (Conscrypt, BouncyCastle, OpenJSSE) absents de l'appli.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# zxing : seul l'encodeur QR est utilisé, le reste est retiré par R8.
-dontwarn com.google.zxing.**
