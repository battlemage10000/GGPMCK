package prover;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
import util.grammar.GdlLiteral;
import util.grammar.GdlNode;
import util.grammar.GdlRule;

public class Prover {
	public static String NOT_PREFIX = "(not ";
	public static String NEXT_PREFIX = "(next ";
	public static String TRUE_PREFIX = "(true ";
	public static String DOES_PREFIX = "(does ";

	private Set<String> literalSet; // L
	private Set<String> initialSet; // I
	private Set<String> tautologySet; // T
	private Set<String> contradictionSet; // C
	private Map<String, Set<Set<String>>> dnfRuleSet;

	public boolean DEBUG;

	public Prover(Gdl root) throws GDLSyntaxException {
		this(root, true);
	}

	public Prover(Gdl root, boolean DEBUG) throws GDLSyntaxException {
		this.DEBUG = DEBUG;
		literalSet = new HashSet<String>();
		initialSet = new HashSet<String>();
		tautologySet = new HashSet<String>();
		contradictionSet = new HashSet<String>();
		dnfRuleSet = new HashMap<String, Set<Set<String>>>();

		for (GdlNode node : root.getChildren()) {
			if (node instanceof GdlLiteral) {
				// These are facts
				if (node.getAtom().contentEquals(GdlNode.GDL_INIT)) {
					// Change init clauses to true and add to initialSet which
					// will be set true exactly once
					if (!initialSet.contains(TRUE_PREFIX + node.getChild(0).toString() + ")")) {
						initialSet.add(TRUE_PREFIX + node.getChild(0).toString() + ")");
					}
					if (!literalSet.contains(TRUE_PREFIX + node.getChild(0).toString() + ")")) {
						literalSet.add(TRUE_PREFIX + node.getChild(0).toString() + ")");
					}
				} else {
					// Non-init facts added to tautology set and rule set as
					// empty body clauses
					dnfRuleSet.put(node.toString(), new HashSet<Set<String>>());

					if (!literalSet.contains(node.toString())) {
						literalSet.add(node.toString());
					}
					if (!tautologySet.contains(node.toString())) {
						tautologySet.add(node.toString());
					}
				}

			} else if (node instanceof GdlRule) {
				// These are clauses
				GdlNode headNode = node.getChild(0);
				if (!dnfRuleSet.containsKey(headNode.toString())) {
					dnfRuleSet.put(headNode.toString(), new HashSet<Set<String>>());
				}
				Set<String> clauseLiteralSet = new HashSet<String>();

				for (int i = 1; i < node.getChildren().size(); i++) {
					GdlNode literal = node.getChild(i);

					// Add literal to ruleset before any processing
					clauseLiteralSet.add(literal.toString());

					// Strip negatives and add to literalSet
					boolean isNegative = false;
					while (literal.getAtom().equals(GdlNode.GDL_NOT)) {
						literal = literal.getChild(0);
						isNegative = !isNegative;
					}

					// Add striped literal to vocab set
					if (!literalSet.contains(literal.toString())) {
						literalSet.add(literal.toString());
					}

					// Evaluate distinct but don't remove yet
					if (literal.getAtom().equals(GdlNode.GDL_DISTINCT)) {
						if (literal.getChild(0).toString().equals(literal.getChild(1).toString())) {
							if (!contradictionSet.contains(literal.toString())) {
								contradictionSet.add(literal.toString());
							}
						} else {
							if (!tautologySet.contains(literal.toString())) {
								tautologySet.add(literal.toString());
							}
						}
					}
				}
				if (clauseLiteralSet.isEmpty()) {
					if (!tautologySet.contains(headNode)) {
						tautologySet.add(headNode.toString());
					}
				}// else {
					dnfRuleSet.get(headNode.toString()).add(clauseLiteralSet);
				//}
			} else {
				throw new GDLSyntaxException();
			}
		}
	}

	public int cullVariables() {
		int numIterations = 0;
		
		boolean changed = true;
		
		while (changed) {
			numIterations++;
			changed = false;
			Set<String> tautologyResetSet = new HashSet<String>();
			Set<String> contradictionResetSet = new HashSet<String>();
			Iterator<String> headIterator = dnfRuleSet.keySet().iterator();
			while (headIterator.hasNext()) {
				String headNode = headIterator.next();
				if (dnfRuleSet.get(headNode).isEmpty()) {
					continue;
				}
				
				//boolean dnfContainsTrue = false;
				Iterator<Set<String>> dnfIterator = dnfRuleSet.get(headNode).iterator();
				while (dnfIterator.hasNext()) {
					
					boolean disjunctContainsFalse = false;
					Set<String> disjunct = dnfIterator.next();
					Iterator<String> disjunctIterator = disjunct.iterator();
					while (disjunctIterator.hasNext()) {
						
						String literal = disjunctIterator.next();
						
						// Exclude not prefix
						while (literal.length() >= NOT_PREFIX.length()
									&& literal.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
							literal = literal.substring(NOT_PREFIX.length(), literal.length()-1);
						}

						// Exclude true and does from tautology/contradiction evaluation
						if ((literal.length() >= TRUE_PREFIX.length()
								&& literal.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX))
								|| (literal.length() >= DOES_PREFIX.length()
										&& literal.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX))) {
							continue;
						}

						// Known tautology therefore literal culled
						if (tautologySet.contains(literal)) {
							disjunctIterator.remove();
							changed = true;
						}

						// Known contradiction therefore clause flagged for culling
						if (contradictionSet.contains(literal)) {
							disjunctContainsFalse = true;
						}

						// Unknown contradiction added to contradictionSet and clause flagged for culling
						if (!dnfRuleSet.containsKey(literal)) {
							if (!contradictionSet.contains(literal)) {
								contradictionSet.add(literal);
								//changed = true;
							}
						}
					}
					if (disjunctContainsFalse) {
						// Cull clauses with contradictions
						dnfIterator.remove();
						changed = true;
						if (dnfRuleSet.get(headNode).isEmpty() && !contradictionSet.contains(headNode)) {
							contradictionSet.add(headNode);
							//changed = true;
						}
					} else if (disjunct.isEmpty()) {
						// Empty clause in dnf therefore head is tautology
						if (!tautologySet.contains(headNode)) {
							tautologySet.add(headNode);
							//changed = true;
						}
						tautologyResetSet.add(headNode);
					}
				}
			}
			for (String resetHead : tautologyResetSet) {
				dnfRuleSet.put(resetHead, new HashSet<Set<String>>());
				changed = true;
			}
			for (String contradiction : contradictionResetSet) {
				if (dnfRuleSet.containsKey(contradiction)) {
					dnfRuleSet.remove(contradiction);
				}
			}
			tautologyResetSet.clear();
			contradictionResetSet.clear();
			System.out.println("Iteration: " + numIterations);
			if (numIterations % 100 == 1) {
				System.out.println("breakpoint");
			}
		}
		return numIterations;
	}

	public Model generateInitialModel() {
		Set<String> trueLiterals = new HashSet<String>();
		trueLiterals.addAll(initialSet);
		trueLiterals.addAll(tautologySet);

		// Evaluate all the head nodes
		Set<String> falseLiterals = new HashSet<String>();
		falseLiterals.addAll(contradictionSet);
		for (String headNode : dnfRuleSet.keySet()) {
			if (evaluateHeadNode(headNode, trueLiterals, falseLiterals)) {
				if (!trueLiterals.contains(headNode)) {
					trueLiterals.add(headNode);
				}
			} else {
				if (!falseLiterals.contains(headNode)) {
					falseLiterals.add(headNode);
				}
			}
		}
		return new Model(trueLiterals);
	}

	public Model generateModel(Model oldModel) {
		Set<String> trueLiterals = new HashSet<String>();
		trueLiterals.addAll(tautologySet);

		// Extract next values from previous model
		for (String literal : oldModel.getModel()) {
			boolean isNegative = false;
			// Skip negations
			while (literal.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
				literal = literal.substring(NOT_PREFIX.length());
				isNegative = !isNegative;
			}
			if (!trueLiterals.contains(literal) && !isNegative
					&& literal.substring(0, NEXT_PREFIX.length()).equals(NEXT_PREFIX)) {
				trueLiterals.add(TRUE_PREFIX + literal.intern());
			}
		}

		// Evaluate head nodes for this model
		Set<String> falseLiterals = new HashSet<String>();
		falseLiterals.addAll(contradictionSet);
		for (String headNode : dnfRuleSet.keySet()) {
			if (evaluateHeadNode(headNode, trueLiterals, falseLiterals)) {
				if (!trueLiterals.contains(headNode)) {
					trueLiterals.add(headNode);
				}
			} else {
				if (!falseLiterals.contains(headNode)) {
					falseLiterals.add(headNode);
				}
			}

		}
		return new Model(trueLiterals);
	}

	public boolean evaluateHeadNode(String headNode, Set<String> trueLiterals, Set<String> falseLiterals) {
		if (dnfRuleSet.get(headNode) == null) {
			if (headNode.length() > TRUE_PREFIX.length()
					&& !headNode.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX)
					&& headNode.length() > DOES_PREFIX.length()
					&& !headNode.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX)) {
				contradictionSet.add(headNode);
			}
			return false;
		}
		if (dnfRuleSet.get(headNode).isEmpty()) {
			tautologySet.add(headNode);
			return true;
		}

		for (Set<String> disjunct : dnfRuleSet.get(headNode)) {
			boolean disjunctHasFalse = false;
			for (String literal : disjunct) {
				// Get rid of prefixing negations
				boolean isNegative = false;
				while (literal.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
					literal = literal.substring(NOT_PREFIX.length(), literal.length()-1);
					isNegative = !isNegative;
				}

				if (trueLiterals.contains(literal)) {
					if (isNegative) {
						disjunctHasFalse = true;
						continue;
					} else {
						continue;
					}
				} else if (falseLiterals.contains(literal)) {
					if (isNegative) {
						continue;
					} else {
						disjunctHasFalse = true;
						continue;
					}
				} else {
					if (evaluateHeadNode(literal, trueLiterals, falseLiterals)) {
						if (isNegative) {
							falseLiterals.add(literal);
						} else {
							trueLiterals.add(literal);
						}
					} else {
						if (isNegative) {
							trueLiterals.add(literal);
						} else {
							falseLiterals.add(literal);
						}
					}
				}
			}
			// clause is true so head of rule is true
			if (!disjunctHasFalse) {
				return true;
			}
		}
		return false;
	}

	public Set<Set<String>> getDnfRuleOfHead(GdlLiteral headNode) {
		return dnfRuleSet.get(headNode.toString());
	}

	public Map<String, Set<Set<String>>> getRuleSet() {
		return dnfRuleSet;
	}
	
	public Set<String> getTautologySet() {
		return tautologySet;
	}
	
	public Set<String> getContradictionSet() {
		return contradictionSet;
	}
	
	public String debug() {
		StringBuilder debug = new StringBuilder();
		debug.append(System.lineSeparator() + "Literals: " + literalSet.toString());
		debug.append(System.lineSeparator() + "Initial: " + initialSet.toString());
		debug.append(System.lineSeparator() + "Tautology: " + tautologySet.toString());
		debug.append(System.lineSeparator() + "Contradiction: " + contradictionSet.toString());
		// debug.append(System.lineSeparator() + "TrueVars: " + trueLiterals.toString());
		// debug.append(System.lineSeparator() + "FalseVars: " + falseLiterals.toString());
		return debug.toString();
	}
}
