package util.graph;

import java.util.Map;

import util.grammar.GdlNode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;

public class DependencyGraph {
	private final Map<String, ArrayList<String>> adjacencyMap;
	private final Map<String, Integer> stratumMap;

	public DependencyGraph() {
		adjacencyMap = new HashMap<String, ArrayList<String>>();
		stratumMap = new HashMap<String, Integer>();
	}

	public boolean hasTerm(String term) {
		return adjacencyMap.containsKey(term);
	}

	public void addTerm(String term) {
		if (!hasTerm(term)) {
			adjacencyMap.put(term, new ArrayList<String>());
		}
	}

	public ArrayList<String> getNeighbours(String term) {
		return adjacencyMap.get(term);
	}

	public int getStratum(String term) {
		if (stratumMap.containsKey(term)) {
			return stratumMap.get(term);
		} else {
			return -2;
		}
	}

	public void addEdge(String fromTerm, String toTerm) {
		if (!hasTerm(fromTerm)) {
			addTerm(fromTerm);
		}
		if (!hasTerm(toTerm)) {
			addTerm(toTerm);
		}
		if (!getNeighbours(fromTerm).contains(toTerm)) {
			getNeighbours(fromTerm).add(toTerm);
		}
	}

	public Map<String, ArrayList<String>> getDependencyMap() {
		return adjacencyMap;
	}

	public Map<String, Integer> getStratumMap() {
		return stratumMap;
	}

	public String dotEncodedGraph() {
		computeStratum();
		StringBuilder dot = new StringBuilder();
		dot.append("strict digraph {");

		// Declare nodes and assign attributes
		for (String from : adjacencyMap.keySet()) {
			dot.append(System.lineSeparator() + from + " [label=\"" + from + " " + stratumMap.get(from) + "\"]");
		}
		dot.append(System.lineSeparator());

		// Add edges
		for (String from : adjacencyMap.keySet()) {
			for (String to : adjacencyMap.get(from)) {
				dot.append(System.lineSeparator() + from + " -> " + to);
			}
		}

		dot.append(System.lineSeparator() + "}");

		return dot.toString();
	}

	public void addOldValue (String node, String child) {
		//if(node.equals(GdlNode.GDL_NEXT)){
		//	addEdge(GdlNode.GDL_NEXT, child + "_old");
		//} else if(node.equals(GdlNode.GDL_SEES)){
		//	addEdge(GdlNode.GDL_SEES, child + "_old");
		//} else {
		//	addEdge(node + "_old", child + "_old");
		//}
		if(child.equals(GdlNode.GDL_TRUE)){
			addEdge(node, GdlNode.GDL_TRUE + "_old");
			adjacencyMap.get(node).remove(GdlNode.GDL_TRUE);
		}
		for (String grandChild : adjacencyMap.get(child)) {
			addOldValue (child, grandChild);
		}
	}
	
	public void computeStratum() {
		LinkedList<String> unset = new LinkedList<String>();
		unset.addAll(adjacencyMap.get(GdlNode.GDL_NEXT));
		while (!unset.isEmpty()) {
			String toNode = unset.remove(0);
			addOldValue(GdlNode.GDL_NEXT, toNode);
		}
		unset.addAll(adjacencyMap.get(GdlNode.GDL_SEES));
		while (!unset.isEmpty()) {
			String toNode = unset.remove(0);
			addOldValue(GdlNode.GDL_SEES, toNode);
		}
		
		addEdge(GdlNode.GDL_TRUE, GdlNode.GDL_NEXT);
		
		unset = new LinkedList<String>();
		LinkedList<String> unsetAlt = new LinkedList<String>();
		for (String key : adjacencyMap.keySet()) {
			if (adjacencyMap.get(key).isEmpty()) {
				stratumMap.put(key, 0);
			} else {
				stratumMap.put(key, -1);
				unset.add(key);
			}
		}

		boolean end = false;
		boolean changed = false;
		while (!end) {
			for (String from : adjacencyMap.keySet()) {
				boolean unknownDep = false;
				int newStratum = -1;
				for (String to : adjacencyMap.get(from)) {
					if (from.equals(to)) {
						continue;
					} else if (stratumMap.get(to) < 0) {
						unsetAlt.add(from);
						unknownDep = true;
						break;
					} else if (stratumMap.get(to) > newStratum) {
						newStratum = stratumMap.get(to);
					}
				}
				if (!unknownDep) {
					if (from.equals(GdlNode.GDL_NEXT)) {
						stratumMap.put(from, newStratum + 2);
					} else {
						stratumMap.put(from, newStratum + 1);
					}
					changed = true;
				}
			}
			if (unsetAlt.isEmpty()) {
				end = true;
			} else if (!changed) {
				end = true;
			} else {
				unset = unsetAlt;
				unsetAlt = new LinkedList<String>();
				changed = false;
			}
		}
	}
}
