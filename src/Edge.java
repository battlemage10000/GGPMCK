package MckTranslator;

public class Edge {
	private Vertex from, to;

	Edge(Vertex from, Vertex to) {
		this.from = from;
		this.to = to;
	}

	public Vertex getFromVertex() {
		return this.from;
	}

	public Vertex getToVertex() {
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
