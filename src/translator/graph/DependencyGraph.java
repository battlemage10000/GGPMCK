package translator.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class DependencyGraph<T> {
	public List<Vertex<T>> verticies;

	public DependencyGraph() {
		verticies = new ArrayList<Vertex<T>>();
	}

	public List<Vertex<T>> getVerticies() {
		return verticies;
	}

	public Vertex<T> getVertex(T data) {
		Vertex<T> newVertex = new Vertex<T>(data);
		if (verticies.contains(newVertex)) {
			return verticies.get(verticies.indexOf(newVertex));
		} else {
			return null;
		}
	}

	public boolean addVertex(Vertex<T> newVertex) {
		if (!verticies.contains(newVertex)) {
			return verticies.add(newVertex);
		} else {
			return false;
		}
	}

	public boolean hasVertex(Vertex<T> vertex) {
		return verticies.contains(vertex);
	}

	public void printGraph() {
		for (Vertex<T> vertex : verticies) {
			System.out.println("Vertex: " + vertex.toString());
			for (Edge edge : vertex.getNeighborhood()) {
				System.out.println("  Neighbour: " + edge.getToVertex().toString());
			}
		}
	}
}