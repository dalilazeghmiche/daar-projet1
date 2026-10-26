# Projet 1 DAAR - clone de egrep
#   make          : compile et cree egrep.jar
#   make test     : tests unitaires (parser, NFA, DFA, minimisation, KMP, Boyer-Moore)
#   make compare  : compare la sortie avec le vrai egrep sur tests/data (Linux/macOS)
#   make demo     : exemple du sujet, avec numeros de ligne et surlignage
#   make clean

SRC   = $(wildcard src/*.java)
TESTS = $(wildcard tests/*.java)
BIN   = bin
JAR   = egrep.jar

all: $(JAR)

$(JAR): $(SRC)
	@mkdir -p $(BIN)
	javac -d $(BIN) $(SRC)
	printf "Main-Class: Egrep\n" > $(BIN)/manifest.txt
	jar cfm $(JAR) $(BIN)/manifest.txt -C $(BIN) .

# on recompile aussi src/ : l'archive rendue contient egrep.jar mais pas bin/
test: $(JAR)
	@mkdir -p $(BIN)
	javac -d $(BIN) $(SRC) $(TESTS)
	@for t in ParserTest NFATest DFATest MinimizerTest MatcherTest LocalisationTest KMPTest BoyerMooreTest; do \
	    echo "===== $$t"; java -cp $(BIN) $$t | tail -1; done

compare: $(JAR)
	bash tests/compare_egrep.sh

demo: $(JAR)
	java -jar $(JAR) -n --color --info "S(a|g|r)+on" tests/data/56667.txt

clean:
	rm -rf $(BIN) $(JAR)

.PHONY: all test compare demo clean
