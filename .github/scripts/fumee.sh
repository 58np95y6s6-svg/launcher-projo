#!/usr/bin/env bash
# Test de démarrage dans un émulateur Android TV (lancé par le job « fumee » de build.yml).
# Pour la version de débogage puis la version optimisée (release, R8) : installation, ouverture,
# navigation à la télécommande, puis vérifications (processus vivant, aucun plantage) et mesures
# (temps de démarrage, images de la navigation). Résultats dans le dossier « fumee/ ».
set -u
PAQUET=fr.projo.launcher
SORTIE=fumee
mkdir -p "$SORTIE"
ECHEC=0

touches() { for k in "$@"; do adb shell input keyevent "$k"; sleep 0.35; done; }

essai() {
  local nom=$1 apk=$2
  echo "=== $nom ==="
  adb uninstall "$PAQUET" > /dev/null 2>&1 || true
  if ! adb install -r -g "$apk" > "$SORTIE/$nom-installation.txt" 2>&1; then
    echo "ECHEC $nom : installation"; cat "$SORTIE/$nom-installation.txt"; ECHEC=1; return
  fi
  adb logcat -c
  # Démarrage à froid, mesuré par le système (TotalTime en ms)
  adb shell am start -W -n "$PAQUET/.MainActivity" > "$SORTIE/$nom-demarrage.txt" 2>&1
  grep -E "TotalTime|WaitTime" "$SORTIE/$nom-demarrage.txt" || true
  sleep 15
  # Échauffement : un tour de navigation dans l'accueil
  touches 22 22 22 20 19 21 21 20 20 19 19
  sleep 2
  # Mesure : statistiques d'images remises à zéro, puis navigation identique pour chaque version
  adb shell dumpsys gfxinfo "$PAQUET" reset > /dev/null
  touches 22 22 22 22 21 21 21 21 22 22 20 19 20 19 22 21 22 21
  sleep 2
  adb shell dumpsys gfxinfo "$PAQUET" > "$SORTIE/$nom-gfxinfo.txt"
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" "$SORTIE/$nom-gfxinfo.txt" || true
  adb exec-out screencap -p > "$SORTIE/$nom-ecran.png"
  # Menu de l'appli sélectionnée (touche Menu de la télécommande), puis retour
  touches 82
  sleep 3
  adb exec-out screencap -p > "$SORTIE/$nom-menu.png"
  touches 4
  sleep 2
  adb logcat -d > "$SORTIE/$nom-logcat.txt"
  local pid
  pid=$(adb shell pidof "$PAQUET" | tr -d '\r')
  # Plantage de notre processus seulement (les applis du système de l'émulateur peuvent en avoir)
  if grep -A 2 "FATAL EXCEPTION" "$SORTIE/$nom-logcat.txt" | grep -q "Process: $PAQUET"; then
    echo "ECHEC $nom : plantage"; grep -A 30 "FATAL EXCEPTION" "$SORTIE/$nom-logcat.txt" | head -60; ECHEC=1
  elif [ -z "$pid" ]; then
    echo "ECHEC $nom : l'appli n'est plus en marche"; ECHEC=1
  else
    echo "OK $nom (processus $pid)"
  fi
}

essai debug app/build/outputs/apk/debug/app-debug.apk
essai release app/build/outputs/apk/release/app-release.apk

{
  echo "# Test de démarrage (émulateur Android TV)"
  for nom in debug release; do
    echo "## $nom"
    grep -E "TotalTime" "$SORTIE/$nom-demarrage.txt" || echo "TotalTime: ?"
    grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" "$SORTIE/$nom-gfxinfo.txt" || true
  done
  [ "$ECHEC" = 0 ] && echo "RESULTAT: OK" || echo "RESULTAT: ECHEC"
} > "$SORTIE/resume.txt"
cat "$SORTIE/resume.txt"
exit "$ECHEC"
