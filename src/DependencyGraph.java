package MckTranslator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 
 */
public class DependencyGraph {
	public List<Vertex> verticies;

	public DependencyGraph() {
		verticies = new ArrayList<Vertex>();
	}

	public Vertex getVertex(String atom, int arity) {
		Vertex newVertex = new Vertex(atom, arity);
		if (verticies.contains(newVertex)) {
			return verticies.get(verticies.indexOf(newVertex));
		} else {
			return null;
		}
	}

	public boolean addVertex(String atom, int arity) {
		Vertex newVertex = new Vertex(atom, arity);
		return addVertex(newVertex);
	}

	public boolean addVertex(Vertex newVertex) {
		if (!verticies.contains(newVertex)) {
			return verticies.add(newVertex);
		} else {
			return false;
		}
	}

	public boolean hasVertex(String atom, int arity) {
		Vertex newVertex = new Vertex(atom, arity);
		return hasVertex(newVertex);
	}

	public boolean hasVertex(Vertex vertex) {
		return verticies.contains(vertex);
	}

	@Deprecated
	// Reads edge1 depends on edge2
	public boolean addEdge(Vertex from, Vertex to) {
		Edge newEdge = new Edge(to, from);
		if (!to.getNeighborhood().contains(newEdge)) {
			return to.getNeighborhood().add(new Edge(to, from));
		} else {
			return false;
		}
	}

	public void printGraph() {

		for (Vertex vertex : verticies) {
			if (vertex.getArity() > 0) {
				System.out.println("From vertex " + vertex.toString());
				for (Edge edge : vertex.getNeighborhood()) {
					System.out.println(edge.getToVertex().toString());
				}
				System.out.println();
			}
		}
	}
}