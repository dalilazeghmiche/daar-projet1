import java.util.Arrays;
import java.util.Random;

/**
 * Tests de Boyer-Moore :
 *  1. Tables du mauvais caractere et du bon suffixe sur des exemples calcules a la main.
 *  2. Recherche sur les exemples du TME2 (Chihuahua, Pizzi, Pepperoni, Pizza, Poppers).
 *  3. Position trouvee : la PREMIERE occurrence, comme KMP.
 *  4. Test differentiel BM == KMP == DFA sur des lignes aleatoires (petit alphabet,
 *     beaucoup de recouvrements : le cas ou la table du bon suffixe travaille le plus).
 */
public class BoyerMooreTest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // 1. Tables
        //   "Sargon" : derniere lettre exclue, distance a la fin du facteur
        int[] bad = BoyerMoore.badCharacter(bytes("Sargon"));
        check("bad(Sargon) : S=5 a=4 r=3 g=2 o=1 n=6 x=6",
              bad['S'] == 5 && bad['a'] == 4 && bad['r'] == 3 && bad['g'] == 2
              && bad['o'] == 1 && bad['n'] == 6 && bad['x'] == 6);
        //   exemple classique de Charras-Lecroq : GCAGAGAG -> [7,7,7,2,7,4,7,1]
        good("GCAGAGAG", new int[]{7, 7, 7, 2, 7, 4, 7, 1});
        // abab : echec en position 2 apres avoir reconnu "b" -> decalage 4, car l'autre "b"
        //        (position 1) est precede du meme 'a' qui vient d'echouer (regle "forte")
        good("abab",     new int[]{2, 2, 4, 1});
        good("aaaa",     new int[]{1, 2, 3, 4});
        for (String f : new String[]{"Chihuahua", "Pizzi", "Pepperoni", "Pizza", "Poppers"})
            System.out.println("       " + f + " -> bon suffixe " + Arrays.toString(new BoyerMoore(bytes(f)).good()));

        // 2. Recherche (TME2)
        match("Chihuahua", "a little Chihuahua dog",   true);
        match("Chihuahua", "Chihuahu a",               false);
        match("Pizzi",     "Pizza Pizzeria Pizzi",     true);
        match("Pizza",     "Pizzi Pizz",               false);
        match("Pepperoni", "Peppers and Pepperoni",    true);
        match("Poppers",   "Popper's",                 false);
        match("Sargon",    "le district de Sargodha",  false);
        match("a",         "a",                        true);
        match("abc",       "ab",                       false);   // ligne plus courte que le facteur

        // 3. Position de la premiere occurrence
        position("on", "Sargon of Babylon", 4);
        position("aa", "baaaa", 1);
        position("xyz", "abc", -1);

        // 4. Test differentiel
        Random rnd = new Random(7);
        String[] factors = { "aab", "abab", "aaaa", "abaab", "babba", "ab", "a", "abaababa" };
        for (String f : factors) {
            BoyerMoore bm = new BoyerMoore(bytes(f));
            KMP kmp = new KMP(bytes(f));
            DFA dfa = DFA.fromNFA(NFA.fromTree(RegexParser.parse(f)), true);
            boolean same = true;
            for (int k = 0; k < 20000 && same; k++) {
                StringBuilder sb = new StringBuilder();
                int len = rnd.nextInt(25);
                for (int i = 0; i < len; i++) sb.append(rnd.nextBoolean() ? 'a' : 'b');
                byte[] l = bytes(sb.toString());
                int p1 = bm.find(l, 0, l.length), p2 = kmp.find(l, 0, l.length);
                if (p1 != p2 || (p1 >= 0) != dfa.matches(l, 0, l.length)) {
                    System.out.println("  difference sur " + sb + " : BM=" + p1 + " KMP=" + p2);
                    same = false;
                }
            }
            check("BM == KMP == DFA sur 20000 lignes aleatoires : " + f, same);
        }

        System.out.println(ok + "/" + total + " tests reussis");
    }

    private static void good(String f, int[] expected) {
        int[] got = BoyerMoore.goodSuffix(bytes(f));
        check("bon suffixe(" + f + ") -> " + Arrays.toString(got), Arrays.equals(got, expected));
    }

    private static void match(String f, String line, boolean expected) {
        check("\"" + f + "\" dans \"" + line + "\" : " + expected,
              new BoyerMoore(bytes(f)).matches(line) == expected);
    }

    private static void position(String f, String line, int expected) {
        byte[] l = bytes(line);
        int got = new BoyerMoore(bytes(f)).find(l, 0, l.length);
        check("position de \"" + f + "\" dans \"" + line + "\" = " + got, got == expected);
    }

    private static byte[] bytes(String s) { return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1); }

    private static void check(String name, boolean cond) {
        total++;
        if (cond) { ok++; System.out.println("OK     " + name); }
        else System.out.println("ECHEC  " + name);
    }
}