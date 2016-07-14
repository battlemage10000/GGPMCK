import static org.junit.Assert.assertEquals;
import org.junit.Test;
import translator.graph.DependencyGraph;

public class DependencyGraphTest {

	@Test
	public void initializesEmpty(){
		DependencyGraph graph = new DependencyGraph();
		assertEquals(graph.verticies.isEmpty(), true);
	}
	
	@Test
	public void graphTest(){
		DependencyGraph graph = new DependencyGraph();
		
	}
}