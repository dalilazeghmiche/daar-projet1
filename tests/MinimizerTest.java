import java.util.Random;

/**
 * Tests de l'etape 5 : minimisation du DFA.
 *
 *  1. Nombre d'etats attendu sur des exemples verifies a la main.
 *  2. Test differentiel : le DFA minimise reconnait EXACTEMENT le meme langage
 *     que le DFA non minimise (mots aleatoires, mode exact et mode search).
 *  3. Tableau NFA / DFA / DFA minimal (donnees pour le rapport).
 */
public class MinimizerTest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // 1. Exemples verifies a la main (mode exact)
        checkSize("a|bc*",       3);  // {debut}, {a ou b vus : final, sans c... } voir rapport
        checkSize("S(a|g|r)+on", 5);  // S -> (a|g|r) -> boucle -> o -> n
        checkSize("(a|b)*abb",   4);  // exemple classique du Dragon Book
        checkSize("a*",          1);
        checkSize("abc",         4);

        DFA min = Minimizer.minimize(DFA.fromNFA(NFA.fromTree(RegexParser.parse("S(a|g|r)+on")), false));
        System.out.println("\nDFA minimal de S(a|g|r)+on :");
        System.out.println(min);

        // 2. Test differentiel
        String[] regexes = { "a|bc*", "S(a|g|r)+on", "(a|b)*abb", "a.c", "(ab|ba)*c",
                             "a*", "b(a|c)*b", "(a|b)*a(a|b)(a|b)", ".*c.*a", "(a|b)(a|b)*|c" };
        char[] alphabet = { 'a', 'b', 'c', 'S', 'g', 'r', 'o', 'n', 'x' };
        Random rnd = new Random(7);
        for (String r : regexes) {
            NFA nfa = NFA.fromTree(RegexParser.parse(r));
            DFA exact = DFA.fromNFA(nfa, false), exactMin = Minimizer.minimize(exact);
            DFA search = DFA.fromNFA(nfa, true), searchMin = Minimizer.minimize(search);
            boolean same = true;
            for (int k = 0; k < 5000 && same; k++) {
                StringBuilder sb = new StringBuilder();
                int len = rnd.nextInt(12);
                for (int i = 0; i < len; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
                String w = sb.toString();
                if (exact.accepts(w) != exactMin.accepts(w) || search.matches(w) != searchMin.matches(w)) {
                    System.out.println("  difference sur \"" + w + "\"");
                    same = false;
                }
            }
            check("meme langage apres minimisation : " + r, same);
        }

        // 3. Tableau pour le rapport
        System.out.println("\n RegEx                 | NFA | DFA exact -> min | DFA search -> min");
        System.out.println("-----------------------+-----+------------------+------------------");
        for (String r : regexes) {
            NFA nfa = NFA.fromTree(RegexParser.parse(r));
            DFA e = DFA.fromNFA(nfa, false), s = DFA.fromNFA(nfa, true);
            System.out.printf(" %-21s | %3d | %6d -> %-6d | %6d -> %-6d%n", r, nfa.size(),
                    e.size(), Minimizer.minimize(e).size(), s.size(), Minimizer.minimize(s).size());
        }

        System.out.println("\n" + ok + "/" + total + " tests reussis");
    }

    private static void checkSize(String regex, int expected) {
        DFA dfa = DFA.fromNFA(NFA.fromTree(RegexParser.parse(regex)), false);
        DFA min = Minimizer.minimize(dfa);
        check(String.format("%-12s DFA %2d etats -> minimal %2d etats (attendu %d)",
                regex, dfa.size(), min.size(), expected), min.size() == expected);
    }

    private static void check(String name, boolean cond) {
        total++;
        if (cond) { ok++; System.out.println("OK     " + name); }
        else System.out.println("ECHEC  " + name);
    }
}