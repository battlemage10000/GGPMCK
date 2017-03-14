package prover;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
import util.grammar.GdlLiteral;
import util.grammar.GdlNode;
import util.grammar.GdlRule;

public class GdlRuleSet {
	public static String NOT_PREFIX = "(not ";
	public static String INIT_PREFIX = "(init ";
	public static String NEXT_PREFIX = "(next ";
	public static String TRUE_PREFIX = "(true ";
	public static String DOES_PREFIX = "(does ";
	public static String FALSE = "?FALSE";
	public static String TRUE = "?TRUE";

	private Set<String> literalSet; // L
	private Set<String> initialSet; // I
	private Set<String> tautologySet; // T
	private Set<String> contradictionSet; // C
	private Map<String, Set<Set<String>>> ruleSet;
	private Map<String, Integer> stratumMap;
	private Set<String> oldSet;

	public boolean debug;
	//public boolean CULL_NULL_RULES; // Remove [headNode -> null] rules from
									// ruleset

	public GdlRuleSet() {
		debug = true;
		oldSet = new HashSet<String>();
		literalSet = new HashSet<String>();
		initialSet = new HashSet<String>();
		tautologySet = new HashSet<String>();
		contradictionSet = new HashSet<String>();
		ruleSet = new HashMap<String, Set<Set<String>>>();
		stratumMap = new HashMap<String, Integer>();
	}
	
	public GdlRuleSet(Gdl root) throws GDLSyntaxException {
		this(root, true);
	}

	public GdlRuleSet(Gdl root, boolean DEBUG) throws GDLSyntaxException {
		this();
		joinRuleSet(root);
	}

	public void joinRuleSet(Gdl game) throws GDLSyntaxException {
		for (GdlNode node : game.getChildren()) {
			if (node instanceof GdlLiteral) {
				// These are facts
				if (node.getAtom().contentEquals(GdlNode.INIT)) {
					// Change init clauses to true and add to initialSet which
					// will be set true exactly once
					if (!initialSet.contains(node.getChild(0).toString())) {
						initialSet.add((node.getChild(0).toString()).intern());
					}
					if (!literalSet.contains(TRUE_PREFIX + node.getChild(0).toString() + ")")) {
						literalSet.add((TRUE_PREFIX + node.getChild(0).toString() + ")").intern());
					}
				} else {
					// Non-init facts added to tautology set and rule set as
					// empty body clauses
					ruleSet.put(node.toString(), new HashSet<Set<String>>());

					if (!literalSet.contains(node.toString())) {
						literalSet.add(node.toString().intern());
					}
				}

			} else if (node instanceof GdlRule) {
				// These are clauses
				GdlNode headNode = node.getChild(0);
				if (!ruleSet.containsKey(headNode.toString())) {
					ruleSet.put(headNode.toString(), new HashSet<Set<String>>());
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
							ruleSet.put(headNode.toString(), new HashSet<Set<String>>());
						}
					}
				}
				ruleSet.get(headNode.toString()).add(clauseLiteralSet);
			} else {
				throw new GDLSyntaxException();
			}
		}
	}
	
	public int cullVariables(boolean cullNullRules) {
		int numIterations = 0;
		boolean changed = true;
		while (changed) {
			numIterations++;
			changed = false;

			Set<String> removeRuleSet = new HashSet<String>();

			for (String headNode : ruleSet.keySet()) {
				if (ruleSet.get(headNode) == null || ruleSet.get(headNode).isEmpty()) {
					continue;
				}
				
				Set<Set<String>> dnfRule = ruleSet.get(headNode);
				Set<Set<String>> newDnfRule = new HashSet<Set<String>>();
				boolean ruleContainsNonFalse = false;
				boolean headIsTautology = false;
				boolean headIsContradiction = false;
				
				for (Set<String> disjunct : dnfRule) {
					disjunct = processClause(disjunct);
					if (disjunct == null){
						
					} else if (disjunct.isEmpty()) {
						newDnfRule.clear();
						headIsTautology = true;
						break;
					} else {
						newDnfRule.add(disjunct);
						ruleContainsNonFalse = true;
					}
				} // End disjunct-for-loop

				if (!ruleContainsNonFalse) {
					// Each clause has a false literal therefore contradiction
					headIsContradiction = true;
				}

				if (headIsTautology) {
					ruleSet.put(headNode, Collections.emptySet());
					changed = true;
				} else if (headIsContradiction) {
					ruleSet.put(headNode, null);
					changed = true;
					if (cullNullRules) {
						removeRuleSet.add(headNode.intern());
					}
				} else {
					ruleSet.put(headNode, newDnfRule);
				}
			} // End head-for-loop
			if (cullNullRules) {
				for (String removeHead : removeRuleSet) {
					ruleSet.remove(removeHead);
				}
			}
			removeRuleSet.clear();
		} // End change-while-loop
		return numIterations;
	}
	
	/**
	 * Minimize on a per-clause basis 
	 * @param clause
	 * @return
	 */
	private Set<String> processClause(Set<String> clause){
		if (clause == null || clause.isEmpty()) {
			return clause;
		}
		Iterator<String> clauseIterator = clause.iterator();
		while (clauseIterator.hasNext()) {
			String literal = clauseIterator.next();
			boolean isNegative = false;
			String posLiteral = literal;
			while (posLiteral.length() > NOT_PREFIX.length()
					&& posLiteral.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
				posLiteral = posLiteral.substring(NOT_PREFIX.length(), posLiteral.length() - 1);
				isNegative = !isNegative;
			}
			if (posLiteral.length() > DOES_PREFIX.length() && 
					posLiteral.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX)) {
				if (ruleSet.get("(legal " + posLiteral.substring(DOES_PREFIX.length())) == null){
					// does must always have a corresponding legal
					if (!isNegative) {
						return null;
					} else {
						clauseIterator.remove();
					}
				}
			} else if (!(posLiteral.length() > TRUE_PREFIX.length() && 
					posLiteral.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX)))
			{
				
				if (ruleSet.get(posLiteral) == null && !isNegative) {
					return null; // false
				} else if (ruleSet.get(posLiteral) == null && isNegative) {
					clauseIterator.remove(); // true
				} else if (ruleSet.get(posLiteral).isEmpty() && !isNegative) {
					clauseIterator.remove(); // true
				} else if (ruleSet.get(posLiteral).isEmpty() && isNegative) {
					return null; // false
				}
			}
		}
		return clause;
	}
	
	public Map<String, Integer> generateStratumMap(){
		for (String head : ruleSet.keySet()) {
			if (!stratumMap.containsKey(head)) {
				stratumMap.put(head, computeStratum(head));
			}
		}
		return stratumMap;
	}
	

	public int computeStratum(String headNode) {
		return computeStratum(headNode, new ArrayDeque<String>());
	}
	
	public int computeStratum(String headNode, Deque<String> stack) {
		if (headNode == null || (headNode.length() > INIT_PREFIX.length() && headNode.substring(0, INIT_PREFIX.length()).equals(INIT_PREFIX))) {
			return -2; // Null parameter
		}
		if (stack.contains(headNode)) {
			if (!oldSet.contains(headNode)) {
				oldSet.add(headNode);
				System.out.println("Oldify " + headNode);
			}
			return 0;
		}
		
		Set<Set<String>> rule = ruleSet.get(headNode);
		if (rule == null) {
			return -2; // Contradiction
		} else if (rule.isEmpty()) {
			return 0;
		} else {
			int max = -2;
			
			for (Set<String> disjunct : rule) {
				for (String literal : disjunct) {
					// Prune not from literal
					while (literal.length() >= NOT_PREFIX.length()
							&& literal.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
						literal = literal.substring(NOT_PREFIX.length(), literal.length() - 1);
					}
					if (stratumMap.containsKey(literal)) {
						// If stratum known
						if (stratumMap.get(literal) > max) {
							max = stratumMap.get(literal);
						}
					} else if (literal.length() >= DOES_PREFIX.length()
							&& literal.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX)) {
						// Stratum unknown, does is 0
						stratumMap.put(literal, 0);
						if (max < 0) {
							max = 0;
						}
					} else if (literal.length() >= TRUE_PREFIX.length()
							&& literal.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX)) {
						// Stratum unknown, true is 0
						stratumMap.put(literal, 0);
						if (max < 0) {
							max = 0;
						}
					} else {
						// Stratum unknown, compute stratum(recursive)
						stack.push(headNode);
						int literalStratum = computeStratum(literal, stack);
						stack.pop(); // pop head node
						if (literalStratum != 0 || !oldSet.contains(literal)) {
							stratumMap.put(literal, literalStratum);
						}
						if (literalStratum > max) {
							max = literalStratum;
						}
					}
				}
			}
			
			return max + 1;
		}
	}
	
	public Set<String> getOldSet(){
		return oldSet;
	}
	
	public Model generateInitialModel(){
		Set<String> initLiterals = new HashSet<String>();
		for (String init : initialSet) {
			initLiterals.add(NEXT_PREFIX + init + ")");
		}
		return generateModel(new Model(initLiterals));
	}
	
	public Model generateModel(Model oldModel) {
		Set<String> trueLiterals = new HashSet<String>();
		Set<String> falseLiterals = new HashSet<String>();
		for (String init : oldModel.getModel()) {
			trueLiterals.add(init.replace(NEXT_PREFIX, TRUE_PREFIX));
		}
		ArrayDeque<String> iterationQueue = new ArrayDeque<String>();
		iterationQueue.addAll(ruleSet.keySet());
		while(!iterationQueue.isEmpty()){
			int queueSize = iterationQueue.size();
			Iterator<String> dequeIterator = iterationQueue.iterator();
			while (dequeIterator.hasNext()) {
				String rule = dequeIterator.next();
				if (ruleSet.get(rule) == null) {
					falseLiterals.add(rule);
					dequeIterator.remove();
				} else if (ruleSet.get(rule).isEmpty()) {
					trueLiterals.add(rule);
					dequeIterator.remove();
				} else {
					boolean ruleHasOnlyFalse = true;
					for (Set<String> disjunct : ruleSet.get(rule)) {
						boolean clauseHasFalse = false;
						boolean clauseHasNotRecorded = false;
						for (String literal : disjunct) {
							boolean isNegative = false;
							while(literal.length() > NOT_PREFIX.length() && literal.substring(0, NOT_PREFIX.length()).equals(NOT_PREFIX)) {
								literal = literal.substring(NOT_PREFIX.length(), literal.length() - 1);
								isNegative = !isNegative;
							}
							if (trueLiterals.contains(literal) && !isNegative) {
								// True
							} else if (trueLiterals.contains(literal) && isNegative) {
								// False
								clauseHasFalse = true;
							} else if (falseLiterals.contains(literal) && !isNegative) {
								// False
								clauseHasFalse = true;
							} else if (falseLiterals.contains(literal) && isNegative) {
								// True
							} else {
								// Unknown
								clauseHasNotRecorded = true;
							}
						}
						
						if (!clauseHasFalse) {
							ruleHasOnlyFalse = false;// rule contains non false clause
							
							if (!clauseHasNotRecorded) {
								// All known true literals in this clause
								trueLiterals.add(rule);
								dequeIterator.remove();
								break;
							}
						}
					}
					if (ruleHasOnlyFalse) {
						falseLiterals.add(rule);
						dequeIterator.remove();
					}
				}
			}
			if (iterationQueue.size() == queueSize) {
				break;
			}
		}
		trueLiterals.addAll(tautologySet);
		
		return new Model(trueLiterals);
	}
	
	@Deprecated
	public Model generateInitialModel(boolean thing) {
		Set<String> trueLiterals = new HashSet<String>();
		trueLiterals.addAll(initialSet);
		trueLiterals.addAll(tautologySet);

		// Evaluate all the head nodes
		Set<String> falseLiterals = new HashSet<String>();
		falseLiterals.addAll(contradictionSet);
		for (String headNode : ruleSet.keySet()) {
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

	@Deprecated
	public Model generateModel(Model oldModel, boolean thing) {
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
		for (String headNode : ruleSet.keySet()) {
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

	@Deprecated
	public boolean evaluateHeadNode(String headNode, Set<String> trueLiterals, Set<String> falseLiterals) {
		if (ruleSet.get(headNode) == null) {
			if (headNode.length() > TRUE_PREFIX.length()
					&& !headNode.substring(0, TRUE_PREFIX.length()).equals(TRUE_PREFIX)
					&& headNode.length() > DOES_PREFIX.length()
					&& !headNode.substring(0, DOES_PREFIX.length()).equals(DOES_PREFIX)) {
				contradictionSet.add(headNode);
			}
			return false;
		}
		if (ruleSet.get(headNode).isEmpty()) {
			tautologySet.add(headNode);
			return true;
		}

		for (Set<String> disjunct : ruleSet.get(headNode)) {
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

	public PriorityQueue<String> getOrderedSet(){
		if (stratumMap == null ||  stratumMap.isEmpty()) {
			generateStratumMap();
		}
		PriorityQueue<String> orderer = new PriorityQueue<String>(ruleSet.keySet().size(), new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return stratumMap.get(o1) - stratumMap.get(o2);
			}
		});
		orderer.addAll(ruleSet.keySet());
		return orderer;
	}
	
	public int getStratum(String headNode){
		return stratumMap.get(headNode);
	}
	
	public Set<Set<String>> getRule(String headNode) {
		return ruleSet.get(headNode);
	}

	public Map<String, Set<Set<String>>> getRuleSet() {
		return ruleSet;
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

		for (String headNode : ruleSet.keySet()) {
			Set<Set<String>> rule = ruleSet.get(headNode);
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
	
	public String toGdlOrdered(){
		StringBuilder gdl = new StringBuilder();

		PriorityQueue<String> orderedSet = getOrderedSet();
		while (!orderedSet.isEmpty()) {
			String headNode = orderedSet.poll();
			Set<Set<String>> rule = ruleSet.get(headNode);
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
			if (ruleSet.get(literal) == null) {
				if (!contradictionSet.contains(literal)) {
					contradictionSet.add(literal);
				}
			} else if (ruleSet.get(literal).isEmpty()) {
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
