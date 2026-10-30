import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Banc d'essai du projet (mesures DANS la JVM, apres prechauffage, pour ne pas
 * mesurer le demarrage de Java ni la compilation JIT).
 *
 *   Usage : java -cp bin Bench <dossier_livres> <dossier_sortie_csv> [E1,E2,E3,E4]
 *
 * Experiences (un fichier CSV par experience) :
 *   construction.csv : taille et temps de construction des automates pour plusieurs RegEx
 *   explosion.csv    : famille (a|b)*a(a|b)^k, croissance exponentielle du DFA
 *   taille.csv       : temps de recherche en fonction de la taille du texte
 *   motif.csv        : temps de recherche en fonction de la longueur du facteur
 *
 * Chaque temps de recherche = moyenne et ecart-type sur REPS executions mesurees,
 * apres WARMUP executions non mesurees. La recherche reproduit exactement la boucle
 * ligne par ligne de Egrep (sans l'ecriture de la sortie).
 */
public class Bench {

    static final int WARMUP = 3, REPS = 10;

    interface Builder { Egrep.LineMatcher build(); }

    public static void main(String[] args) throws IOException {
        Path books = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);

        byte[] corpus = loadCorpus(books);
        System.out.printf("Corpus : %d octets (%.1f Mo), %d lignes%n",
                corpus.length, corpus.length / 1e6, countLines(corpus, corpus.length, (b, f, t) -> true));

        String which = args.length > 2 ? args[2] : "E1,E2,E3,E4";
        if (which.contains("E1")) construction(out.resolve("construction.csv"));
        if (which.contains("E2")) explosion(out.resolve("explosion.csv"));
        if (which.contains("E3")) taille(corpus, out.resolve("taille.csv"));
        if (which.contains("E4")) motif(corpus, out.resolve("motif.csv"));
    }

    // =====================================================================
    //  Corpus : concatenation des livres (ordre alphabetique des fichiers)
    // =====================================================================

    static byte[] loadCorpus(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) { s.filter(p -> p.toString().endsWith(".txt")).sorted().forEach(files::add); }
        int total = 0;
        List<byte[]> parts = new ArrayList<>();
        for (Path p : files) { byte[] b = Files.readAllBytes(p); parts.add(b); total += b.length; }
        byte[] all = new byte[total];
        int pos = 0;
        for (byte[] b : parts) { System.arraycopy(b, 0, all, pos, b.length); pos += b.length; }
        System.out.println(files.size() + " livres charges depuis " + dir);
        return all;
    }

    /** Meme boucle que Egrep.main : nombre de lignes de text[0..len[ retenues par m. */
    static int countLines(byte[] text, int len, Egrep.LineMatcher m) {
        int count = 0, lineStart = 0;
        for (int i = 0; i <= len; i++) {
            boolean endOfLine = (i == len) || (text[i] == '\n');
            if (!endOfLine) continue;
            if (i == len && lineStart == len) break;
            if (m.matches(text, lineStart, i)) count++;
            lineStart = i + 1;
        }
        return count;
    }

    /** Prefixe de taille ~target, coupe sur une fin de ligne. */
    static int prefixLength(byte[] text, int target) {
        if (target >= text.length) return text.length;
        int i = target;
        while (i < text.length && text[i] != '\n') i++;
        return Math.min(i + 1, text.length);
    }

    /** {moyenne, ecart-type} en millisecondes, et nombre de lignes trouvees. */
    static double[] time(byte[] text, int len, Egrep.LineMatcher m) {
        int lines = 0;
        for (int w = 0; w < WARMUP; w++) lines = countLines(text, len, m);
        double[] t = new double[REPS];
        for (int r = 0; r < REPS; r++) {
            long s = System.nanoTime();
            lines = countLines(text, len, m);
            t[r] = (System.nanoTime() - s) / 1e6;
        }
        double mean = Arrays.stream(t).average().orElse(0);
        double var = Arrays.stream(t).map(x -> (x - mean) * (x - mean)).sum() / (REPS - 1);
        return new double[]{mean, Math.sqrt(var), lines};
    }

    static RegexNode parse(String r) { return RegexParser.parse(r); }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

    // Les moteurs, construits exactement comme dans Egrep
    static Egrep.LineMatcher engine(String name, String regex) {
        RegexNode tree = parse(regex);
        switch (name) {
            case "BM":   return new BoyerMoore(KMP.literalOf(tree))::matches;
            case "KMP":  return new KMP(KMP.literalOf(tree))::matches;
            case "DFAmin": return Minimizer.minimize(DFA.fromNFA(NFA.fromTree(tree), true))::matches;
            case "DFA":  return DFA.fromNFA(NFA.fromTree(tree), true)::matches;
            case "NFA":  return new NFAMatcher(NFA.fromTree(tree))::matches;
            case "scan": return (b, f, t) -> false;        // decoupage en lignes seul
            default: throw new IllegalArgumentException(name);
        }
    }

    // =====================================================================
    //  E1. Construction des automates
    // =====================================================================

    static void construction(Path csv) throws IOException {
        String[] regexes = { "Sargon", "S(a|g|r)+on", "a|bc*", "(a|b)*abb", "S.*n", ".a.a.",
                "Th(e|is|at)", "(Ass|Bab)(y|ur)+", "(the|The) (king|queen)", "(a|b)*a(a|b)(a|b)" };
        int N = 200;
        try (PrintWriter w = new PrintWriter(csv.toFile())) {
            w.println("regex;nfa;dfa_search;min_search;dfa_exact;min_exact;t_parse_us;t_nfa_us;t_dfa_us;t_min_us");
            for (String r : regexes) {
                // prechauffage
                for (int k = 0; k < 50; k++) Minimizer.minimize(DFA.fromNFA(NFA.fromTree(parse(r)), true));
                double tp = 0, tn = 0, td = 0, tm = 0;
                NFA nfa = null; DFA d = null, m = null;
                for (int k = 0; k < N; k++) {
                    long a = System.nanoTime(); RegexNode t = parse(r);
                    long b = System.nanoTime(); nfa = NFA.fromTree(t);
                    long c = System.nanoTime(); d = DFA.fromNFA(nfa, true);
                    long e = System.nanoTime(); m = Minimizer.minimize(d);
                    long f = System.nanoTime();
                    tp += b - a; tn += c - b; td += e - c; tm += f - e;
                }
                DFA ex = DFA.fromNFA(nfa, false), exm = Minimizer.minimize(ex);
                w.printf(java.util.Locale.ROOT, "%s;%d;%d;%d;%d;%d;%.2f;%.2f;%.2f;%.2f%n", r, nfa.size(), d.size(),
                        m.size(), ex.size(), exm.size(), tp / N / 1e3, tn / N / 1e3, td / N / 1e3, tm / N / 1e3);
                System.out.printf("E1 %-24s NFA %3d  DFA %4d -> %4d   exact %4d -> %4d%n",
                        r, nfa.size(), d.size(), m.size(), ex.size(), exm.size());
            }
        }
    }

    // =====================================================================
    //  E2. Explosion exponentielle : (a|b)*a(a|b)^k
    // =====================================================================

    static void explosion(Path csv) throws IOException {
        try (PrintWriter w = new PrintWriter(csv.toFile())) {
            w.println("k;nfa;dfa;min;t_dfa_ms;t_min_ms");
            for (int k = 0; k <= 12; k++) {
                String r = "(a|b)*a" + "(a|b)".repeat(k);
                NFA nfa = NFA.fromTree(parse(r));
                int reps = k <= 8 ? 20 : 3;
                DFA.fromNFA(nfa, true);                         // prechauffage
                double td = 0, tm = 0; DFA d = null, m = null;
                for (int i = 0; i < reps; i++) {
                    long a = System.nanoTime(); d = DFA.fromNFA(nfa, true);
                    long b = System.nanoTime(); m = Minimizer.minimize(d);
                    long c = System.nanoTime();
                    td += b - a; tm += c - b;
                }
                w.printf(java.util.Locale.ROOT, "%d;%d;%d;%d;%.3f;%.3f%n", k, nfa.size(), d.size(), m.size(),
                        td / reps / 1e6, tm / reps / 1e6);
                System.out.printf("E2 k=%2d NFA %3d  DFA %5d  min %5d  (%.1f ms + %.1f ms)%n",
                        k, nfa.size(), d.size(), m.size(), td / reps / 1e6, tm / reps / 1e6);
            }
        }
    }

    // =====================================================================
    //  E3. Temps de recherche en fonction de la taille du texte
    // =====================================================================

    static void taille(byte[] corpus, Path csv) throws IOException {
        int[] targets = { 1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000, corpus.length };
        String[][] cases = {
            { "Sargon", "scan" }, { "Sargon", "BM" }, { "Sargon", "KMP" }, { "Sargon", "DFAmin" }, { "Sargon", "NFA" },
            { "S(a|g|r)+on", "DFAmin" }, { "S(a|g|r)+on", "DFA" }, { "S(a|g|r)+on", "NFA" },
        };
        try (PrintWriter w = new PrintWriter(csv.toFile())) {
            w.println("regex;moteur;octets;mo;moy_ms;sd_ms;lignes");
            for (String[] c : cases) {
                Egrep.LineMatcher m = engine(c[1], c[0]);
                for (int target : targets) {
                    int len = prefixLength(corpus, target);
                    double[] r = time(corpus, len, m);
                    w.printf(java.util.Locale.ROOT, "%s;%s;%d;%.3f;%.3f;%.3f;%d%n",
                            c[0], c[1], len, len / 1e6, r[0], r[1], (int) r[2]);
                    System.out.printf("E3 %-12s %-7s %6.1f Mo  %8.2f +- %6.2f ms  (%d lignes)%n",
                            c[0], c[1], len / 1e6, r[0], r[1], (int) r[2]);
                }
            }
        }
    }

    // =====================================================================
    //  E4. Influence de la longueur du facteur (BM vs KMP vs DFA minimal)
    // =====================================================================

    static void motif(byte[] corpus, Path csv) throws IOException {
        int[] lengths = { 2, 4, 8, 16, 32, 64 };
        int PATTERNS = 20;
        // "scan" (decoupage en lignes seul) sert de TEMOIN : son cout ne depend pas du motif,
        // donc s'il varie, c'est la machine qui a change de vitesse (chauffe, autre programme...).
        String[] engines = { "BM", "KMP", "DFAmin", "scan" };
        Random rnd = new Random(2026);
        try (PrintWriter w = new PrintWriter(csv.toFile())) {
            w.println("m;moteur;moy_ms;sd_ms");
            for (int m : lengths) {
                // facteurs tires du corpus (donc presents), uniquement lettres et espaces :
                // pas de metacaractere de RegEx
                List<String> pats = new ArrayList<>();
                while (pats.size() < PATTERNS) {
                    int p = rnd.nextInt(corpus.length - m);
                    boolean okp = true;
                    for (int i = p; i < p + m && okp; i++) {
                        byte b = corpus[i];
                        okp = (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || b == ' ';
                    }
                    if (okp) pats.add(new String(corpus, p, m, StandardCharsets.ISO_8859_1));
                }
                // Mesures ENTRELACEES : pour chaque facteur, les moteurs passent l'un apres
                // l'autre, dans un ordre qui tourne. Une derive de la machine pendant la mesure
                // touche alors tous les moteurs de la meme facon, au lieu d'en penaliser un seul.
                double[][] t = new double[engines.length][PATTERNS];
                for (int i = 0; i < PATTERNS; i++)
                    for (int k = 0; k < engines.length; k++) {
                        int e = (i + k) % engines.length;
                        t[e][i] = time(corpus, corpus.length, engine(engines[e], pats.get(i)))[0];
                    }
                for (int e = 0; e < engines.length; e++) {
                    double[] v = t[e];
                    double mean = Arrays.stream(v).average().orElse(0);
                    double sd = Math.sqrt(Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / (PATTERNS - 1));
                    w.printf(java.util.Locale.ROOT, "%d;%s;%.3f;%.3f%n", m, engines[e], mean, sd);
                    System.out.printf("E4 m=%2d %-7s %8.2f +- %6.2f ms%n", m, engines[e], mean, sd);
                }
            }
        }
    }
}
