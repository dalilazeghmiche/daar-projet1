/**
 * Tests de l'etape 3 : une ligne contient-elle le motif ?
 * Chaque cas : RegEx, ligne, resultat attendu (celui de egrep).
 */
public class MatcherTest {

    private static int ok = 0, total = 0;

    public static void main(String[] args) {
        // Recherche de SOUS-CHAINE (pas la ligne entiere)
        check("Sargon",      "After the defeat, Sargon was",        true);
        check("Sargon",      "le district de Sargodha",              false);
        check("S(a|g|r)+on", "Sargon of Akkad",                      true);
        check("S(a|g|r)+on", "Son",                                  false); // + exige au moins 1 lettre
        check("S(a|g|r)+on", "Saaaagggrrron!",                       true);

        // Exemple du cours a|bc*
        check("a|bc*",       "xyz",                                  false);
        check("a|bc*",       "xbx",                                  true);  // "b" suffit (c* = 0 fois)
        check("a|bc*",       "babybcc",                              true);  // exemple du livre

        // Le point
        check("S.n",         "San",                                  true);
        check("S.n",         "Sn",                                   false);
        check("S.*n",        "Sn",                                   true);

        // Motifs qui acceptent le mot vide : TOUTE ligne correspond (meme vide), comme egrep
        check("a*",          "",                                     true);
        check("a*",          "xyz",                                  true);

        // Debut / fin de ligne
        check("ab",          "ab",                                   true);
        check("ab",          "xxab",                                 true);
        check("ab",          "a b",                                  false);

        // Chevauchement : la tentative ratee ne doit pas faire rater la suivante
        check("aab",         "aaab",                                 true);
        check("mami",        "maman mamami",                         true);

        System.out.println(ok + "/" + total + " tests reussis");
    }

    private static void check(String regex, String line, boolean expected) {
        total++;
        boolean got = new NFAMatcher(NFA.fromTree(RegexParser.parse(regex))).matches(line);
        if (got == expected) ok++;
        else System.out.println("ECHEC : \"" + regex + "\" sur \"" + line + "\" -> " + got
                                + " (attendu " + expected + ")");
    }
}