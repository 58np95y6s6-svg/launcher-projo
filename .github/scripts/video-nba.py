#!/usr/bin/env python3
"""Fichiers de préférences d'iBiTV pour l'enregistrement de la vidéo du design NBA.

projo.preferences_pb (DataStore « projo ») : design « nba », favoris donnés (un paquet par ligne),
favoris par défaut marqués comme déjà appliqués. Encodage protobuf de PreferenceMap
(map<string, Value> = 1 ; Value : boolean = 1, string = 5).
Usage : video-nba.py <sortie.preferences_pb> <paquet>...
"""
import sys


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def champ(numero, donnees):
    return bytes([numero << 3 | 2]) + varint(len(donnees)) + donnees


def texte(valeur):
    return champ(5, valeur.encode())


def booleen(valeur):
    return bytes([1 << 3 | 0, 1 if valeur else 0])


def entree(cle, valeur):
    return champ(1, champ(1, cle.encode()) + champ(2, valeur))


sortie, paquets = sys.argv[1], sys.argv[2:]
contenu = entree("design", texte("nba"))
if paquets:
    contenu += entree("favoris", texte("\n".join(paquets)))
contenu += entree("favoris_par_defaut_appliques", booleen(True))
with open(sortie, "wb") as f:
    f.write(contenu)
