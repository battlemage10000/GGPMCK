package translator.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 
 */
public class DependencyGraph<T> {
	public List<Vertex<T>> verticies;

	public DependencyGraph() {
		verticies = new ArrayList<Vertex<T>>();
	}

	/*public Vertex getVertex(String atom, int arity) {
		Vertex<T> newVertex = new Vertex(atom, arity);
		if (verticies.contains(newVertex)) {
			return verticies.get(verticies.indexOf(newVertex));
		} else {
			return null;
		}
	}*/
	
	public Vertex<T> getVertex(T data) {
		Vertex<T> newVertex = new Vertex<T>(data);
		if(verticies.contains(newVertex)){
			return verticies.get(verticies.indexOf(newVertex));
		}else{
			return null;
		}
	}

	/*public boolean addVertex(String atom, int arity) {
		Vertex<T> newVertex = new Vertex(atom, arity);
		return addVertex(newVertex);
	}*/

	public boolean addVertex(Vertex<T> newVertex) {
		if (!verticies.contains(newVertex)) {
			return verticies.add(newVertex);
		} else {
			return false;
		}
	}

	/*public boolean hasVertex(String atom, int arity) {
		Vertex<T> newVertex = new Vertex<T>(atom, arity);
		return hasVertex(newVertex);
	}*/

	public boolean hasVertex(Vertex<T> vertex) {
		return verticies.contains(vertex);
	}

	@Deprecated
	// Reads edge1 depends on edge2
	public boolean addEdge(Vertex<T> from, Vertex<T> to) {
		Edge newEdge = new Edge(to, from);
		if (!to.getNeighborhood().contains(newEdge)) {
			return to.getNeighborhood().add(new Edge(to, from));
		} else {
			return false;
		}
	}

	public void printGraph() {

		for (Vertex<T> vertex : verticies) {
			System.out.println("From vertex " + vertex.toString());
			for (Object edge : vertex.getNeighborhood()) {
				System.out.println(((Edge)edge).getToVertex().toString());
			}
			System.out.println();
		}
	}
}