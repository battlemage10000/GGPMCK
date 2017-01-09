import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;

import org.junit.Test;

import prover.Prover;
import util.GdlParser;
import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;

public class ProverTest {
	String mhGdlPath = "res/gdlii/MontyHall.gdl";
	String meierGdlPath = "res/gdlii/meier.gdl";
	String GDL_STRING = "(role p1)" + "(role p2)" + "(init (step 1))" + "(<= (legal ?p (move 1)) (role ?p))"
			+ "(<= (legal ?p (move 2)) (role ?p))" + "(<= (next (step 2)) (true (step 1)))"
			+ "(<= (next (step 3)) (true (step 2)))" + "(<= (next (step 4)) (true (step 3)))"
			+ "(<= terminal (true (step 4)) (does p1 (move 1)))" + "(<= terminal (true (step 4)) (does p1 (move 2)))"
			+ "(<= terminal (true (step 4)) (does p2 (move 1)))" + "(<= terminal (true (step 4)) (does p2 (move 2)))"
			+ "(<= (goal ?p 100) (role ?p) (true (step 4)))"
			+ "(<= (sees ?p (does ?q ?m)) (role ?p) (role ?q) (does ?q ?m))";

	String BETTER_VALUE_GDL_STRING = "(<= (better_value ?x ?y) (succ_value ?x ?y))"
			+ "(<= (better_value ?x ?z) (better_value ?y ?z) (succ_value ?x ?y))" + "(succ_value 1 0)"
			+ "(succ_value 2 1)" + "(succ_value 3 2)" + "(succ_value 4 3)";
	//+ "(succ_value 5 4)" + "(succ_value 6 5)"	+ "(succ_value 7 6)";

	@Test
	public void betterValueGameRulesetTest() throws IOException, URISyntaxException, GDLSyntaxException {
		// Gdl root = GdlParser.parseFile(mhGdlPath);
		Gdl root = GdlParser.parseString(BETTER_VALUE_GDL_STRING);
		root = GdlParser.groundGdl(root, GdlParser.constructDomainGraph(root));
		Prover prover = new Prover(root);
		prover.cullVariables(true);
		for (String head : prover.getRuleSet().keySet()) {
			if (prover.getRuleSet().get(head) != null) {
				assertThat(prover.getRuleSet().get(head).isEmpty(), is(true));
			}
		}
	}

	@Test
	public void testGameRulesetTest() throws IOException, URISyntaxException, GDLSyntaxException {
		// Gdl root = GdlParser.parseFile(mhGdlPath);
		Gdl root = GdlParser.parseString(GDL_STRING);
		root = GdlParser.groundGdl(root, GdlParser.constructDomainGraph(root));
		Prover prover = new Prover(root);
		prover.cullVariables(true);
		// System.out.println(prover.debug());
		// System.out.println();

		// for (String head : prover.getRuleSet().keySet()) {
		// System.out.println(head + " -> " + prover.getRuleSet().get(head));
		// }

		// System.out.println();
		// Model initState = prover.generateInitialModel();

		/*
		 * assertThat(initState.evaluate(GdlParser.parseString("(role p2)").
		 * getChild(0)), is(true));
		 * assertThat(initState.evaluate(GdlParser.parseString("(true (step 1))"
		 * ).getChild(0)), is(true)); assertThat(initState.evaluate(GdlParser.
		 * parseString("(legal p2 (move 1))").getChild(0)), is(true));
		 * assertThat(initState.evaluate(GdlParser.
		 * parseString("(<= (legal p2 (move 1)) (role p2))").getChild(0)),
		 * is(true));
		 * 
		 * assertThat(initState.contains("(role p1)"), is(true));
		 * assertThat(initState.contains("(role p2)"), is(true));
		 * assertThat(initState.contains("(true (step 1))"), is(true));
		 * 
		 * assertThat(initState.contains("terminal"), is(false));
		 * assertThat(initState.contains("(true (step 2))"), is(false));
		 * assertThat(initState.contains("(next (step 3))"), is(false));
		 * assertThat(initState.contains("(true (step 3))"), is(false));
		 * assertThat(initState.contains("(next (step 4))"), is(false));
		 * assertThat(initState.contains("(true (step 4))"), is(false));
		 */
	}

	@Test
	public void mhRulesetTest() throws GDLSyntaxException, IOException, URISyntaxException {
		Gdl root = GdlParser.parseFile(mhGdlPath);
		root = GdlParser.groundGdl(root, GdlParser.constructDomainGraph(root));
		Prover prover = new Prover(root);
		prover.cullVariables(true);

		for (String head : prover.getRuleSet().keySet()) {
			System.out.println(head + " -> " + prover.getRuleSet().get(head));
		}

		assertThat(prover.getRuleSet().keySet().contains("terminal"), is(true));
	}

	@Test
	public void meierRulesetTest() throws GDLSyntaxException, IOException, URISyntaxException {
		Gdl root = GdlParser.parseFile(meierGdlPath);
		root = GdlParser.groundGdl(root, GdlParser.constructDomainGraph(root));
		Prover prover = new Prover(root, true);
		prover.cullVariables(true);
		// System.out.println();

		String better_values = "(better_values ";
		String succ_values = "(succ_values ";
		String distinct = "(distinct ";
		for (String head : prover.getRuleSet().keySet()) {
			if (!prover.getRuleSet().get(head).isEmpty()) {
				// System.out.println(head + " -> " +
				// prover.getRuleSet().get(head));
			}
			if (head.length() >= better_values.length()
					&& head.substring(0, better_values.length()).equals(better_values)) {
				// assertThat(prover.getRuleSet().get(head).isEmpty(),
				// is(true));
			}
			for (Set<String> disjunct : prover.getRuleSet().get(head)) {
				for (String literal : disjunct) {
					if (literal.length() >= distinct.length()
							&& literal.substring(0, distinct.length()).equals(distinct)) {
						// System.out.println(literal);
					}
				}
			}
		}
		// System.out.println(prover.debug());
	}
}
