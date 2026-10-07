import java.util.Random;

/**
 * Tests de l'etape 4 : NFA -> DFA (methode des sous-ensembles).
 *
 *  1. Exemple du cours a|bc* en mode exact : comparaison avec Aho-Ullman Fig. 10.35.
 *  2. Mots acceptes / refuses par le DFA exact.
 *  3. Test "aleatoire" : sur des milliers de lignes, le DFA (mode search) doit donner
 *     EXACTEMENT les memes reponses que la simulation du NFA (etape 3, deja validee).
 */
public class DFATest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // 1. Exemple du cours
        DFA exact = DFA.fromNFA(NFA.fromTree(RegexParser.parse("a|bc*")), false);
        System.out.println("RegEx : a|bc*");
        System.out.println(exact);
        check("a|bc* : 4 etats en mode exact", exact.size() == 4);

        // 2. Mots entiers acceptes ou non par le DFA exact
        String[] oui = { "a", "b", "bc", "bcccc" };
        String[] non = { "", "ab", "c", "ba", "bcb", "aa" };
        for (String w : oui) check("a|bc* accepte \"" + w + "\"", exact.accepts(w));
        for (String w : non) check("a|bc* refuse \"" + w + "\"", !exact.accepts(w));

        // Exemple du TME
        DFA sargon = DFA.fromNFA(NFA.fromTree(RegexParser.parse("S(a|g|r)+on")), false);
        System.out.println("RegEx : S(a|g|r)+on");
        System.out.println(sargon);

        // 3. DFA (search) == NFA sur des lignes aleatoires
        String[] regexes = { "a|bc*", "S(a|g|r)+on", "(a|b)*abb", "a.c", "(ab|ba)*c",
                             "a*", "b(a|c)*b", "(a|b)*a(a|b)(a|b)", ".*c.*a" };
        Random rnd = new Random(42);
        char[] alphabet = { 'a', 'b', 'c', 'S', 'g', 'r', 'o', 'n', 'x' };
        for (String r : regexes) {
            NFA nfa = NFA.fromTree(RegexParser.parse(r));
            NFAMatcher viaNfa = new NFAMatcher(nfa);
            DFA viaDfa = DFA.fromNFA(nfa, true);
            boolean same = true;
            for (int k = 0; k < 5000 && same; k++) {
                StringBuilder line = new StringBuilder();
                int len = rnd.nextInt(15);
                for (int i = 0; i < len; i++) line.append(alphabet[rnd.nextInt(alphabet.length)]);
                String l = line.toString();
                if (viaNfa.matches(l) != viaDfa.matches(l)) {
                    System.out.println("  difference sur \"" + l + "\"");
                    same = false;
                }
            }
            check(String.format("%-20s NFA %2d etats -> DFA %2d etats, memes reponses", r, nfa.size(), viaDfa.size()), same);
        }

        System.out.println(ok + "/" + total + " tests reussis");
    }

    private static void check(String name, boolean cond) {
        total++;
        if (cond) { ok++; System.out.println("OK     " + name); }
        else System.out.println("ECHEC  " + name);
    }
}