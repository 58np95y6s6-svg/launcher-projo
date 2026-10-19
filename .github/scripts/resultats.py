#!/usr/bin/env python3
"""Résumé des tests JUnit (XML écrits par Gradle) : une ligne par test « NOM | OK/ECHEC/IGNORE | message »."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

dossier = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results"
lignes = []
compte = {"OK": 0, "ECHEC": 0, "IGNORE": 0}

for fichier in sorted(glob.glob(os.path.join(dossier, "**", "*.xml"), recursive=True)):
    try:
        racine = ET.parse(fichier).getroot()
    except ET.ParseError:
        continue
    for cas in racine.iter("testcase"):
        nom = cas.get("classname", "").split(".")[-1] + "." + cas.get("name", "")
        probleme = cas.find("failure")
        if probleme is None:
            probleme = cas.find("error")
        if probleme is not None:
            message = (probleme.get("message") or "").strip()
            if not message:
                # Exception sans message : type + début de la pile
                message = " ".join((probleme.text or "").strip().splitlines()[:4])
            message = " ⏎ ".join(l.strip() for l in message.splitlines() if l.strip())
            lignes.append(f"{nom} | ECHEC | {message[:2000]}")
            compte["ECHEC"] += 1
        elif cas.find("skipped") is not None:
            lignes.append(f"{nom} | IGNORE | ")
            compte["IGNORE"] += 1
        else:
            lignes.append(f"{nom} | OK | ")
            compte["OK"] += 1

print(f"# {compte['OK']} OK, {compte['ECHEC']} ECHEC, {compte['IGNORE']} ignorés")
print("\n".join(lignes))
