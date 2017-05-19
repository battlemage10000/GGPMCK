import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import prover.GdlRuleSet;
import translator.MckFormat;
import translator.MckTranslator;
import util.GdlParser;
import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
import util.grammar.GdlLiteral;
import util.grammar.GdlNode;
import util.graph.DomainGraph;

public class TranslatorTest {
	
	private String montyHallGame = "res/gdlii/MontyHall.gdl";
	private String tictactoeGame = "res/gdl/tictactoe.kif";
	private String kriegtictactoeGame = "res/gdlii/KriegTicTacToe.gdl";
	private String biddingtictactoeGame = "res/gdl/bidding-tictactoe.gdl.txt";

	@Test
	public void testFormatMckNodeMethod() {
		// case 1
		GdlNode node = GdlParser.parseString("(sees player1 (move 1))").getChildren().get(0);
		assertThat(MckFormat.formatMckNode(node), is("sees_player1_move_1"));
		
		// case 2
		node = GdlParser.parseString("(sees player1 (does player2 (move_1)))").getChildren().get(0);
		assertThat(MckFormat.formatMckNode(node), is("sees_player1_does_player2_move_1"));
		
		// case 3
		node = GdlParser.parseString("(next (step 2))").getChildren().get(0);
		assertThat(MckFormat.formatMckNode(node), is("step_2"));
		
		// case 4
		node = GdlParser.parseString("(not (true (step 1)))").getChildren().get(0);
		assertThat(MckFormat.formatMckNode(node), is("neg step_1"));

		// case 5
		node = GdlParser.parseString("(++ 1 2)").getChildren().get(0);
		assertThat(MckFormat.formatMckNode(node), is("plusplus_1_2"));
	}
	
	@Test
	public void testFormatClauseMethod() throws Exception{
		// case 1
		String GDL_STRING = 
				"(role p1)"
				+ "(role p2)"
				+ "(init (step 1))"
				+ "(<= (legal ?p (move 1)) (role ?p))"
				+ "(<= (legal ?p (move 2)) (role ?p))"
				+ "(<= (next (step 2)) (true (step 1)))"
				+ "(<= (next (step 3)) (true (step 2)))"
				+ "(<= (next (step 4)) (true (step 3)))"
				+ "(<= terminal (true (step 4)) (does p1 (move 1)))"
				+ "(<= terminal (true (step 4)) (does p1 (move 2)))"
				+ "(<= terminal (true (step 4)) (does p2 (move 1)))"
				+ "(<= terminal (true (step 4)) (does p2 (move 2)))"
				+ "(<= (goal ?p 100) (role ?p) (true (step 4)))"
				+ "(<= (sees ?p (does ?q ?m)) (role ?p) (role ?q) (does ?q ?m))";
		
		GdlNode root = GdlParser.parseString(GDL_STRING);
		DomainGraph graph = GdlParser.constructDomainGraph(root);
		//System.out.println(graph.dotEncodedGraph());
		//System.out.println(root.toString());
		root = GdlParser.groundGdl(root, graph);
		//System.out.println(root.toString());
		GdlRuleSet ruleSet = new GdlRuleSet((Gdl)root);
		ruleSet.cullVariables(true);
		
		GdlNode headNode = root.getChildren().get(7).getChildren().get(0);
		ArrayList<GdlNode> bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(7));
		assertThat(MckFormat.formatClause(Collections.emptySet(), ruleSet, (GdlLiteral)headNode, false, true), is("step_2 := (step_1);"));
		
		headNode = root.getChildren().get(8).getChildren().get(0);
		bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(8));
		assertThat(MckFormat.formatClause(Collections.emptySet(), ruleSet, (GdlLiteral)headNode, false, true), is("step_3 := (step_2);"));
		
		headNode = root.getChildren().get(9).getChildren().get(0);
		bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(9));
		assertThat(MckFormat.formatClause(Collections.emptySet(), ruleSet, (GdlLiteral)headNode, false, true), is("step_4 := (step_3);"));
	}

	@Test
	public void testMontyHallTranslation() throws GDLSyntaxException{
		try {
			GdlNode mhRoot = GdlParser.parseFile(montyHallGame);
			DomainGraph graph = GdlParser.constructDomainGraph(mhRoot);
			//System.out.println(mhRoot.toString());
			mhRoot = GdlParser.groundGdl(mhRoot, graph);
			//System.out.println(mhRoot.toString());
			
			GdlRuleSet mhRuleSet = new GdlRuleSet((Gdl)mhRoot);
			mhRuleSet.cullVariables(true);
			
			
			MckTranslator mhTrans = new MckTranslator(mhRuleSet, true, false);
			//mhTrans.setProver(mhProver);
			
			String translation = mhTrans.toMck();
			//System.out.println("Number of contradictions: " + mhTrans.ATc.size());
			//System.out.println(translation);
			assertThat(translation, is(not("")));
		}catch (URISyntaxException e){
			e.printStackTrace();
		}catch (IOException e){
			e.printStackTrace();
		}
	}
	
	@Test
	public void testTicTacToeTranslation() throws IOException, URISyntaxException, GDLSyntaxException {
		GdlNode tttRoot = GdlParser.parseFile(tictactoeGame);
		tttRoot = GdlParser.groundGdl(tttRoot, GdlParser.constructDomainGraph(tttRoot));
		GdlRuleSet tttRuleSet = new GdlRuleSet((Gdl)tttRoot);
		tttRuleSet.cullVariables(true);

		MckTranslator tttTrans = new MckTranslator(tttRuleSet, false, false);
		String tttTranslation = tttTrans.toMck();
		assertThat(tttTrans.ATi, hasItem("control_white"));
		assertThat(tttTrans.ATi, not(hasItem("control_black")));
		assertThat(tttTranslation, is(not("")));
	}
	
	@Test
	public void testKriegTicTacToeTranslation() throws IOException, URISyntaxException, GDLSyntaxException {
		GdlNode ktttRoot = GdlParser.parseFile(kriegtictactoeGame);
		ktttRoot = GdlParser.groundGdl(ktttRoot, GdlParser.constructDomainGraph(ktttRoot));
		GdlRuleSet ktttRuleSet = new GdlRuleSet((Gdl)ktttRoot);
		ktttRuleSet.cullVariables(true);

		MckTranslator ktttTrans = new MckTranslator(ktttRuleSet, false, false);
		
		String ktttTranslation = ktttTrans.toMck();
		assertThat(ktttTrans.ATi, hasItem("control_xplayer"));
		assertThat(ktttTrans.ATi, not(hasItem("control_oplayer")));
		assertThat(ktttTranslation, is(not("")));
	}
	
	@Test
	public void testBiddingTicTacToeTranslation() throws IOException, URISyntaxException, GDLSyntaxException {
		GdlNode btttRoot = GdlParser.parseFile(biddingtictactoeGame);
		btttRoot = GdlParser.groundGdl(btttRoot, GdlParser.constructDomainGraph(btttRoot));
		GdlRuleSet btttRuleSet = new GdlRuleSet((Gdl)btttRoot);
		btttRuleSet.cullVariables(true);

		MckTranslator btttTrans = new MckTranslator(btttRuleSet, false, false);
		String btttTranslation = btttTrans.toMck();
		assertThat(btttTranslation, is(not("")));
	}
}
