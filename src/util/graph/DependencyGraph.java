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
			adjacencyMap.put(term.intern(), new ArrayList<String>());
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

	public void addOldValueForSees(String node, String child) {
		if (child.length() >= 5 && child.substring(0, 5).equals("true_") && !child.substring(child.length()-4).equals("_old")) {
			adjacencyMap.get(node).remove(child);
			addEdge(node, child + "_old");
			stratumMap.put(child + "_old", 0);
		}
		ArrayList<String> grandChildList = adjacencyMap.get(child);
		adjacencyMap.put(child, new ArrayList<String>());
		for (String grandChild : grandChildList) {
			adjacencyMap.get(child).add(grandChild);
			addOldValueForSees(child, grandChild);
		}
	}

	public void computeStratum() {
		LinkedList<String> unstratified = new LinkedList<String>();
		LinkedList<String> unstratifiedAlt = new LinkedList<String>();

		for (String key : adjacencyMap.keySet()) {
			if (adjacencyMap.get(key).isEmpty()) {
				stratumMap.put(key, 0);
			} else {
				stratumMap.put(key, -1);
				unstratified.add(key);
			}
		}

		ArrayList<String> oldifyList = new ArrayList<String>();
		boolean hasCycle = false;
		while (true) {
			boolean changed = false;
			for (String from : unstratified) {
				// Head of clause
				boolean unstratDep = false;
				int newStratum = 0;
				for (String to : adjacencyMap.get(from)) {
					// Body of clause
					if (from.equals(to)) {
						continue;
					} else if (stratumMap.get(to) < 0) {
						if (oldifyList.contains(to)) {
							continue;
						} else if (hasCycle && to.substring(0, 5).equals("true_")) {
							oldifyList.add(to);
							hasCycle = false;
						} else {
							if (!unstratifiedAlt.contains(from)) {
								unstratifiedAlt.add(from);
							}
							unstratDep = true;
						}
					} else if (stratumMap.get(to) > newStratum) {
						newStratum = stratumMap.get(to);
					}
				}
				if (!unstratDep) {
					ArrayList<String> bodyList = adjacencyMap.get(from);
					adjacencyMap.put(from, new ArrayList<String>());
					for (String to : bodyList) {
						if (oldifyList.contains(to)) {
							addEdge(from, to + "_old");
							stratumMap.put(to + "_old", 0);
						}else {
							addEdge(from, to);
						}
					}
					if (oldifyList.contains(from)) {
						oldifyList.remove(from);
					}
					stratumMap.put(from, newStratum + 1);
					changed = true;
				}
			}
			// End of unstratified entries
			if (unstratifiedAlt.isEmpty()) {
				break;
			} else if (!changed) {
				// Graph contains a cycle
				hasCycle = true;
			} else {
				unstratified = unstratifiedAlt;
				unstratifiedAlt = new LinkedList<String>();
				hasCycle = false;
			}
		}

		if (adjacencyMap.containsKey(GdlNode.GDL_SEES)) {
			//for (String child : adjacencyMap.get(GdlNode.GDL_SEES)) {
				addOldValueForSees("", GdlNode.GDL_SEES);
			//}
		}
	}
}
