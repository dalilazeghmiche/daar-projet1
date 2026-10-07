/**
 * Tests de l'etape 1 : on affiche l'arbre de chaque RegEx,
 * puis on verifie que les RegEx mal formees sont bien rejetees.
 */
public class ParserTest {

    public static void main(String[] args) {
        String[] valides = { "a", "ab", "a|b", "ab*", "(a|b)c", "a|bc*", "S(a|g|r)+on", "S.*n" };
        for (String r : valides) {
            RegexNode tree = RegexParser.parse(r);
            System.out.println("RegEx : " + r);
            System.out.println("Forme parenthesee : " + tree);
            System.out.print(tree.toTree());
            System.out.println();
        }

        String[] invalides = { "", "a|", "|a", "()", "(ab", "ab)", "*a", "a**b(" };
        int ok = 0;
        for (String r : invalides) {
            try {
                RegexParser.parse(r);
                System.out.println("ECHEC : \"" + r + "\" aurait du etre rejetee");
            } catch (IllegalArgumentException e) {
                ok++;
            }
        }
        System.out.println(ok + "/" + invalides.length + " RegEx invalides correctement rejetees");
    }
}