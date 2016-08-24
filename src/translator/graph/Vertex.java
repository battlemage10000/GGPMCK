package translator.graph;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

public class Vertex<T> {
	private T data;
	boolean visited;
	private List<Edge> neighborhood;

	public Vertex(T data) {
		this.data = data;
		this.visited = false;
		this.neighborhood = new ArrayList<Edge>();
	}

	public T getData() {
		return data;
	}

	public List<Edge> getNeighborhood() {
		return this.neighborhood;
	}

	public Edge addNeighbor(Vertex<T> neighbor) {
		Edge newEdge = new Edge(this, neighbor);
		if (neighbor == null) {
			return null;
		}
		if (!neighborhood.contains(newEdge)) {
			neighborhood.add(newEdge);
		}
		return newEdge;
	}

	/*
	 * Set this vertex as root and construct a dependency tree from directed
	 * dependency graph
	 */
	public List<Vertex<?>> getDomain() {
		List<Vertex<?>> domain = new ArrayList<Vertex<?>>();

		LinkedList<Edge> queue = new LinkedList<Edge>();
		queue.addAll(neighborhood);

		while (!queue.isEmpty()) {
			Edge edge = queue.remove();

			if (!domain.contains(edge.getToVertex())) {
				queue.addAll(edge.getToVertex().getNeighborhood());
				domain.add((Vertex<?>) edge.getToVertex());
			}
		}

		return domain;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof Vertex) {
			return this.toString().equals(obj.toString());
		}
		return false;
	}

	@Override
	public String toString() {
		return data.toString();
	}
}