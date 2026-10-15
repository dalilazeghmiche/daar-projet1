import java.util.Arrays;
import java.util.Random;

/**
 * Tests de KMP :
 *  1. Tableaux de retenue identiques a ceux du cours 2.
 *  2. Recherche sur les exemples du cours et du TME2.
 *  3. Detection automatique : facteur ou non.
 *  4. Test differentiel : KMP == DFA sur des lignes aleatoires (alphabet tres petit,
 *     pour provoquer beaucoup de recouvrements, le cas difficile pour KMP).
 */
public class KMPTest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // 1. Tableaux du cours
        carry("mami",    new int[]{-1, 0, -1, 1, 0});
        carry("mamamia", new int[]{-1, 0, -1, 0, -1, 3, 0, 0});
        carry("chicha",  new int[]{-1, 0, 0, -1, 0, 2, 0});
        carry("Sargon",  new int[]{-1, 0, 0, 0, 0, 0, 0});
        for (String f : new String[]{"Chihuahua", "Pizzi", "Pepperoni", "Pizza", "Poppers"})
            System.out.println("       " + f + " -> " + Arrays.toString(new KMP(bytes(f)).carry()));

        // 2. Recherche
        match("Sargon", "After the defeat of Shabaka, Sargon was", true);
        match("Sargon", "le district de Sargodha",                  false);
        match("mami",   "maman mame mia ! mm maaah !",              false);  // exemple du cours
        match("mami",   "maman mamami",                             true);
        match("Chihuahua", "a Chihuahua dog",                       true);
        match("Pizza",  "Pizzi Pizz",                               false);
        match("aab",    "aaab",                                     true);

        // 3. Detection automatique
        literal("Sargon", true);
        literal("S(a|g|r)+on", false);
        literal("S.n", false);
        literal("ab*", false);
        literal("(ab)c", true);     // parentheses seules : toujours un facteur

        // 4. Test differentiel KMP == DFA
        Random rnd = new Random(3);
        String[] factors = { "aab", "abab", "aaaa", "abaab", "babba", "ab", "a" };
        for (String f : factors) {
            KMP kmp = new KMP(bytes(f));
            DFA dfa = DFA.fromNFA(NFA.fromTree(RegexParser.parse(f)), true);
            boolean same = true;
            for (int k = 0; k < 20000 && same; k++) {
                StringBuilder sb = new StringBuilder();
                int len = rnd.nextInt(20);
                for (int i = 0; i < len; i++) sb.append(rnd.nextBoolean() ? 'a' : 'b');
                String l = sb.toString();
                if (kmp.matches(l) != dfa.matches(l)) { System.out.println("  difference sur " + l); same = false; }
            }
            check("KMP == DFA sur 20000 lignes aleatoires : " + f, same);
        }

        System.out.println(ok + "/" + total + " tests reussis");
    }

    private static void carry(String f, int[] expected) {
        int[] got = new KMP(bytes(f)).carry();
        check(f + " -> " + Arrays.toString(got), Arrays.equals(got, expected));
    }

    private static void match(String f, String line, boolean expected) {
        check("\"" + f + "\" dans \"" + line + "\" : " + expected, new KMP(bytes(f)).matches(line) == expected);
    }

    private static void literal(String regex, boolean expected) {
        boolean got = KMP.literalOf(RegexParser.parse(regex)) != null;
        check(regex + (expected ? " est un facteur -> KMP" : " n'est pas un facteur -> automate"), got == expected);
    }

    private static byte[] bytes(String s) { return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1); }

    private static void check(String name, boolean cond) {
        total++;
        if (cond) { ok++; System.out.println("OK     " + name); }
        else System.out.println("ECHEC  " + name);
    }
}