import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import translator.MckTranslator;
import util.GdlParser;
import util.grammar.GdlNode;
import util.graph.DomainGraph;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.net.URISyntaxException;

public class TranslatorTest {	
	static final String testGoals = "(<= (goal ?player 100) (true (win ?player))) (<= (goal red 50) (true (draw))) (<= (goal blue 50) (true (draw))) (<= (goal red 0) (true (not (win blue)))) (<= (goal blue 0) (true (not (win red))))";
	static final String testGoalGrounding = "(<= (goal ?player 100) (true (win ?player)))";

	static final String testGdlPath = "test/gdlii/paperScissorsRock.kif";
	static final String groundedTestGdlPath = "test/gdlii/paperScissorsRock.ground.kif";

	
	@Test
	public void testSimpleDependencyGraph(){
		List<String> tokens = null;
		try {
			tokens = GdlParser.tokenizeString(testGoals);
		} catch (IOException e) {
			e.printStackTrace();
		}

		GdlNode root = GdlParser.expandParseTree(tokens);

		Map<String, ArrayList<String>> dependencyMap = MckTranslator.constructDependencyGraph(root)
				.getDependencyMap();

		assertThat(dependencyMap.keySet(), hasItem("goal"));
		//assertThat(domainMap.keySet(), hasItem(not(new DomainGraph.Term("?1_?player", 0))));
		//assertThat(domainMap.get(new DomainGraph.Term("goal", 1)).size(), is(2));
		//assertThat(domainMap.get(new DomainGraph.Term("goal", 2)).size(), is(3));
	}
	
	@Test
	public void testSimpleDomainGraph() {
		List<String> tokens = null;
		try {
			tokens = GdlParser.tokenizeString(testGoals);
		} catch (IOException e) {
			e.printStackTrace();
		}

		GdlNode root = GdlParser.expandParseTree(tokens);

		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = MckTranslator.constructDomainGraph(root)
				.getMap();

		assertThat(domainMap.keySet(), hasItems(new DomainGraph.Term("goal", 0)));
		assertThat(domainMap.keySet(), hasItem(not(new DomainGraph.Term("?1_?player", 0))));
		assertThat(domainMap.get(new DomainGraph.Term("goal", 1)).size(), is(2));
		assertThat(domainMap.get(new DomainGraph.Term("goal", 2)).size(), is(3));
	}

	@Test
	public void testSimpleDomainGraphOnGdl() {
		List<String> tokens = null;
		try {
			tokens = GdlParser.tokenizeFile("res/gdlii/tictactoe.kif");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		GdlNode root = GdlParser.expandParseTree(tokens);

		DomainGraph domainGraph = MckTranslator.constructDomainGraph(root);
		GdlNode groundedRoot = MckTranslator.groundGdl(root, domainGraph);

		assertThat(MckTranslator.isVariableInTree(groundedRoot), is(false));
	}

	@Test
	public void testCreationOfGroundedClause() {
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = new HashMap<DomainGraph.Term, ArrayList<DomainGraph.Term>>();
		ArrayList<DomainGraph.Term> domain = new ArrayList<DomainGraph.Term>();
		domain.add(new DomainGraph.Term("red", 0));
		domain.add(new DomainGraph.Term("blue", 0));
		domainMap.put(new DomainGraph.Term("goal", 1), domain);
		domainMap.put(new DomainGraph.Term("win", 1), domain);

		List<String> tokens = new ArrayList<String>();
		try {
			tokens = GdlParser.tokenizeString(testGoalGrounding);
		} catch (IOException e) {
			e.printStackTrace();
		}

		String groundedClauses = MckTranslator.groundClause(GdlParser.expandParseTree(tokens), domainMap);

		assertThat(groundedClauses, is("(<= (goal red 100) (true (win red)))" + System.lineSeparator()
				+ "(<= (goal blue 100) (true (win blue)))" + System.lineSeparator()));
	}

	@Test
	public void testParseTreeIterator() {
		List<String> tokens = null;
		try {
			tokens = GdlParser.tokenizeString(testGoalGrounding);
		} catch (IOException e) {
			e.printStackTrace();
		}

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
					assertThat(node.getParent().getType(), is(GdlNode.GdlType.ROOT));
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
			MckTranslator.saveFile(root.toString(), "build/testGameAfterParse.gdl");

			// Check that gdlTokenizer, expandParseTree, ParseNode.toString and
			// saveFile are doing their job
			List<String> tokensAfterSave = GdlParser.tokenizeFile("build/testGameAfterParse.gdl");
			assertThat(tokens, is(tokensAfterSave));

			MckTranslator.saveFile(GdlParser.expandParseTree(tokensAfterSave).toString(),
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
			String lparse = MckTranslator.toLparse(root);
			System.out.println(lparse);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}