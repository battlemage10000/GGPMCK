package translator.graph;

public class Edge<T> {
	private Vertex<T> from, to;

	public Edge(Vertex<T> from, Vertex<T> to) {
		this.from = from;
		this.to = to;
	}

	public Vertex<T> getFromVertex() {
		return this.from;
	}

	public Vertex<T> getToVertex() {
		return this.to;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof Edge) {
			Edge otherEdge = (Edge) obj;
			if (from.equals(otherEdge.getFromVertex()) && to.equals(otherEdge.getToVertex())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return from.toString() + "-" + to.toString();
	}
}
