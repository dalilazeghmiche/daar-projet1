import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Tests de la localisation des occurrences : longestMatchEnd(buf, i, to)
 * renvoie la fin de la plus LONGUE correspondance qui COMMENCE en i, ou -1.
 *
 *  1. Exemples calcules a la main, sur le DFA minimal exact.
 *  2. Test differentiel : le DFA et la simulation du NFA donnent la meme fin,
 *     pour chaque position de depart, sur des lignes aleatoires.
 */
public class LocalisationTest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // 1. Exemples                               ligne          debut  fin attendue
        end("S(a|g|r)+on",   "to Sargon of",   3,  9);   // "Sargon" = positions 3 a 8
        end("S(a|g|r)+on",   "to Sargon of",   0, -1);   // rien ne commence en 0
        end("(ab|a)(c|bcd)", "xabcdy",         1,  5);   // la plus LONGUE : "abcd", pas "abc"
        end("a*",            "xyz",            0,  0);   // seul le mot vide : fin = debut
        end("a*",            "aaab",           0,  3);   // "aaa"
        end("S.*n",          "Sargon and on",  0, 13);   // .* va le plus loin possible
        end("Th(e|is|at)",   "This",           0,  4);

        // 2. DFA == NFA sur des lignes aleatoires
        String[] regexes = { "a|bc*", "S(a|g|r)+on", "(a|b)*abb", "a.c", "(ab|ba)*c",
                             "a*", "b(a|c)*b", "(ab|a)(c|bcd)" };
        char[] alphabet = { 'a', 'b', 'c', 'd', 'S', 'g', 'r', 'o', 'n' };
        Random rnd = new Random(11);
        for (String r : regexes) {
            NFA nfa = NFA.fromTree(RegexParser.parse(r));
            DFA exact = Minimizer.minimize(DFA.fromNFA(nfa, false));
            NFAMatcher sim = new NFAMatcher(nfa);
            boolean same = true;
            for (int k = 0; k < 3000 && same; k++) {
                StringBuilder sb = new StringBuilder();
                int len = rnd.nextInt(12);
                for (int i = 0; i < len; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
                byte[] l = bytes(sb.toString());
                for (int i = 0; i <= l.length && same; i++) {
                    int d = exact.longestMatchEnd(l, i, l.length), n = sim.longestMatchEnd(l, i, l.length);
                    if (d != n) {
                        System.out.println("  difference sur \"" + sb + "\" depuis " + i + " : DFA=" + d + " NFA=" + n);
                        same = false;
                    }
                }
            }
            check("DFA == NFA pour chaque position de depart : " + r, same);
        }
        System.out.println(ok + "/" + total + " tests reussis");
    }

    private static void end(String regex, String line, int from, int expected) {
        DFA exact = Minimizer.minimize(DFA.fromNFA(NFA.fromTree(RegexParser.parse(regex)), false));
        byte[] l = bytes(line);
        int got = exact.longestMatchEnd(l, from, l.length);
        check(String.format("%-14s dans %-16s depuis %2d -> fin %2d", regex, "\"" + line + "\"", from, got),
              got == expected);
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

    private static void check(String name, boolean cond) {
        total++;
        if (cond) { ok++; System.out.println("OK     " + name); }
        else System.out.println("ECHEC  " + name);
    }
}