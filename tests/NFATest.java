/**
 * Tests de l'etape 2 : RegEx -> arbre -> NFA.
 * On verifie le nombre d'etats attendu par la construction Aho-Ullman :
 *   lettre ou '.' : 2 etats
 *   R S          : etats(R) + etats(S)        (relies par une eps-transition)
 *   R | S        : etats(R) + etats(S) + 2
 *   R*           : etats(R) + 2
 */
public class NFATest {

    public static void main(String[] args) {
        check("a",     2);   // q0 --a--> q1
        check("ab",    4);   // q0 --a--> q1 --eps--> q2 --b--> q3
        check("a|b",   6);
        check("a*",    4);
        check("a|bc*", 10);  // exemple du cours (Aho-Ullman)

        // Exemple du TME, affiche aussi en Graphviz pour une future figure du rapport
        NFA nfa = NFA.fromTree(RegexParser.parse("S(a|g|r)+on"));
        System.out.println("RegEx : S(a|g|r)+on");
        System.out.println(nfa);
        System.out.println("--- Graphviz (pour le rapport) ---");
        System.out.println(NFA.fromTree(RegexParser.parse("a|bc*")).toDot());
    }

    private static void check(String regex, int expectedStates) {
        NFA nfa = NFA.fromTree(RegexParser.parse(regex));
        String verdict = nfa.size() == expectedStates ? "OK" : "ECHEC (attendu " + expectedStates + ")";
        System.out.println("RegEx : " + regex + "  ->  " + nfa.size() + " etats  " + verdict);
        System.out.println(nfa);
    }
}