package util.graph;

import java.util.Map;
import java.util.Set;

import util.grammar.GdlNode;
import util.grammar.GdlType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;

/**
 * @author vedantds
 *
 */
public class DomainGraph {
	private Map<Term, Set<Term>> adjacencyMap;
	
	private boolean SYNCHRONIZED_COLLECTIONS = false;

	/**
	 * 
	 */
	public DomainGraph() {
		if (SYNCHRONIZED_COLLECTIONS) {
			adjacencyMap = Collections.synchronizedMap(new HashMap<Term, Set<Term>>());
		} else {
			adjacencyMap = new HashMap<Term, Set<Term>>();
		}
	}

	/**
	 * @param term
	 * @param arity
	 * @return
	 */
	public boolean hasTerm(String term, int arity) {
		return adjacencyMap.containsKey(new Term(term, arity));
	}

	/**
	 * @param term
	 * @param arity
	 * @return
	 */
	public Set<Term> getNeighbours(String term, int arity) {
		if (hasTerm(term, arity)) {
			return adjacencyMap.get(new Term(term, arity));
		} else {
			return Collections.emptySet();
		}
	}

	/**
	 * @param term
	 * @param arity
	 * @return
	 */
	public Set<Term> getDomain(String term, int arity) {
		HashSet<Term> domain = new HashSet<Term>();
		Term termObj = new Term(term, arity);

		// TODO: allow for dependency to be a complex term
		for (Term dependency : adjacencyMap.get(termObj)) {
			if (dependency.getTerm().equals(GdlNode.DISTINCT)) {
				continue;
			} else if (dependency.getArity() == 0 && !domain.contains(dependency)) {
				domain.add(dependency);
			} else {
				if (!dependency.visited) {
					dependency.visited = true;
					for (Term subTerm : getDomain(dependency.getTerm(), dependency.getArity())) {
						if (!domain.contains(subTerm)) {
							domain.add(subTerm);
						}
					}
					dependency.visited = false;
				}
			}
		}
		return domain;
	}

	/**
	 * @return
	 */
	public Map<Term, Set<Term>> getMap() {
		Map<Term, Set<Term>> domainMap = new HashMap<Term, Set<Term>>();

		for (Term term : adjacencyMap.keySet()) {
			domainMap.put(term, getDomain(term.getTerm(), term.getArity()));
		}

		return domainMap;
	}

	/**
	 * @param term
	 * @param arity
	 */
	public void addTerm(String term, int arity) {
		Term newTerm = new Term(term, arity);
		if (!adjacencyMap.containsKey(newTerm)) {
			adjacencyMap.put(newTerm, new HashSet<Term>());
		}
	}

	/**
	 * @param term
	 * @param functionArity
	 */
	public void addFunction(String term, int functionArity) {
		Term function = new Term(term, functionArity, true, GdlType.FUNCTION);
		if (!adjacencyMap.containsKey(function)) {
			adjacencyMap.put(function, new HashSet<Term>());
		} else {
			adjacencyMap.put(function, adjacencyMap.get(function));
		}
		for (int i = 1; i <= functionArity; i++) {
			Term parameter = new Term(term, i);
			if (!adjacencyMap.containsKey(parameter)) {
				addTerm(term, i);
			}
		}
	}

	/**
	 * @param term
	 * @param formulaArity
	 */
	public void addFormula(String term, int formulaArity) {
		Term formula = new Term(term, formulaArity, true, GdlType.FORMULA);
		if (!adjacencyMap.containsKey(formula)) {
			adjacencyMap.put(formula, new HashSet<Term>());
		} else {
			adjacencyMap.put(formula, adjacencyMap.get(formula));
		}
		for (int i = 1; i <= formulaArity; i++) {
			Term parameter = new Term(term, i);
			if (!adjacencyMap.containsKey(parameter)) {
				addTerm(term, i);
			}
		}
	}

	/**
	 * @param fromTerm
	 * @param fromArity
	 * @param toTerm
	 * @param toArity
	 * @param type
	 */
	public void addEdge(String fromTerm, int fromArity, String toTerm, int toArity, GdlType type) {
		boolean toFunction = false;
		if (type != GdlType.CONSTANT) {
			toFunction = true;
		}
		Term from = new Term(fromTerm, fromArity);
		Term to = new Term(toTerm, toArity, toFunction, type);
		if (!adjacencyMap.containsKey(from)) {
			addTerm(fromTerm, fromArity);
		}
		if (!adjacencyMap.containsKey(to)) {
			if (type == GdlType.FUNCTION) {
				addFunction(toTerm, toArity);
			} else if (type == GdlType.FORMULA) {
				addFormula(toTerm, toArity);
			} else {
				addTerm(toTerm, toArity);
			}
		}
		if (!adjacencyMap.get(from).contains(to)) {
			adjacencyMap.get(from).add(to);
		}
	}

	/**
	 * @param fromTerm
	 * @param fromArity
	 * @param toTerm
	 * @param toArity
	 */
	public void addEdge(String fromTerm, int fromArity, String toTerm, int toArity) {
		addEdge(fromTerm, fromArity, toTerm, toArity, GdlType.CONSTANT);
	}

	private String dotEncoded(String string){
		if (string.contains("+")){
			string = string.replace("+", "plus");
		}
		return string;
	}
	
	/**
	 * @return
	 */
	public String dotEncodedGraph() {
		StringBuilder dot = new StringBuilder();

		dot.append("strict digraph {");

		for (Term node : adjacencyMap.keySet()) {
			dot.append(System.lineSeparator() + "d_" + dotEncoded(node.getTerm()));
			if (node.getArity() > 0) {
				dot.append("_" + node.getArity() + " [label=\"" + dotEncoded(node.getTerm()) + "[" + node.getArity()
						+ "]\",color=blue]");
			} else if (node.getFunctionArity() > 0) {
				dot.append("__" + node.getFunctionArity() + " [label=\"" + dotEncoded(node.getTerm()) + "/"
						+ node.getFunctionArity() + "\",");
				if (node.getType() == GdlType.FORMULA) {
					dot.append("color=red]");
				} else {
					dot.append("color=orange]");
				}
				// Add subgraph that links functor to function parameters
				dot.append(System.lineSeparator() + "subgraph {");
				for (int i = 1; i <= node.getFunctionArity(); i++) {
					dot.append(System.lineSeparator() + "  d_" + dotEncoded(node.getTerm()) + "__" + node.getFunctionArity());
					dot.append("  ->  d_" + dotEncoded(node.getTerm()) + "_" + i);
				}
				dot.append(System.lineSeparator() + "}");
			} else {
				dot.append(" [label=\"" + dotEncoded(node.getTerm()) + "\",");
				if (node.getType() == GdlType.FORMULA) {
					dot.append("color=red]");
				} else {
					dot.append("color=green]");
				}
			}
		}

		for (Term from : adjacencyMap.keySet()) {
			if (adjacencyMap.get(from).size() > 0) {
				dot.append(System.lineSeparator() + "  d_" + dotEncoded(from.getTerm()) + "_" + from.getArity() + " -> { ");
				for (Term to : adjacencyMap.get(from)) {
					dot.append("d_" + dotEncoded(to.getTerm()));

					if (to.getFunctionArity() > 0) {
						dot.append("__" + to.getFunctionArity());
					} else if (to.getArity() > 0) {
						dot.append("_" + to.getArity());
					}
					dot.append(" ");

				}
				dot.append("}");
			}
		}

		dot.append(System.lineSeparator());
		dot.append("}");

		return dot.toString();
	}

	/**
	 * 
	 */
	public void printGraph() {
		for (Term from : adjacencyMap.keySet()) {
			System.out.println("From : " + from.toString());
			for (Term to : adjacencyMap.get(from)) {
				System.out.println("  To : " + to.toString());
			}
		}
	}

	/**
	 * @param term
	 * @param arity
	 */
	public void printFunction(String term, int arity) {
		System.out.println("From : " + term + "[" + arity + "]");
		for (Term to : getDomain(term, arity)) {
			System.out.println("  To : " + to.toString());
		}
	}

	/**
	 * 
	 */
	public void printGraphDomains() {
		for (Term from : adjacencyMap.keySet()) {
			System.out.println("From : " + from.toString());
			for (Term to : getDomain(from.getTerm(), from.getArity())) {
				System.out.println("  To : " + to.toString());
			}
		}
	}

	/**
	 * @author vedantds
	 *
	 */
	public static class Term {
		private String term;
		private int arity;
		private int functionArity;
		boolean visited;
		private GdlType type;

		/**
		 * @param term
		 * @param arity
		 */
		public Term(String term, int arity) {
			this.term = term;
			this.arity = arity;
			this.functionArity = 0;
		}

		/**
		 * @param term
		 * @param arity
		 * @param function
		 * @param type
		 */
		public Term(String term, int arity, boolean function, GdlType type) {
			this.term = term;
			this.type = type;
			if (function) {
				this.arity = 0;
				this.functionArity = arity;
			} else {
				this.arity = arity;
				this.functionArity = 0;
			}
		}

		/**
		 * @return
		 */
		public boolean isConstant() {
			if (arity == 0 && functionArity == 0) {
				return true;
			} else {
				return false;
			}
		}

		/**
		 * @return
		 */
		public String getTerm() {
			return term;
		}

		/**
		 * @return
		 */
		public int getArity() {
			return arity;
		}

		/**
		 * @return
		 */
		public int getFunctionArity() {
			return functionArity;
		}

		/**
		 * @return
		 */
		public GdlType getType() {
			return type;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return term + "[" + arity + "]";
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Term) {
				Term other = (Term) obj;
				if (this.term.equals(other.getTerm()) && this.arity == other.getArity()) {
					if (other.getFunctionArity() > this.functionArity) {
						this.functionArity = other.getFunctionArity();
					} else {
						other.functionArity = this.functionArity;
					}
					return true;
				}
			}
			return false;
		}
	}
}