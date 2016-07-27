import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;
import translator.MckTranslator;
import translator.Arguments;
import translator.graph.DependencyGraph;
import translator.graph.Vertex;
import translator.graph.Edge;
import java.util.List;
import java.util.Arrays;
import java.util.Queue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class MckTranslatorTest {
	
	String emptyGdlPath = "test/gdlii/empty.gdl";
	String testGdlPath = "test/gdlii/testGame.gdl";
	String dependencyTestGdlPath = "test/gdlii/dependencyTestGame.gdl";
	String groundedDependencyTestGdlPath = "test/gdlii/dependencyTestGroundedGame.gdl";
	
	@Test
	public void loadEmptyGameDescription() {
		try{
			List<String> tokens = MckTranslator.tokenizeFile(emptyGdlPath);
			assertThat(tokens.isEmpty(), is(true));
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void loadUnformatedValidTokens() {
		try{
			List<String> tokens = MckTranslator.tokenizeFile(testGdlPath);
			assertThat(tokens.isEmpty(), is(false));
			
			assertThat(tokens, is(Arrays.asList("(",")","(","init",")","(","the","clause",")")));
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void loadGameAndConstructParseTree(){
		try{
			List<String> tokens = MckTranslator.tokenizeFile(dependencyTestGdlPath);
			
			MckTranslator.ParseNode root = MckTranslator.expandParseTree(tokens);
			
			// TODO: add a gdl validator method based on this code
			Queue<MckTranslator.ParseNode> parseTreeValidatingQueue = new LinkedList<MckTranslator.ParseNode>();
			parseTreeValidatingQueue.add(root);
			
			while(!parseTreeValidatingQueue.isEmpty()){
				MckTranslator.ParseNode node = parseTreeValidatingQueue.remove();
				
				switch(node.getType()){
				case VARIABLE:
					assertThat(node.getAtom().charAt(0), is('?'));
				case CONSTANT:
					//assertThat(node.getAtom().charAt(0), is(not('?')));
					assertThat(node.getChildren().isEmpty(), is(true));
					break;
				case FORMULA:
					assertThat(node.getChildren().isEmpty(), is(false));
					break;
				case CLAUSE:
					assertThat(node.getParent().getType(), is(MckTranslator.GdlType.ROOT));
					break;
				}
				
				parseTreeValidatingQueue.addAll(node.getChildren());
			}
			
		}catch(IOException e){
			e.printStackTrace();
		}catch(URISyntaxException e){
			e.printStackTrace();
		}
	}
	
	@Test
	public void validDepencencyGraphGeneration() {
		try{
			List<String> tokens = MckTranslator.tokenizeFile(dependencyTestGdlPath);
			
			MckTranslator.ParseNode root = MckTranslator.expandParseTree(tokens);
			
			DependencyGraph<Arguments> graph = MckTranslator.constructDependencyGraph(root);
			graph.printGraph();
			
			
			for(Vertex<Arguments> vertex : graph.getVerticies()){
				
				System.out.println("Vertex: " + vertex.toString());
				
				for(Vertex<Arguments> domainVertex : vertex.getDomain()){
					
					System.out.println("  Element: " + domainVertex.toString());
					
				}
			
			}
			
			//for(Vertex<Arguments> vertex : graph.getVerticies()){
			//	Arguments data = vertex.getData();
			//	if(data.getArity() > 0){
			//		System.out.println("Parameter: " + data.toString() + " Domain:");
			//		for(String atom : getDomain(vertex)){
			//			System.out.println("  =: " + atom);
			//		}
			//	}
			//}
			//root = MckTranslator.groundClauses(root);
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private List<String> getDomain(Vertex<Arguments> vertex){
		List<String> domain = new ArrayList<String>();
		List<Vertex<Arguments>> visited = new LinkedList<Vertex<Arguments>>();
		LinkedList<Edge<Arguments>> queue = new LinkedList<Edge<Arguments>>();
		queue.addAll(vertex.getNeighborhood());
		
		while(!queue.isEmpty()){
			Edge<Arguments> edge = queue.remove();
			if(edge.getToVertex().getData().getArity() == 0){
				domain.add(edge.getToVertex().getData().getAtom());
				if(!visited.contains(edge.getToVertex())){
					visited.add(edge.getToVertex());
				}
			}else if(edge.getToVertex().getData().getArity() > 0){
				for(Edge<Arguments> childEdge : edge.getToVertex().getNeighborhood()){
					// TODO: This code goes into infinite loop
					//if(!queue.contains(childEdge) && !visited.contains(childEdge.getToVertex())){
						//queue.add(childEdge);
					//}
				}
			}
		}
		
		return domain;
	}
	
	//@Test
	public void mckTranslatorGdlTestAndSave(){
		try{
			List<String> tokens = MckTranslator.tokenizeFile(groundedDependencyTestGdlPath);
			
			MckTranslator.ParseNode root = MckTranslator.expandParseTree(tokens);
			
			MckTranslator.saveFile(root.toString(), "build-test/testGameAfterParse.gdl");
			// Check that tokenizer, expandParseTree, ParseNode.toString and saveFile are doing their job
			assertThat(tokens, is(MckTranslator.tokenizeFile("build-test/testGameAfterParse.gdl")));
			
			String mck = MckTranslator.toMck(root);
			
			MckTranslator.saveFile(mck, "build-test/mck-translation.mck");
			
			System.out.println(mck);
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}