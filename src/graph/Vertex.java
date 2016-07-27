package translator.graph;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

public class Vertex<T> {
	private T data;
	private boolean visited;
	private List<Edge<T>> neighborhood;
	
	public Vertex(T data) {
		this.data = data;
		this.visited = false;
		this.neighborhood = new ArrayList<Edge<T>>();
	}
	
	public T getData(){
		return data;
	}

	public List<Edge<T>> getNeighborhood() {
		return this.neighborhood;
	}

	public Edge<T> addNeighbor(Vertex<T> neighbor) {
		Edge<T> newEdge = new Edge<T>(this, neighbor);
		if (neighbor == null) {
			return null;
		}
		if (!neighborhood.contains(newEdge)) {
			neighborhood.add(newEdge);
		}
		return newEdge;
	}
	
	/**
	 * Returns the domain of the vertex
	 * TODO: 
	 * @Deprecated
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
	
	/* 
	 * Set this vertex as root and construct a dependency tree from directed dependency graph
	 */	
	public List<Vertex<T>> getDomain(){
		List<Vertex<T>> domain = new ArrayList<Vertex<T>>();
		
		LinkedList<Edge<T>> queue = new LinkedList<Edge<T>>();
		queue.addAll(neighborhood);
		
		while(!queue.isEmpty()){
			Edge<T> edge = queue.remove();
			
			if(!domain.contains(edge.getToVertex())){
				queue.addAll(edge.getToVertex().getNeighborhood());
				domain.add(edge.getToVertex());
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