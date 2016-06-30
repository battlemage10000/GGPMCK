import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class MckTranslatorTest {
	
	String emptyGdlPath = "test/gdlii/empty.gdl";
	String testGdlPath = "test/gdlii/testGame.gdl";
	String dependencyTestGdlPath = "test/gdlii/dependencyTestGame.gdl";
	
	@Test
	public void loadEmptyGameDescription() {
		try{
			List<String> tokens = MckTranslator.tokenizer(emptyGdlPath);
			assertEquals(tokens.isEmpty(), true);
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void loadUnformatedValidGameDescription() {
		try{
			List<String> tokens = MckTranslator.tokenizer(testGdlPath);
			assertEquals(tokens.isEmpty(), false);
			
			List<String> expectedList = Arrays.asList("(",")","(","init",")","(","the","clause",")");
			assertEquals(tokens, expectedList);
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void validDepencencyGraphGeneration() {
		try{
			List<String> tokens = MckTranslator.tokenizer(dependencyTestGdlPath);
			
			for(String token : tokens)System.out.print(token+", ");
			
			MckTranslator.ParseTreeNode root = MckTranslator.expandParseTree(tokens);
			
			System.out.println(root.toString());
			
			DependencyGraph graph = MckTranslator.constructDependencyGraph(root);
			graph.printGraph();
			
			for(DependencyGraph.Vertex vertex : graph.verticies){
				if(vertex.getArity() > 0){
					System.out.println("Parameter " + vertex.toString() + " has domain: " + vertex.getDomain());
				}
			}
			//root = MckTranslator.groundClauses(root);
			
		}catch(URISyntaxException e) {
			e.printStackTrace();
		}catch(IOException e) {
			e.printStackTrace();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}