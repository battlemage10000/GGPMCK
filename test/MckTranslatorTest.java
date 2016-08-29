import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import translator.GdlParser;
import translator.MckTranslator;
import translator.grammar.GdlNode;
import translator.graph.DomainGraph;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Queue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.IOException;
import java.net.URISyntaxException;

public class MckTranslatorTest {
	
	String emptyGdlPath = "test/gdlii/empty.gdl";
	String testGdlPath = "test/gdlii/testGame.gdl";
	String dependencyTestGdlPath = "test/gdlii/dependencyTestGame.gdl";
	String groundedDependencyTestGdlPath = "test/gdlii/dependencyTestGroundedGame.gdl";
	
	String testGoals = "(<= (goal ?player 100) (true (win ?player))) (<= (goal red 50) (true (draw))) (<= (goal blue 50) (true (draw))) (<= (goal red 0) (true (not (win blue)))) (<= (goal blue 0) (true (not (win red))))";
	String testGoalGrounding = "(<= (goal ?player 100) (true (win ?player)))";
	
	@SuppressWarnings("unused")
	@Test
	public void testSimpleDomainGraph(){
		List<String> tokens = null;
		try{
			tokens = GdlParser.tokenizeString(testGoals);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		GdlNode root = GdlParser.expandParseTree(tokens);
		//MckTranslator.printParseTreeTypes(root);
		
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = MckTranslator.constructDomainGraph(root).getDomainMap();
		
		for(DomainGraph.Term term : domainMap.keySet()){
			//System.out.println("From: " + term.toString());
			for(DomainGraph.Term dependency : domainMap.get(term)){
				//System.out.println("  To: " + dependency.toString());
			}
		}
	}
	
	@SuppressWarnings("unused")
	@Test
	public void testSimpleDomainGraphOnGdl(){
		List<String> tokens = null;
		try{
			tokens = GdlParser.tokenizeFile("res/gdlii/tictactoe.kif");
		}catch(IOException e){
			e.printStackTrace();
		}catch(URISyntaxException e){
			e.printStackTrace();
		}
		
		GdlNode root = GdlParser.expandParseTree(tokens);
		//MckTranslator.printParseTreeTypes(root);
		
		DomainGraph domainGraph = MckTranslator.constructDomainGraph(root);
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = domainGraph.getDomainMap();
		
		for(DomainGraph.Term term : domainMap.keySet()){
			//System.out.println("From: " + term.toString());
			for(DomainGraph.Term dependency : domainMap.get(term)){
				//System.out.println("  To: " + dependency.toString());
			}
		}
		
		GdlNode groundedRoot = MckTranslator.groundGdl(root, domainGraph);
		//MckTranslator.printParseTreeTypes(groundedRoot);
	}
	
	@Test
	public void testCreationOfGroundedClause(){
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = new HashMap<DomainGraph.Term, ArrayList<DomainGraph.Term>>();
		ArrayList<DomainGraph.Term> domain = new ArrayList<DomainGraph.Term>();
		domain.add(new DomainGraph.Term("red", 0));
		domain.add(new DomainGraph.Term("blue", 0));
		domainMap.put(new DomainGraph.Term("goal", 1), domain);
		domainMap.put(new DomainGraph.Term("win", 1), domain);
		
		List<String> tokens = new ArrayList<String>();
		try{
			tokens = GdlParser.tokenizeString(testGoalGrounding);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		String groundedClauses = MckTranslator.groundClause(GdlParser.expandParseTree(tokens), domainMap);
		
		assertThat(groundedClauses, is(" (<= (goal red 100) (true (win red))) (<= (goal blue 100) (true (win blue)))"));
	}
	
	@Test
	public void testParseTreeIterator(){
		List<String> tokens = null;
		try{
			tokens = GdlParser.tokenizeString(testGoalGrounding);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		GdlNode root = GdlParser.expandParseTree(tokens);
		
		StringBuilder sb = new StringBuilder();
		for(GdlNode node : root){
			sb.append(node.getAtom() + " ");
		}
		assertThat(sb.toString(), is(" <= goal ?player___1 100 true win ?player___1 "));
	}
	
	//@Test
	public void loadEmptyGameDescription() {
		try{
			List<String> tokens = GdlParser.tokenizeFile(emptyGdlPath);
			assertThat(tokens.isEmpty(), is(true));
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	//@Test
	public void loadUnformatedValidTokens() {
		try{
			List<String> tokens = GdlParser.tokenizeFile(testGdlPath);
			assertThat(tokens.isEmpty(), is(false));
			
			assertThat(tokens, is(Arrays.asList("(",")","(","role","player1",")","(","the","clause",")")));
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void loadGameAndConstructParseTree(){
		try{
			List<String> tokens = GdlParser.tokenizeFile(dependencyTestGdlPath);
			GdlNode root = GdlParser.expandParseTree(tokens);
			
			// TODO: add a gdl validator method based on this code
			Queue<GdlNode> parseTreeValidatingQueue = new LinkedList<GdlNode>();
			parseTreeValidatingQueue.add(root);
			
			while(!parseTreeValidatingQueue.isEmpty()){
				GdlNode node = parseTreeValidatingQueue.remove();
				
				switch(node.getType()){
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
					assertThat(node.getParent().getType(), is(MckTranslator.GdlType.ROOT));
					break;
				default:
				}
				
				parseTreeValidatingQueue.addAll(node.getChildren());
			}
			
		}catch(IOException e){
			e.printStackTrace();
		}catch(URISyntaxException e){
			e.printStackTrace();
		}
	}
	
	/*
	 * 
	 */
	@Test
	public void mckTranslatorGdlTestAndSave(){
		try{
			List<String> tokens = GdlParser.tokenizeFile(groundedDependencyTestGdlPath);
			
			GdlNode root = GdlParser.expandParseTree(tokens);
			
			MckTranslator.saveFile(root.toString(), "build/testGameAfterParse.gdl");
			// Check that gdlTokenizer, expandParseTree, ParseNode.toString and saveFile are doing their job
			List<String> tokensAfterSave = GdlParser.tokenizeFile("build/testGameAfterParse.gdl");
			assertThat(tokens, is(tokensAfterSave));
			
			MckTranslator.saveFile(GdlParser.expandParseTree(tokensAfterSave).toString(), "build/testGameAfterParseTwice.gdl");
			List<String> tokensAfterSaveTwice = GdlParser.tokenizeFile("build/testGameAfterParseTwice.gdl");
			assertThat(tokensAfterSave, is(tokensAfterSaveTwice));
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	@Test
	public void printParseTreeAsLparse(){
		try{
			List<String> tokens = GdlParser.tokenizeFile(dependencyTestGdlPath);
			
			GdlNode root = GdlParser.expandParseTree(tokens);
			
			//MckTranslator.printParseTreeTypes(root);
			
			String lparse = MckTranslator.toLparse(root);
			//System.out.println(lparse);
			
			MckTranslator.saveFile(lparse, "build/ungrounded.lp");
		}catch(URISyntaxException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
}