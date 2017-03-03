import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import prover.GdlRuleSet;
import util.GdlParser;
import util.grammar.GDLSyntaxException;
import util.grammar.GdlNode;
import util.grammar.GdlType;
import util.graph.DependencyGraph;
import util.graph.DomainGraph;
import util.graph.DomainGraph.Term;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.net.URISyntaxException;

public class ParserTest {
	static final String testGoals = "(<= (goal ?player 100) (true (win ?player))) (<= (goal red 50) (true (draw))) (<= (goal blue 50) (true (draw))) (<= (goal red 0) (true (not (win blue)))) (<= (goal blue 0) (true (not (win red))))";
	static final String testGoalGrounding = "(<= (goal ?player 100) (true (win ?player)))";

	static final String testGdlPath = "test/gdlii/paperScissorsRock.kif";
	static final String groundedTestGdlPath = "test/gdlii/paperScissorsRock.ground.kif";
	static final String largeGdlPath = "res/gdlii/mastermind.gdl";

	@Test
	public void testSimpleDependencyGraph() {
		List<String> tokens = null;
		tokens = GdlParser.tokenizeString(testGoals);

		GdlNode root = GdlParser.expandParseTree(tokens);

		Map<String, ArrayList<String>> dependencyMap = GdlParser.constructDependencyGraph(root).getDependencyMap();

		assertThat(dependencyMap.keySet(), hasItem("goal"));
		// assertThat(domainMap.keySet(), hasItem(not(new
		// DomainGraph.Term("?1_?player", 0))));
		// assertThat(domainMap.get(new DomainGraph.Term("goal", 1)).size(),
		// is(2));
		// assertThat(domainMap.get(new DomainGraph.Term("goal", 2)).size(),
		// is(3));
	}

	@Test
	public void testSimpleDomainGraph() {
		List<String> tokens = null;
		tokens = GdlParser.tokenizeString(testGoals);

		GdlNode root = GdlParser.expandParseTree(tokens);

		Map<Term, Set<Term>> domainMap = GdlParser.constructDomainGraph(root).getMap();

		assertThat(domainMap.keySet(), hasItems(new DomainGraph.Term("goal", 0)));
		assertThat(domainMap.keySet(), hasItem(not(new DomainGraph.Term("?1_?player", 0))));
		assertThat(domainMap.get(new DomainGraph.Term("goal", 1)).size(), is(2));
		assertThat(domainMap.get(new DomainGraph.Term("goal", 2)).size(), is(3));
	}

	@Test
	public void testSimpleDomainGraphOnGdl() throws IOException {
		List<String> tokens = null;
		try {
			tokens = GdlParser.tokenizeFile("res/gdlii/tictactoe.kif");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		GdlNode root = GdlParser.expandParseTree(tokens);

		DomainGraph domainGraph = GdlParser.constructDomainGraph(root);
		GdlNode groundedRoot = GdlParser.groundGdl(root, domainGraph);

		assertThat(GdlParser.isVariableInTree(groundedRoot), is(false));
	}

	@Test
	public void testCreationOfGroundedClause() throws IOException {
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = new HashMap<DomainGraph.Term, ArrayList<DomainGraph.Term>>();
		ArrayList<DomainGraph.Term> domain = new ArrayList<DomainGraph.Term>();
		domain.add(new DomainGraph.Term("red", 0));
		domain.add(new DomainGraph.Term("blue", 0));
		domainMap.put(new DomainGraph.Term("goal", 1), domain);
		domainMap.put(new DomainGraph.Term("win", 1), domain);

		GdlNode clause = GdlParser.parseString(testGoalGrounding);

		Map<String, Set<String>> constantMap = new HashMap<String, Set<String>>();
		Set<String> constantDomain = new HashSet<String>();
		constantDomain.add("red");
		constantDomain.add("blue");
		constantMap.put("?1_?player", constantDomain);
		String groundedClauses = GdlParser.groundClause(clause, constantMap , false);

		assertThat(groundedClauses, is("(<= (goal red 100) (true (win red)))" + System.lineSeparator()
				+ "(<= (goal blue 100) (true (win blue)))" + System.lineSeparator()));
	}

	@Test
	public void testParseTreeIterator() {
		List<String> tokens = null;
		tokens = GdlParser.tokenizeString(testGoalGrounding);

		GdlNode root = GdlParser.expandParseTree(tokens);

		StringBuilder sb = new StringBuilder();
		for (GdlNode node : root) {
			sb.append(node.getAtom() + " ");
		}
		assertThat(sb.toString(), is(" <= goal ?1_?player 100 true win ?1_?player "));
	}

	@Test
	public void loadGameAndConstructParseTree() {
		try {
			List<String> tokens = GdlParser.tokenizeFile(testGdlPath);
			GdlNode root = GdlParser.expandParseTree(tokens);

			// TODO: add a gdl validator method based on this code
			for (GdlNode node : root) {
				switch (node.getType()) {
				case VARIABLE:
					assertThat(node.getChildren().isEmpty(), is(true));
					assertThat(node.getAtom().charAt(0), is('?'));
					break;
				case CONSTANT:
					assertThat(node.getChildren().isEmpty(), is(true));
					assertThat(node.getAtom().charAt(0), is(not('?')));
					break;
				case FUNCTION:
					assertThat(node.getChildren().isEmpty(), is(false));
					break;
				case CLAUSE:
					assertThat(node.getParent().getType(), is(GdlType.ROOT));
					break;
				default:
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void mckTranslatorGdlTestAndSave() {
		try {
			List<String> tokens = GdlParser.tokenizeFile(groundedTestGdlPath);
			GdlNode root = GdlParser.expandParseTree(tokens);
			GdlParser.saveFile(root.toString(), "build/testGameAfterParse.gdl");

			// Check that gdlTokenizer, expandParseTree, ParseNode.toString and
			// saveFile are doing their job
			List<String> tokensAfterSave = GdlParser.tokenizeFile("build/testGameAfterParse.gdl");
			assertThat(tokens, is(tokensAfterSave));

			GdlParser.saveFile(GdlParser.expandParseTree(tokensAfterSave).toString(),
					"build/testGameAfterParseTwice.gdl");
			List<String> tokensAfterSaveTwice = GdlParser.tokenizeFile("build/testGameAfterParseTwice.gdl");
			assertThat(tokensAfterSave, is(tokensAfterSaveTwice));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void printParseTreeAsLparse() {
		try {
			List<String> tokens = GdlParser.tokenizeFile(testGdlPath);
			GdlNode root = GdlParser.expandParseTree(tokens);
			String lparse = GdlParser.toLparse(root);
			assertThat(lparse, not(""));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//@Test
	public void groundLargeGame() {
		try {
			GdlNode root = GdlParser.parseFile(largeGdlPath);
			DomainGraph domGraph = GdlParser.constructDomainGraph(root);

			GdlRuleSet ruleSet = new GdlRuleSet(GdlParser.groundGdl(root, domGraph));

			ruleSet.cullVariables(true);
			System.out.println(ruleSet.debug());
			String gdlString = ruleSet.toGdl();
			System.out.println(gdlString);
			System.out.println();

			DependencyGraph depGraph = GdlParser.constructDependencyGraph(root);
			gdlString = GdlParser.orderGdlRules(root, depGraph);
			//System.out.println(gdlString);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (GDLSyntaxException e) {
			e.printStackTrace();
		}
	}
}