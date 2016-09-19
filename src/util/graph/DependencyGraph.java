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
		//if (term.equals(GdlNode.GDL_TRUE)) {
		//	term = GdlNode.GDL_TRUE + "_old";
		//}
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

	public void computeStratum() {
		LinkedList<String> unset = new LinkedList<String>();
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
					stratumMap.put(from, newStratum + 1);
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

	@Deprecated
	public static class Term {
		private final String term;

		public Term(String term) {
			this.term = term;
		}

		public String getTerm() {
			return this.term;
		}

		@Override
		public int hashCode() {
			return term.hashCode();
		}

		@Override
		public String toString() {
			return term;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Term && ((Term) obj).getTerm().equals(this.term)) {
				return true;
			}
			return false;
		}
	}
}
