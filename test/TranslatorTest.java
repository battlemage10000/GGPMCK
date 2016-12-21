import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import translator.MckTranslator;
import util.GdlParser;
import util.grammar.GdlNode;
import util.graph.DomainGraph;

public class TranslatorTest {
	
	private String montyHallGame = "res/gdlii/MontyHall.gdl";

	@Test
	public void testFormatMckNodeMethod() {
		// case 1
		GdlNode node = GdlParser.parseString("(sees player1 (move 1))").getChildren().get(0);
		assertThat(MckTranslator.formatMckNode(node), is("sees_player1_move_1"));
		
		// case 2
		node = GdlParser.parseString("(sees player1 (does player2 (move_1)))").getChildren().get(0);
		assertThat(MckTranslator.formatMckNode(node), is("sees_player1_does_player2_move_1"));
		
		// case 3
		node = GdlParser.parseString("(next (step 2))").getChildren().get(0);
		assertThat(MckTranslator.formatMckNode(node), is("step_2"));
		
		// case 4
		node = GdlParser.parseString("(not (true (step 1)))").getChildren().get(0);
		assertThat(MckTranslator.formatMckNode(node), is("neg step_1"));

		// case 5
		node = GdlParser.parseString("(++ 1 2)").getChildren().get(0);
		assertThat(MckTranslator.formatMckNode(node), is("plusplus_1_2"));
	}
	
	@Test
	public void testFormatClauseMethod(){
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
		System.out.println(root.toString());
		root = GdlParser.groundGdl(root, graph);
		System.out.println(root.toString());
		MckTranslator translator = new MckTranslator(root, false);
		
		GdlNode headNode = root.getChildren().get(7).getChildren().get(0);
		ArrayList<GdlNode> bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(7));
		assertThat(translator.formatClause(headNode, bodyList) , is("\nstep_2 := (step_1);"));
		
		headNode = root.getChildren().get(8).getChildren().get(0);
		bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(8));
		assertThat(translator.formatClause(headNode, bodyList) , is("\nstep_3 := (step_2);"));
		
		headNode = root.getChildren().get(9).getChildren().get(0);
		bodyList = new ArrayList<GdlNode>();
		bodyList.add(root.getChildren().get(9));
		assertThat(translator.formatClause(headNode, bodyList) , is("\nstep_4 := (step_3);"));
	}

	@Test
	public void testMontyHallTranslation(){
		try {
			GdlNode mhRoot = GdlParser.parseFile(montyHallGame);
			DomainGraph graph = GdlParser.constructDomainGraph(mhRoot);
			System.out.println(mhRoot.toString());
			mhRoot = GdlParser.groundGdl(mhRoot, graph);
			System.out.println(mhRoot.toString());
			
			
		}catch (URISyntaxException e){
			e.printStackTrace();
		}catch (IOException e){
			e.printStackTrace();
		}
	}
}
