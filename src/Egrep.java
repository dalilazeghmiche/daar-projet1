import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Clone de egrep (support partiel des ERE).
 *
 *    Usage : java Egrep [-nfa | -dfa] "RegEx" fichier.txt
 *    -dfa (defaut) : recherche avec le DFA (methode des sous-ensembles, mode .*R)
 *    -nfa          : recherche par simulation du NFA (utile pour comparer les performances)
 *    Si le DFA explose (trop d'etats), on revient automatiquement au NFA.
 *
 *
 * Affiche chaque ligne du fichier contenant une sous-chaine reconnue par la RegEx.
 * Le fichier est lu en OCTETS (comme "LC_ALL=C egrep") : chaque octet est un caractere,
 * et les lignes sont recopiees telles quelles, ce qui permet de comparer avec diff.
 *
 * Code de retour (comme egrep) : 0 = au moins une ligne trouvee, 1 = aucune, 2 = erreur.
 */
public class Egrep {
	
	/** Ce qui sait dire si une ligne contient le motif (NFA ou DFA). */
    interface LineMatcher {
        boolean matches(byte[] buf, int from, int to);
    }

    public static void main(String[] args) {
    	String mode = "-dfa";
    	if (args.length == 3 && (args[0].equals("-nfa") || args[0].equals("-dfa"))) {
    	    mode = args[0];
    	    args = new String[]{args[1], args[2]};
    	}
        if (args.length != 2) {
            System.err.println("Usage : java Egrep \"RegEx\" fichier.txt");
            System.exit(2);
        }

        // Etapes 1 et 2 : RegEx -> arbre -> NFA
        LineMatcher matcher;
        try {
            // La RegEx est aussi traitee en octets (un caractere non-ASCII = plusieurs octets),
            // pour rester coherent avec la lecture du fichier et avec LC_ALL=C egrep.
            String enc = System.getProperty("sun.jnu.encoding", "UTF-8");
            String regexBytes = new String(args[0].getBytes(java.nio.charset.Charset.forName(enc)),
                                           java.nio.charset.StandardCharsets.ISO_8859_1);
            RegexNode tree = RegexParser.parse(regexBytes);
            
            NFA nfa = NFA.fromTree(tree);
            matcher = buildMatcher(nfa, mode);
            
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        // Lecture du fichier
        byte[] text;
        try {
            text = Files.readAllBytes(Paths.get(args[1]));
        } catch (IOException e) {
            System.err.println("Impossible de lire " + args[1] + " : " + e.getMessage());
            System.exit(2);
            return;
        }

        // Etape 3 : recherche ligne par ligne
        boolean found = false;
        try (OutputStream out = new BufferedOutputStream(System.out, 1 << 16)) {
            int lineStart = 0;
            for (int i = 0; i <= text.length; i++) {
                boolean endOfLine = (i == text.length) || (text[i] == '\n');
                if (!endOfLine) continue;
                // derniere "ligne" vide apres le dernier '\n' : on l'ignore (comme egrep)
                if (i == text.length && lineStart == text.length) break;

                if (matcher.matches(text, lineStart, i)) {
                    out.write(text, lineStart, i - lineStart);
                    out.write('\n');
                    found = true;
                }
                lineStart = i + 1;
            }
        } catch (IOException e) {
            System.err.println("Erreur d'ecriture : " + e.getMessage());
            System.exit(2);
        }
        System.exit(found ? 0 : 1);
    }
    
    private static LineMatcher buildMatcher(NFA nfa, String mode) {
        if (mode.equals("-dfa")) {
            try {
                DFA dfa = DFA.fromNFA(nfa, true);
                return dfa::matches;
            } catch (IllegalStateException explosion) {
                System.err.println("Avertissement : " + explosion.getMessage() + ", simulation du NFA a la place");
            }
        }
        return new NFAMatcher(nfa)::matches;
    }
   
}


