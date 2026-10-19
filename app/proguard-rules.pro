# Bibliothèque ADB (dadb) et ses dépendances : certaines classes visent le PC (serveur adb)
-dontwarn dadb.**
-dontwarn okio.**
-keep class dadb.** { *; }
