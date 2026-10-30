# Trace les graphes a partir des CSV de Bench.  Usage : python bench/graphes.py
import csv, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

DOSSIER_CSV = "resultats_finaux"   # ou Bench a ecrit les CSV
DOSSIER_GRAPHES = "graphes"        # ou ecrire les graphes

def lire(nom):
    with open(os.path.join(DOSSIER_CSV, nom), newline="") as f:
        return list(csv.DictReader(f, delimiter=";"))

os.makedirs(DOSSIER_GRAPHES, exist_ok=True)

# E2 : taille des automates selon k
d = lire("explosion.csv")
k = [int(r["k"]) for r in d]
plt.figure()
for col, nom in (("nfa", "NFA"), ("dfa", "DFA"), ("min", "DFA minimal")):
    plt.plot(k, [int(r[col]) for r in d], marker="o", label=nom)
plt.yscale("log", base=2)
plt.xlabel("k"); plt.ylabel("nombre d'états"); plt.legend()
plt.savefig(os.path.join(DOSSIER_GRAPHES, "e2_explosion.png"))

# E3 : temps selon la taille du texte (facteur Sargon)
d = lire("taille.csv")
plt.figure()
for moteur in ("NFA", "DFAmin", "KMP", "BM", "scan"):
    r = [x for x in d if x["regex"] == "Sargon" and x["moteur"] == moteur]
    plt.errorbar([float(x["mo"]) for x in r], [float(x["moy_ms"]) for x in r],
                 yerr=[float(x["sd_ms"]) for x in r], marker="o", capsize=3, label=moteur)
plt.xlabel("taille du texte (Mo)"); plt.ylabel("temps (ms)"); plt.legend()
plt.savefig(os.path.join(DOSSIER_GRAPHES, "e3_taille.png"))

# E4 : temps selon la longueur du facteur
d = lire("motif.csv")
plt.figure()
for moteur in sorted({x["moteur"] for x in d}):
    r = [x for x in d if x["moteur"] == moteur]
    plt.errorbar([int(x["m"]) for x in r], [float(x["moy_ms"]) for x in r],
                 yerr=[float(x["sd_ms"]) for x in r], marker="o", capsize=3, label=moteur)
plt.xscale("log", base=2)
plt.xlabel("longueur du facteur m"); plt.ylabel("temps (ms)"); plt.legend()
plt.savefig(os.path.join(DOSSIER_GRAPHES, "e4_motif.png"))

print("graphes ecrits dans", DOSSIER_GRAPHES)