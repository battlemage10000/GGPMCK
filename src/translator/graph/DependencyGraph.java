package translator.graph;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class DependencyGraph {
	private final Map<Term, ArrayList<Term>> adjacencyMap;

	public DependencyGraph() {
		adjacencyMap = new HashMap<Term, ArrayList<Term>>();
	}

	public boolean hasTerm(String term) {
		return adjacencyMap.containsKey(new Term(term));
	}

	public void addTerm(String term) {
		if (!hasTerm(term)) {
			adjacencyMap.put(new Term(term), new ArrayList<Term>());
		}
	}

	public ArrayList<Term> getNeighbour(String term) {
		return adjacencyMap.get(new Term(term));
	}

	public void addEdge(String fromTerm, String toTerm) {
		if (!hasTerm(fromTerm)) {
			addTerm(fromTerm);
		}
		if (!hasTerm(toTerm)) {
			addTerm(toTerm);
		}
		Term to = new Term(toTerm);
		if (!getNeighbour(fromTerm).contains(to)) {
			getNeighbour(fromTerm).add(to);
		}
	}

	public Map<Term, ArrayList<Term>> getMap() {
		return adjacencyMap;
	}

	public String dotEncodedGraph() {
		StringBuilder dot = new StringBuilder();
		dot.append("strict digraph {");

		// Declare nodes and assign attributes
		for (Term from : adjacencyMap.keySet()) {
			dot.append(System.lineSeparator() + from.getTerm() + " [label=\"" + from.getTerm() + "\"]");
		}
		dot.append(System.lineSeparator());

		// Add edges
		for (Term from : adjacencyMap.keySet()) {
			for (Term to : adjacencyMap.get(from)) {
				dot.append(System.lineSeparator() + from.getTerm() + " -> " + to.getTerm());
			}
		}

		dot.append(System.lineSeparator() + "}");

		return dot.toString();
	}

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
