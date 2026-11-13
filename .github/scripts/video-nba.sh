#!/usr/bin/env bash
# Vidéo de présentation du design NBA : la vraie appli (version optimisée) dans l'émulateur
# Android TV, avec les vraies données ESPN. Une capture PNG à chaque étape du parcours à la
# télécommande (l'émulateur dessine trop lentement pour filmer en direct : le montage se fait
# à partir des captures), plus un enregistrement d'écran de secours. Résultats dans « video/ ».
set -u
P=fr.projo.launcher
S=video
mkdir -p "$S"
N=0

k() { adb shell input keyevent "$1"; }
# Capture numérotée après une attente (secondes)
cap() { sleep "${2:-1.6}"; N=$((N + 1)); adb exec-out screencap -p > "$S/$(printf '%02d' $N)-$1.png"; echo "capture $N $1"; }

# Accès root (images d'émulateur de développement) : fuseau horaire et préférences de l'appli
adb root > /dev/null 2>&1 || true
sleep 4
adb wait-for-device
ROOT=0
[ "$(adb shell id -u | tr -d '\r')" = "0" ] && ROOT=1
echo "root: $ROOT"
if [ "$ROOT" = 1 ]; then
  APK=app/build/outputs/apk/release/app-release.apk
  adb shell setprop persist.sys.timezone Europe/Paris
else
  # Sans root : version de débogage, préférences écrites avec run-as
  APK=app/build/outputs/apk/debug/app-debug.apk
fi
adb shell settings put global auto_time_zone 0 || true
adb shell service call alarm 3 s16 Europe/Paris > /dev/null 2>&1 || true

adb install -r -g "$APK" || { echo "installation impossible"; exit 1; }

# Favoris : les applis de l'émulateur (écran d'accueil TV), sauf iBiTV et les réglages
FAV=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER \
  | tr -d '\r' | grep '/' | cut -d/ -f1 | sed 's/^ *//' | grep -v -E "^$P$|settings|tvlauncher|setupwraith|provision" | sort -u)
echo "favoris: $FAV"
python3 .github/scripts/video-nba.py "$S/projo.preferences_pb" $FAV

ecrire_prefs() {
  local ambiance=$1
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <string name="ambiance">%s</string>\n</map>\n' "$ambiance" > "$S/nba_carte.xml"
  adb push "$S/projo.preferences_pb" /data/local/tmp/projo.preferences_pb > /dev/null
  adb push "$S/nba_carte.xml" /data/local/tmp/nba_carte.xml > /dev/null
  adb shell chmod 644 /data/local/tmp/projo.preferences_pb /data/local/tmp/nba_carte.xml
  adb shell am force-stop "$P"
  if [ "$ROOT" = 1 ]; then
    local u
    u=$(adb shell stat -c %u "/data/data/$P" | tr -d '\r')
    adb shell "mkdir -p /data/data/$P/files/datastore /data/data/$P/shared_prefs \
      && cp /data/local/tmp/projo.preferences_pb /data/data/$P/files/datastore/ \
      && cp /data/local/tmp/nba_carte.xml /data/data/$P/shared_prefs/ \
      && chown -R $u:$u /data/data/$P/files /data/data/$P/shared_prefs \
      && restorecon -R /data/data/$P"
  else
    adb shell "run-as $P sh -c 'mkdir -p files/datastore shared_prefs && cp /data/local/tmp/projo.preferences_pb files/datastore/ && cp /data/local/tmp/nba_carte.xml shared_prefs/'"
  fi
}

ouvrir() { adb shell am start -W -n "$P/.MainActivity" > /dev/null; }

ecrire_prefs leaders
ouvrir
cap accueil-10s 10
cap accueil-25s 15
cap accueil-45s 20

# Parcours : Spurs (fiche, effectif), Lakers (fiche, effectif), la côte Est jusqu'à Boston
k 19; cap entree
k 23; cap fiche-1 2.5; cap fiche-1b 3
k 22; cap effectif-1 2.5
k 4; cap retour-1
for t in 19 19 21 21 19; do k $t; cap pas; done
k 23; cap fiche-2 2.5; cap fiche-2b 3
k 22; cap effectif-2 2.5
k 4; cap retour-2
for t in 19 22 22 22 22 22 22 19 22; do k $t; cap pas; done
k 23; cap fiche-3 2.5; cap fiche-3b 3
k 22; cap effectif-3 2.5
k 4; cap retour-3
k 4; cap dock
k 22; cap dock-2
k 22; cap dock-3
k 21; k 21; cap dock-4

# Enregistrement d'écran de secours (parcours rapide), en tâche de fond
adb shell screenrecord --bit-rate 16000000 --time-limit 60 /sdcard/parcours.mp4 &
REC=$!
sleep 2
k 19; sleep 1.2
for t in 23 22 4 19 19 21 21 19 23 4 19 22 22 22 22; do k $t; sleep 1.2; done
k 4; sleep 2
adb shell pkill -INT screenrecord || true
wait $REC 2>/dev/null || true
sleep 2
adb pull /sdcard/parcours.mp4 "$S/parcours.mp4" > /dev/null 2>&1 || echo "pas d'enregistrement d'écran"

# Les deux autres ambiances
ecrire_prefs parquet
ouvrir
cap parquet 15
k 19; cap parquet-entree
k 23; cap parquet-fiche 4
ecrire_prefs nuit
ouvrir
cap nuit 15
k 19; cap nuit-entree
ecrire_prefs leaders
ouvrir
cap leaders-fin 15

adb logcat -d > "$S/logcat.txt"
adb shell date > "$S/date.txt"
rm -f "$S/projo.preferences_pb" "$S/nba_carte.xml"
if grep -A 2 "FATAL EXCEPTION" "$S/logcat.txt" | grep -q "Process: $P"; then
  echo "plantage :"; grep -A 30 "FATAL EXCEPTION" "$S/logcat.txt" | head -40
fi
ls -la "$S"
