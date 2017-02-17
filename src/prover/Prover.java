package prover;

import java.util.HashMap;
import java.util.HashSet;
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
	public static String FALSE = "?FALSE";
	public static String TRUE = "?TRUE";

	private Set<String> literalSet; // L
	private Set<String> initialSet; // I
	private Set<String> tautologySet; // T
	private Set<String> contradictionSet; // C
	private Map<String, Set<Set<String>>> dnfRuleSet;

	public boolean DEBUG;
	public boolean CULL_NULL_RULES; // Remove [headNode -> null] rules from
									// ruleset

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

		joinRuleSet(root);
	}

	public void joinRuleSet(Gdl ruleSet) throws GDLSyntaxException {
		for (GdlNode node : ruleSet.getChildren()) {
			if (node instanceof GdlLiteral) {
				// These are facts
				if (node.getAtom().contentEquals(GdlNode.INIT)) {
					// Change init clauses to true and add to initialSet which
					// will be set true exactly once
					if (!initialSet.contains(TRUE_PREFIX + node.getChild(0).toString() + ")")) {
						initialSet.add((TRUE_PREFIX + node.getChild(0).toString() + ")").intern());
					}
					if (!literalSet.contains(TRUE_PREFIX + node.getChild(0).toString() + ")")) {
						literalSet.add((TRUE_PREFIX + node.getChild(0).toString() + ")").intern());
					}
				} else {
					// Non-init facts added to tautology set and rule set as
					// empty body clauses
					dnfRuleSet.put(node.toString(), new HashSet<Set<String>>());

					if (!literalSet.contains(node.toString())) {
						literalSet.add(node.toString().intern());
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
					clauseLiteralSet.add(literal.toString().intern());

					// Strip negatives and add to literalSet
					boolean isNegative = false;
					while (literal.getAtom().equals(GdlNode.NOT)) {
						literal = literal.getChild(0);
						isNegative = !isNegative;
					}

					// Add striped literal to vocab set
					if (!literalSet.contains(literal.toString())) {
						literalSet.add(literal.toString().intern());
					}

					// Evaluate distinct but don't remove yet
					if (literal.getAtom().equals(GdlNode.DISTINCT)) {
						if (!literal.getChild(0).toString().equals(literal.getChild(1).toString())) {
							dnfRuleSet.put(headNode.toString(), new HashSet<Set<String>>());
						}
					}
				}
				dnfRuleSet.get(headNode.toString()).add(clauseLiteralSet);
			} else {
				throw new GDLSyntaxException();
			}
		}
	}
	
	public int cullVariables(boolean CULL_NULL_RULES) {
		int numIterations = 0;
		boolean changed = true;
		while (changed) {
			numIterations++;
			changed = false;

			Set<String> removeRuleSet = new HashSet<String>();

			for (String headNode : dnfRuleSet.keySet()) {
				Set<Set<String>> dnfRule = dnfRuleSet.get(headNode);
				Set<Set<String>> newDnfRule = new HashSet<Set<String>>();

				boolean ruleContainsNonFalse = false;
				boolean headIsTautology = false;
				boolean headIsContradiction = false;

				if (dnfRuleSet.get(headNode) == null || dnfRuleSet.get(headNode).isEmpty()) {
					continue;
				}

				for (Set<String> disjunct : dnfRule) {
					Set<String> newDisjunct = new HashSet<String>();

					if (disjunct.isEmpty()) {
						// Clause is tautology so rule is tautology
						headIsTautology = true;
						break;
					}

					for (String literal : disjunct) {
						// Filter negative prefix
						boolean isNegative = false;
						String posLiteral = literal;
						while (posLiteral.length() >= NOT_PREFIX.length()
								&& posLiteral.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
							posLiteral = posLiteral.substring(NOT_PREFIX.length(), posLiteral.length() - 1);
							isNegative = !isNegative;
						}

						if (posLiteral.length() >= DOES_PREFIX.length()
								&& posLiteral.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX)) {
							// No change for does
							newDisjunct.add(literal.intern());
							// continue;
						} else if (posLiteral.length() >= TRUE_PREFIX.length()
								&& posLiteral.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX)) {
							// No change for true
							newDisjunct.add(literal.intern());
							// continue;
						} else if (dnfRuleSet.get(posLiteral) == null) {
							if (isNegative) {
								// True literal
								// continue;
							} else {
								// False literal
								newDisjunct.add(FALSE);
							}
						} else if (dnfRuleSet.get(posLiteral).isEmpty()) {
							if (isNegative) {
								// False literal
								newDisjunct.add(FALSE);
							} else {
								// True literal
								// continue;
							}
						} else {
							newDisjunct.add(literal.intern());
						}
					} // End literal-for-loop

					if (!newDisjunct.contains(FALSE)) {
						newDnfRule.add(newDisjunct);
						ruleContainsNonFalse = true;
					}
				} // End disjunct-for-loop

				if (!ruleContainsNonFalse) {
					// Each clause has a false literal therefore contradiction
					headIsContradiction = true;
				}

				if (headIsTautology) {
					dnfRuleSet.put(headNode, new HashSet<Set<String>>());
					changed = true;
				} else if (headIsContradiction) {
					dnfRuleSet.put(headNode, null);
					changed = true;
					if (CULL_NULL_RULES) {
						removeRuleSet.add(headNode.intern());
					}
				} else {
					dnfRuleSet.put(headNode, newDnfRule);
				}
			} // End head-for-loop
			if (CULL_NULL_RULES) {
				for (String removeHead : removeRuleSet) {
					dnfRuleSet.remove(removeHead);
				}
			}
			removeRuleSet.clear();
		} // End change-while-loop
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
					literal = literal.substring(NOT_PREFIX.length(), literal.length() - 1);
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

	public Set<String> getLiteralSet() {
		return literalSet;
	}

	public String toGdl() {
		StringBuilder gdl = new StringBuilder();

		for (String headNode : dnfRuleSet.keySet()) {
			Set<Set<String>> rule = dnfRuleSet.get(headNode);
			if (rule == null) {
				continue;
			} else if (rule.isEmpty()) {
				gdl.append(System.lineSeparator() + headNode);
			} else {
				for (Set<String> disjunct : rule) {
					StringBuilder clause = new StringBuilder();
					for (String literal : disjunct) {
						clause.append(literal + " ");
					}
					gdl.append(System.lineSeparator());
					if (clause.length() > 0) {
						gdl.append("(<= " + headNode + " " + clause.substring(0, clause.length() - 1) + ")");
					} else {
						gdl.append(headNode);
					}
				}
			}
		}
		return gdl.toString();
	}

	public String debug() {
		StringBuilder debug = new StringBuilder();
		debug.append(System.lineSeparator() + "Literals: " + literalSet.toString());
		for (String literal : literalSet) {
			if (dnfRuleSet.get(literal) == null) {
				if (!contradictionSet.contains(literal)) {
					contradictionSet.add(literal);
				}
			} else if (dnfRuleSet.get(literal).isEmpty()) {
				if (!tautologySet.contains(literal)) {
					tautologySet.add(literal);
				}
			}
		}
		debug.append(System.lineSeparator() + "Initial: " + initialSet.toString());
		debug.append(System.lineSeparator() + "Tautology: " + tautologySet.toString());
		debug.append(System.lineSeparator() + "Contradiction: " + contradictionSet.toString());
		return debug.toString();
	}
}
