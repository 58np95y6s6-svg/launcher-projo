"""Tendances TMDB de la semaine (fr-FR) pour l'aperçu « Galerie » : images + donnees.js.

Usage : python3 outils/tmdb.py <dossier_apercu>
Clé : variable d'environnement TMDB_KEY (clé v3, ou jeton v4 « eyJ… »). Elle n'est JAMAIS
affichée : aucune adresse n'est imprimée, les erreurs sont résumées sans leur texte.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

BASE = "https://api.themoviedb.org/3/"
IMG = "https://image.tmdb.org/t/p/"
CLE = (os.environ.get("TMDB_KEY") or "").strip()
JETON = CLE.startswith("eyJ")


def appel(chemin, **params):
    params["language"] = "fr-FR"
    if not JETON:
        params["api_key"] = CLE
    req = urllib.request.Request(BASE + chemin + "?" + urllib.parse.urlencode(params))
    req.add_header("Accept", "application/json")
    if JETON:
        req.add_header("Authorization", "Bearer " + CLE)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except Exception as e:  # jamais le message (il pourrait contenir l'adresse et donc la clé)
        print("appel TMDB refusé :", chemin.split("/")[0], type(e).__name__)
        return None


def telecharger(chemin_image, taille, fichier):
    try:
        with urllib.request.urlopen(IMG + taille + chemin_image, timeout=30) as r:
            data = r.read()
        with open(fichier, "wb") as f:
            f.write(data)
        return True
    except Exception as e:
        print("image refusée :", type(e).__name__)
        return False


def main():
    dossier = sys.argv[1]
    if not CLE:
        print("TMDB_KEY absente : aperçu non généré")
        sys.exit(1)
    os.makedirs(os.path.join(dossier, "img"), exist_ok=True)
    genres = {}
    for t in ("movie", "tv"):
        g = appel(f"genre/{t}/list") or {}
        for x in g.get("genres", []):
            genres[x["id"]] = x["name"]
    tend = appel("trending/all/week") or {}
    resultats = [r for r in tend.get("results", []) if r.get("backdrop_path") and r.get("poster_path")
                 and r.get("media_type") in ("movie", "tv")]
    # Le héros : le premier film de la liste, sinon le premier titre
    heros = next((x for x in resultats if x["media_type"] == "movie"), None)
    if heros is not None:
        resultats.remove(heros)
        resultats.insert(0, heros)
    titres = []
    for i, r in enumerate(resultats[:12]):
        film = r["media_type"] == "movie"
        nom = r.get("title") if film else r.get("name")
        date = (r.get("release_date") if film else r.get("first_air_date")) or ""
        genre = next((genres[g] for g in r.get("genre_ids", []) if g in genres), "")
        fournisseur = ""
        p = appel(f"{r['media_type']}/{r['id']}/watch/providers") or {}
        fr = (p.get("results") or {}).get("FR") or {}
        if fr.get("flatrate"):
            fournisseur = fr["flatrate"][0].get("provider_name", "")
        ok = telecharger(r["backdrop_path"], "w1280", os.path.join(dossier, "img", f"backdrop{i}.jpg"))
        ok = telecharger(r["poster_path"], "w500", os.path.join(dossier, "img", f"poster{i}.jpg")) and ok
        if not ok:
            continue
        titres.append({
            "titre": nom,
            "sous": ("Film" if film else "Série") + (" · " + genre if genre else ""),
            "plateforme": fournisseur or ("Au cinéma" if film else "TV"),
            "annee": date[:4],
            "note": ("%.1f" % r["vote_average"]).replace(".", ",") if r.get("vote_average") else "",
            "backdrop": f"img/backdrop{i}.jpg",
            "poster": f"img/poster{i}.jpg",
        })
    if len(titres) < 6:
        print("Pas assez de titres TMDB :", len(titres))
        sys.exit(1)
    with open(os.path.join(dossier, "donnees.js"), "w", encoding="utf-8") as f:
        f.write("window.DONNEES = " + json.dumps({"titres": titres, "source": "tmdb"}, ensure_ascii=False, indent=1) + ";\n")
    print("Titres TMDB :", len(titres))


main()
