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


public class Vertex {
	private List<Edge> neighborhood;
	private String atom;
	private int arity;
	boolean visited;
	private List<Vertex> parameters;

	Vertex(String atom, int arity) {
		this.atom = atom;
		this.arity = arity;
		this.neighborhood = new ArrayList<Edge>();
		this.visited = false;
		this.parameters = new ArrayList<Vertex>();
	}

	public List<Edge> getNeighborhood() {
		return this.neighborhood;
	}

	public Edge addNeighbor(Vertex neighbor) {
		Edge newEdge = new Edge(this, neighbor);
		if (neighbor == null) {
			return null;
		}
		if (!neighborhood.contains(newEdge)) {
			neighborhood.add(newEdge);
		}
		return newEdge;
	}

	public String getAtom() {
		return this.atom;
	}

	public int getArity() {
		return this.arity;
	}
	
	/**
	 * Returns the domain of the vertex
	 */
	public Set<String> getDomain(){
		Set<String> domain = new HashSet<String>();
		for(Edge edge : neighborhood){
			// Recursive method which uses the boolean variable visited to counter cycles in graph
			if(!visited){
				visited = true;
				Set<String> neighborDomain = edge.getToVertex().getDomain();
				if(edge.getToVertex().getArity() == 0) {
					Vertex para1 = new Vertex(edge.getToVertex().getAtom(), 1);
					if(!parameters.isEmpty()){
						for(Vertex param : parameters)domain.addAll(param.getDomain());
					} else {
						domain.add(edge.getToVertex().getAtom());
					}
				}else{
					domain.addAll(neighborDomain);
				}
				visited = false;
			}
		}
		return domain;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Vertex) {
			return this.toString().equals(obj.toString());
		}
		return false;
	}

	@Override
	public String toString() {
		return atom + "[" + arity + "]";
	}
}

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
}