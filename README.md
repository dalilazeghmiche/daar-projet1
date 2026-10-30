# DAAR — Projet 1 : clone de egrep

RegEx supportées : lettres ASCII, `.`, concaténation, `|`, `*`, `+` (réécrit en `XX*`), parenthèses, `\` pour échapper.

## Compiler et lancer

```bash
make                 # compile src/ et crée egrep.jar
java -jar egrep.jar "S(a|g|r)+on" tests/data/56667.txt
make demo            # même exemple, avec numéros de ligne et surlignage
make test            # tests unitaires

```

Sans `make` (par exemple sous Windows) :

```bash
javac -d build src/*.java
jar cfe egrep.jar Egrep -C build .
java -jar egrep.jar --help
```

## Options

```
java -jar egrep.jar [-n] [-o] [--color] [--info] [moteur] "RegEx" fichier.txt
```

| Option         | Effet                                                                      |
| -------------- | -------------------------------------------------------------------------- |
| `-n`           | numéro de ligne devant chaque ligne trouvée (`437:...`)                    |
| `--color`      | sous-chaînes reconnues en **gras rouge** (ANSI)                            |
| `-o`           | seulement les sous-chaînes reconnues, une par ligne                        |
| `--info`       | moteur choisi et taille des automates (sur stderr)                         |
| `-h`, `--help` | guide d'utilisation (affiché aussi si on lance le programme sans argument) |

| Moteur           | Méthode                                                    |
| ---------------- | ---------------------------------------------------------- |
| `-auto` (défaut) | facteur → Boyer-Moore ; sinon DFA minimal                  |
| `-bm`, `-kmp`    | mot simple → Boyer-Moore / KMP ; vraie RegEx → DFA minimal |
| `-min`, `-dfa`   | DFA minimisé / non minimisé (méthode des sous-ensembles)   |
| `-nfa`           | simulation directe du NFA                                  |

Sans option, la sortie est octet pour octet celle de `LC_ALL=C egrep` ; avec `-n`/`-o`, celle de `egrep -n`/`-o`.
Code de retour : 0 = trouvé, 1 = rien, 2 = erreur.

## Organisation

```
src/   RegexParser, RegexNode   RegEx -> arbre syntaxique (Aho-Ullman fig. 10.29)
       NFA                      arbre -> ε-NFA (fig. 10.27-10.28)
       DFA                      ε-NFA -> DFA (sous-ensembles), recherche de .*R
       Minimizer                minimisation (Moore, Aho-Ullman p. 555)
       NFAMatcher               simulation du NFA (comparaison / repli si le DFA explose)
       KMP, BoyerMoore          cas où la RegEx est un facteur
       Egrep                    programme principal, choix du moteur, affichage
tests/ tests unitaires, data/ (livres Gutenberg de test)
```

Les livres du benchmark (`bench_data/`, ~25 Mo) ne sont pas inclus dans le rendu : liste dans `tests/livres_bench.txt`, script de téléchargement dans `tests/scripts/`.
