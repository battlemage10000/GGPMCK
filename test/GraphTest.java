import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;

import util.graph.DependencyGraph;
import util.graph.DomainGraph;

public class GraphTest {
	
	@Test
	public void dependencyGraphTest(){
		DependencyGraph graph = new DependencyGraph();
		graph.addEdge("goal", "win");
		
		assertThat(graph.getDependencyMap().keySet(), hasItems("goal", "win"));
		
		//System.out.println(graph.dotEncodedGraph());
	}
	
	@Test
	public void domainGraphTest(){
		DomainGraph domainGraph = new DomainGraph();
		domainGraph.addTerm("thing", 0);
		domainGraph.addTerm("thing", 1);
		domainGraph.addTerm("thing", 2);
		domainGraph.addTerm("thing", 3);
		domainGraph.addEdge("thing", 2, "thing", 0);
		domainGraph.addTerm("stuff", 2);
		domainGraph.addFunction("function", 2);
		domainGraph.addEdge("stuff", 3, "stuff", 1);
		domainGraph.addEdge("thing", 1, "stuff", 3);
		domainGraph.addEdge("stuff", 3, "const1", 0);
		domainGraph.addEdge("stuff", 3, "const2", 0);
		domainGraph.addEdge("thing", 1, "function", 0);
		
		assertThat(domainGraph.getNeighbours("thing", 2).size(), is(1));
		assertThat(domainGraph.getNeighbours("thing", 2).iterator().next(), is(new DomainGraph.Term("thing", 0)));
		assertThat(domainGraph.getNeighbours("something", 2).size(), is(0));
		
		assertThat(domainGraph.getDomain("stuff", 3).size(), is(2));
		assertThat(domainGraph.getDomain("thing", 1).size(), is(3));
		
		assertThat(domainGraph.hasTerm("function", 0), is(true));
		assertThat(domainGraph.hasTerm("function", 1), is(true));
		assertThat(domainGraph.hasTerm("function", 2), is(true));
	}
}