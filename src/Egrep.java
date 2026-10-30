import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Clone de egrep (support partiel des ERE : lettres, '.', '|', '*', '+', parentheses).
 *
 *   Usage : java Egrep [options] [moteur] "RegEx" fichier.txt
 *
 *   Moteurs :
 *     -auto (defaut) : facteur (suite de lettres) -> Boyer-Moore, sinon DFA minimal
 *     -bm            : comme -auto (facteur -> Boyer-Moore, sinon DFA minimal)
 *     -kmp           : facteur -> Knuth-Morris-Pratt, sinon DFA minimal
 *     Autrement dit, -bm / -kmp choisissent l'algorithme des MOTS SIMPLES ; une vraie
 *     RegEx passe toujours par la strategie 1 (automates). --info affiche le moteur retenu.
 *     -min           : DFA minimise (methode des sous-ensembles + Moore)
 *     -dfa           : DFA non minimise
 *     -nfa           : simulation directe du NFA (sans determinisation)
 *     Si le DFA explose (trop d'etats), on revient automatiquement au NFA.
 *
 *   Options d'affichage :
 *     -n       : prefixe chaque ligne par son numero (comme egrep -n)
 *     --color  : surligne en gras/rouge les sous-chaines reconnues (couleurs ANSI)
 *     -o       : n'affiche que les sous-chaines reconnues, une par ligne (comme egrep -o)
 *     --info   : affiche sur stderr le moteur choisi et la taille des automates
 *
 * Sans option, la sortie est octet pour octet celle de "LC_ALL=C egrep" .
 * Avec -n et/ou -o, elle est celle de "LC_ALL=C egrep -n / -o / -no".
 * Le fichier est lu en OCTETS : chaque octet est un caractere.
 *
 * Code de retour (comme egrep) : 0 = au moins une ligne trouvee, 1 = aucune, 2 = erreur.
 */
public class Egrep {

    /** Dit si une ligne buf[from..to[ contient le motif. */
    interface LineMatcher {
        boolean matches(byte[] buf, int from, int to);
    }

    /**
     * Trouve la premiere correspondance non vide dans buf[from..to[ :
     * renvoie {debut, fin} (la plus a gauche, puis la plus longue, comme POSIX/egrep), ou null.
     */
    interface MatchFinder {
        int[] next(byte[] buf, int from, int to);
    }

    /** Fin de la plus longue correspondance commencant en i, -1 si aucune. */
    interface AnchoredMatcher {
        int longestMatchEnd(byte[] buf, int i, int to);
    }

    /** Le moteur de recherche retenu pour une RegEx. */
    static final class Engine {
        String name;
        LineMatcher matcher;     // phase 1 : la ligne contient-elle le motif ?  (rapide)
        MatchFinder finder;      // phase 2 : OU sont les correspondances ?      (seulement si besoin)
    }

    // Couleurs ANSI (memes conventions que grep --color)
    private static final byte[] MATCH_ON  = "\033[1;31m".getBytes(StandardCharsets.ISO_8859_1); // gras rouge
    private static final byte[] NUM_ON    = "\033[32m".getBytes(StandardCharsets.ISO_8859_1);   // vert
    private static final byte[] SEP_ON    = "\033[36m".getBytes(StandardCharsets.ISO_8859_1);   // cyan
    private static final byte[] COLOR_OFF = "\033[0m".getBytes(StandardCharsets.ISO_8859_1);

    public static void main(String[] args) {
        String mode = "-auto";
        boolean lineNumbers = false, color = false, onlyMatching = false, info = false;
        // --- Guide d'utilisation ---
        // Sans argument : guide sur stderr et code 2 (lancement incorrect, comme egrep).
        // -h / --help   : guide sur stdout et code 0 (demande explicite).
        if (args.length == 0) {
            printGuide(System.err);
            System.exit(2);
        }
        // On ne cherche -h/--help que parmi les OPTIONS, pas dans "RegEx fichier" :
        // ainsi on peut toujours chercher le texte "-h" avec : -- "-h" fichier.txt
        int lastOpt = args.length > 2 ? args.length - 2 : args.length;
        for (int i = 0; i < lastOpt; i++) {
            if (args[i].equals("--")) break;
            if (args[i].equals("-h") || args[i].equals("--help")) {
                printGuide(System.out);
                System.exit(0);
            }
        }
        // --- Lecture des options ---
        int a = 0;
        while (a < args.length && args.length - a > 2) {
            String opt = args[a];
            if (opt.equals("--")) { a++; break; }
            switch (opt) {
                case "-auto": case "-bm": case "-kmp": case "-min": case "-dfa": case "-nfa":
                    mode = opt; break;
                case "-n":      lineNumbers = true; break;
                case "--color": color = true; break;
                case "-o":      onlyMatching = true; break;
                case "--info":  info = true; break;
                default:
                    usage("option inconnue " + opt);
            }
            a++;
        }
        if (args.length - a != 2) usage(null);
        String regexArg = args[a], fileArg = args[a + 1];
        boolean needSpans = color || onlyMatching;

        // --- Etapes 1 a 4 : RegEx -> arbre -> NFA -> DFA -> DFA minimal (ou KMP/BM) ---
        Engine engine;
        try {
            // La RegEx est aussi traitee en octets (un caractere non-ASCII = plusieurs octets),
            // pour rester coherent avec la lecture du fichier et avec LC_ALL=C egrep.
            String enc = System.getProperty("sun.jnu.encoding", "UTF-8");
            String regexBytes = new String(regexArg.getBytes(java.nio.charset.Charset.forName(enc)),
                                           StandardCharsets.ISO_8859_1);
            RegexNode tree = RegexParser.parse(regexBytes);
            engine = buildEngine(tree, mode, needSpans, info);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        // --- Lecture du fichier ---
        byte[] text;
        try {
            text = Files.readAllBytes(Paths.get(fileArg));
        } catch (IOException e) {
            System.err.println("Impossible de lire " + fileArg + " : " + e.getMessage());
            System.exit(2);
            return;
        }

        // --- Etape 5 : recherche ligne par ligne ---
        boolean found = false;
        int matchedLines = 0;
        try (OutputStream out = new BufferedOutputStream(System.out, 1 << 16)) {
            int lineStart = 0;
            int lineNo = 0;
            for (int i = 0; i <= text.length; i++) {
                boolean endOfLine = (i == text.length) || (text[i] == '\n');
                if (!endOfLine) continue;
                // derniere "ligne" vide apres le dernier '\n' : on l'ignore (comme egrep)
                if (i == text.length && lineStart == text.length) break;
                lineNo++;

                if (engine.matcher.matches(text, lineStart, i)) {
                    found = true;
                    matchedLines++;
                    if (onlyMatching)
                        printMatches(out, text, lineStart, i, lineNo, lineNumbers, color, engine.finder);
                    else
                        printLine(out, text, lineStart, i, lineNo, lineNumbers, color, engine.finder);
                }
                lineStart = i + 1;
            }
        } catch (IOException e) {
            System.err.println("Erreur d'ecriture : " + e.getMessage());
            System.exit(2);
        }
        if (info) System.err.println("[info] " + matchedLines + " ligne(s) trouvee(s)");
        System.exit(found ? 0 : 1);
    }

    // =====================================================================
    //  Choix du moteur
    // =====================================================================

    private static Engine buildEngine(RegexNode tree, String mode, boolean needSpans, boolean info) {
        Engine e = new Engine();
        byte[] factor = KMP.literalOf(tree);   // non null <=> la RegEx est une suite de lettres

        // --- Cas 1 : la RegEx est un facteur -> Boyer-Moore ou KMP ---
        if (mode.equals("-auto") || mode.equals("-bm")) {
            if (factor != null) {
                BoyerMoore bm = new BoyerMoore(factor);
                e.name = "Boyer-Moore (facteur de " + factor.length + " lettres)";
                e.matcher = bm::matches;
                e.finder = literalFinder(bm::find, factor.length);
                if (info) System.err.println("[info] moteur : " + e.name);
                return e;
            }
        }
        if (mode.equals("-kmp") && factor != null) {
            KMP kmp = new KMP(factor);
            e.name = "KMP (facteur de " + factor.length + " lettres)";
            e.matcher = kmp::matches;
            e.finder = literalFinder(kmp::find, factor.length);
            if (info) System.err.println("[info] moteur : " + e.name);
            return e;
        }

        // --- Cas 2 : automates (vraie RegEx, ou moteur d'automate demande) ---
        // Avec -auto, -bm ou -kmp, une RegEx qui n'est pas un facteur arrive ici :
        // strategie 1 avec le DFA minimal.
        NFA nfa = NFA.fromTree(tree);
        boolean minimize = !mode.equals("-dfa");      // -min, -auto, -bm, -kmp
        NFAMatcher nfaMatcher = null;   // cree seulement si on en a besoin

        if (!mode.equals("-nfa")) {
            try {
                // DFA de .*R : un seul passage sur la ligne pour savoir si elle correspond
                DFA search = DFA.fromNFA(nfa, true);
                int before = search.size();
                if (minimize) search = Minimizer.minimize(search);
                e.matcher = search::matches;
                e.name = (minimize ? "DFA minimal" : "DFA") + " (NFA " + nfa.size()
                        + " etats -> DFA " + before + " etats"
                        + (minimize ? " -> " + search.size() + " apres minimisation" : "") + ")";
            } catch (IllegalStateException explosion) {
                System.err.println("Avertissement : " + explosion.getMessage()
                        + ", simulation du NFA a la place");
            }
            if (needSpans) {
                try {
                    // DFA de R (exact) : pour trouver OU commence et finit chaque correspondance
                    DFA exact = DFA.fromNFA(nfa, false);
                    if (minimize) exact = Minimizer.minimize(exact);
                    e.finder = anchoredFinder(exact::longestMatchEnd);
                } catch (IllegalStateException explosion) {
                    // on retombera sur le NFA pour le surlignage
                }
            }
        }
        if (e.matcher == null) {
            nfaMatcher = new NFAMatcher(nfa);
            e.matcher = nfaMatcher::matches;
            e.name = "simulation du NFA (" + nfa.size() + " etats)";
        }
        if (needSpans && e.finder == null) {
            if (nfaMatcher == null) nfaMatcher = new NFAMatcher(nfa);
            e.finder = anchoredFinder(nfaMatcher::longestMatchEnd);
        }
        if (info) System.err.println("[info] moteur : " + e.name);
        return e;
    }

    /** Correspondances d'un facteur : chaque occurrence, sans chevauchement. */
    private static MatchFinder literalFinder(LineFind find, int m) {
        return (buf, from, to) -> {
            int p = find.find(buf, from, to);
            return p < 0 ? null : new int[]{p, p + m};
        };
    }

    interface LineFind { int find(byte[] buf, int from, int to); }

    /**
     * Correspondances d'un automate : on essaie chaque position de depart de gauche a droite,
     * et on garde la plus longue correspondance (non vide) qui y commence.
     * Cout : O(longueur de ligne * longueur de correspondance) au pire, mais seulement
     * sur les lignes deja retenues par la phase 1.
     */
    private static MatchFinder anchoredFinder(AnchoredMatcher a) {
        return (buf, from, to) -> {
            for (int i = from; i < to; i++) {
                int end = a.longestMatchEnd(buf, i, to);
                if (end > i) return new int[]{i, end};
            }
            return null;
        };
    }

    // =====================================================================
    //  Affichage
    // =====================================================================

    private static void printPrefix(OutputStream out, int lineNo, boolean color) throws IOException {
        byte[] num = Integer.toString(lineNo).getBytes(StandardCharsets.ISO_8859_1);
        if (color) { out.write(NUM_ON); out.write(num); out.write(COLOR_OFF);
                     out.write(SEP_ON); out.write(':'); out.write(COLOR_OFF); }
        else       { out.write(num); out.write(':'); }
    }

    /** Ligne entiere, eventuellement numerotee, avec les correspondances surlignees. */
    private static void printLine(OutputStream out, byte[] text, int from, int to, int lineNo,
                                  boolean lineNumbers, boolean color, MatchFinder finder) throws IOException {
        if (lineNumbers) printPrefix(out, lineNo, color);
        if (!color) {
            out.write(text, from, to - from);
        } else {
            int pos = from;
            int[] m;
            while (pos < to && (m = finder.next(text, pos, to)) != null) {
                out.write(text, pos, m[0] - pos);          // texte avant la correspondance
                out.write(MATCH_ON);
                out.write(text, m[0], m[1] - m[0]);        // la correspondance, en gras rouge
                out.write(COLOR_OFF);
                pos = m[1];                                 // on reprend juste apres
            }
            out.write(text, pos, to - pos);
        }
        out.write('\n');
    }

    /** Mode -o : seulement les sous-chaines reconnues, une par ligne. */
    private static void printMatches(OutputStream out, byte[] text, int from, int to, int lineNo,
                                     boolean lineNumbers, boolean color, MatchFinder finder) throws IOException {
        int pos = from;
        int[] m;
        while (pos < to && (m = finder.next(text, pos, to)) != null) {
            if (lineNumbers) printPrefix(out, lineNo, color);
            if (color) out.write(MATCH_ON);
            out.write(text, m[0], m[1] - m[0]);
            if (color) out.write(COLOR_OFF);
            out.write('\n');
            pos = m[1];
        }
    }

    /** Erreur d'utilisation : message court + rappel de --help, code 2. */
    private static void usage(String msg) {
        if (msg != null) System.err.println("Erreur : " + msg);
        System.err.println("Usage : java -jar egrep.jar [options] [moteur] \"RegEx\" fichier.txt");
        System.err.println("Tapez  java -jar egrep.jar --help  pour le guide complet.");
        System.exit(2);
    }

    /**
     * Guide d'utilisation (sans accents : il doit s'afficher correctement dans toutes
     * les consoles, y compris l'invite de commandes Windows).
     */
    private static void printGuide(java.io.PrintStream out) {
        out.println(String.join("\n",
            "egrep (clone) - DAAR projet 1, Sorbonne Universite",
            "Affiche les lignes d'un fichier qui contiennent un motif (RegEx).",
            "",
            "USAGE",
            "  java -jar egrep.jar [options] [moteur] \"RegEx\" fichier.txt",
            "  (toujours mettre la RegEx entre guillemets)",
            "",
            "REGEX ACCEPTEES",
            "  lettres  abc        point  .  (n'importe quel caractere)",
            "  alternative  a|b    etoile  a*  (0 fois ou plus)    plus  a+  (1 fois ou plus)",
            "  parentheses  (ab)   \\c  cherche le caractere c lui-meme (ex. \\.  \\?  \\*)",
            "  Non supportes : ?  [  {  ^  $   (message d'erreur explicite)",
            "",
            "OPTIONS D'AFFICHAGE",
            "  -n          numero de ligne devant chaque ligne trouvee",
            "  --color     motif trouve en gras rouge",
            "  -o          seulement les morceaux trouves, un par ligne",
            "  --info      algorithme choisi et taille des automates (sur stderr)",
            "  -h, --help  affiche ce guide",
            "",
            "MOTEURS (le resultat est le meme, seul le temps change)",
            "  -auto  (defaut) mot simple -> Boyer-Moore, sinon automate (DFA minimal)",
            "  -bm    mot simple -> Boyer-Moore, sinon DFA minimal",
            "  -kmp   mot simple -> KMP, sinon DFA minimal",
            "  -min   DFA minimal   ",
            "  -dfa  DFA non minimise ",
            "  -nfa  simulation du NFA",
            "",
            "EXEMPLES",
            "  java -jar egrep.jar -n --color \"S(a|g|r)+on\" tests/data/56667.txt",
            "  java -jar egrep.jar -n -o \"(Ass|Bab)(y|ur)+\" tests/data/56667.txt",
            "  java -jar egrep.jar -kmp --info \"Sargon\" tests/data/56667.txt",
            "",
            "CODE DE RETOUR : 0 = trouve, 1 = rien trouve, 2 = erreur"));
    }

}