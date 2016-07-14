package translator.graph;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class Vertex<T> {
	private T data;
	private boolean visited;
	private List<Edge> neighborhood;
	
	// Getting rid of these
	private String atom;
	private int arity;
	private List<Vertex<T>> parameters;
	
	public Vertex(T data) {
		this.data = data;
		this.atom="";
		this.arity=0;
		this.visited=false;
		this.neighborhood=new ArrayList<Edge>();
		this.parameters=new ArrayList<Vertex<T>>();
	}
	
	/*Vertex(String atom, int arity) {
		this.atom = atom;
		this.arity = arity;
		this.neighborhood = new ArrayList<Edge>();
		this.visited = false;
		this.parameters = new ArrayList<Vertex<T>>();
	}*/
	
	public T getData(){
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

	/*public String getAtom() {
		return this.atom;
	}

	public int getArity() {
		return this.arity;
	}*/
	
	/**
	 * Returns the domain of the vertex
	 */
	/*public Set<String> getDomain(){
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
	}*/
	
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