package translator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import prover.Prover;
import util.GdlParser;
import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
import util.grammar.GdlLiteral;
import util.grammar.GdlNode;
import util.grammar.GdlRule;
import util.grammar.GdlType;
import util.graph.DependencyGraph;

/**
 * Translates GDL-II in kif format to equivalent MCK
 * 
 * @author Darrel Sadanand
 */
public class MckTranslator {
	// Variables
	private Set<String> AT;
	// Variables found in true and/or next
	private Set<String> ATf;
	// List of variables which are true in the initial state
	private Set<String> ATi;
	// List of variables which are always true(totality)
	private Set<String> ATt;
	// List of variables which are always false(contradiction)
	public Set<String> ATc;
	// List of formulae which are heads of clauses or facts
	private Set<String> ATh;
	// [Role -> Move] move map from legal
	private Map<String, List<String>> ATd;
	// [Role -> Sees] observation map from sees
	private Map<String, List<String>> ATs;
	private GdlNode root;
	private StringBuilder defineBasedDeclarations;
	// List of variables using the define keyword
	private Map<String, String> ATdef;

	private DependencyGraph graph;
	private Prover prover;

	private boolean DEBUG;
	private boolean ONE_LINE_TRANSITIONS = true;
	private boolean SHOW_PRUNED_VARS = true;
	private boolean SYNCHRONIZED_COLLECTIONS = false;
	private boolean ASSIGNMENT_IN_ACTION = false; // assign did_role in protocol
													// instead of as a state
													// transition
	private boolean DERIVE_INITIAL_CONDITIONS = true;
	private boolean TRANSITIONS_WITH_DEFINE = false;
	private boolean USE_PROVER = false;
	
	public MckTranslator(GdlNode root, boolean TRANSITIONS_WITH_DEFINE, boolean DEBUG, Prover prover) {
		this.root = root;
		this.TRANSITIONS_WITH_DEFINE = TRANSITIONS_WITH_DEFINE;
		this.DEBUG = DEBUG;
		if (SYNCHRONIZED_COLLECTIONS) {
			this.AT = Collections.synchronizedSet(new HashSet<String>());
			this.ATf = Collections.synchronizedSet(new HashSet<String>());
			this.ATi = Collections.synchronizedSet(new HashSet<String>());
			this.ATt = Collections.synchronizedSet(new HashSet<String>());
			this.ATc = Collections.synchronizedSet(new HashSet<String>());
			this.ATh = Collections.synchronizedSet(new HashSet<String>());
			this.ATdef = Collections.synchronizedMap(new HashMap<String, String>());
			this.ATd = Collections.synchronizedMap(new HashMap<String, List<String>>());
			this.ATs = Collections.synchronizedMap(new HashMap<String, List<String>>());
		} else {
			this.AT = new HashSet<String>();
			this.ATf = new HashSet<String>();
			this.ATi = new HashSet<String>();
			this.ATt = new HashSet<String>();
			this.ATc = new HashSet<String>();
			this.ATh = new HashSet<String>();
			this.ATdef = new HashMap<String, String>();
			this.ATd = new HashMap<String, List<String>>();
			this.ATs = new HashMap<String, List<String>>();
		}
		this.defineBasedDeclarations = new StringBuilder();
		if (prover != null) {
			this.prover = prover;
			USE_PROVER = true;
		} else if (USE_PROVER) {
			try {
				this.prover = new Prover((Gdl) root);
				System.out.println(this.prover.cullVariables(true) + " iterations");
			} catch (GDLSyntaxException e) {
				this.USE_PROVER = false;
				e.printStackTrace();
			}
		}
		initialize();
	}
	
	public MckTranslator(GdlNode root, boolean USE_DEFINE, boolean DEBUG){
		this(root, USE_DEFINE, DEBUG, null);
	}

	public MckTranslator(GdlNode root, boolean DEBUG) {
		this(root, false, DEBUG, null);
	}
	
	/**
	 * Set pre-initialized prover
	 * @param prover
	 */
	public void setProver(Prover prover) {
		if (prover != null) {
			this.prover = prover;
			USE_PROVER = true;
		}
	}

	@Deprecated
	public String formatClause(GdlNode headNode, List<GdlNode> bodyList) throws Exception {
		return formatClause(graph, headNode, bodyList);
	}

	/**
	 * Reformat a clause from gdl to an equivalent one in mck
	 * 
	 * TODO: add typed variable processing (currently only handles rules
	 * resulting true/false)
	 * TODO: move functionality to MckFormat class. 
	 * 
	 * @param ATf
	 * @param graph
	 * @param headNode
	 * @param bodyList
	 * @return
	 */
	@Deprecated
	public String formatClause(DependencyGraph graph, GdlNode headNode, List<GdlNode> bodyList) throws Exception {
		// Invalid inputs
		if (bodyList.isEmpty() || headNode.toString().length() == 0) {
			throw new Exception("Body list or head is empty");
		} else if (graph == null) {
			throw new Exception("Trying to format clause without dependency graph");
		} else {
			for (GdlNode clause : bodyList) {
				if (!clause.getChild(0).equals(headNode)) {
					throw new Exception("Clause doesn't match head node");
				}
			}
		}

		boolean seesClause = false;
		boolean nextClause = false;
		boolean containsDoes = false;

		// Recognize clause type
		if (headNode.getAtom().equals(GdlNode.SEES)) {
			seesClause = true;
		} else if (headNode.getAtom().equals(GdlNode.NEXT)) {
			// TODO: oldify using next
			nextClause = true;
		}

		StringBuilder disjunctBody = new StringBuilder();
		boolean disjunctBodyHasOtherThanFalse = false;
		for (GdlNode clause : bodyList) {
			boolean conjunctBodyHasFalse = false;
			boolean conjunctBodyHasOtherThanTrue = false;
			StringBuilder conjuntBody = new StringBuilder();
			conjuntBody.append(GdlParser.OPEN_P_Str + " "); // "("
			for (int i = 1; i < clause.getChildren().size(); i++) {
				boolean isNegated = false;
				String mckFormatted;
				if (clause.getChild(i).getAtom().equals(GdlNode.NOT)) {
					isNegated = true;
					if (TRANSITIONS_WITH_DEFINE && clause.getChild(i).getChild(0).getAtom().equals(GdlNode.DOES)) {
						containsDoes = true;
					}
					mckFormatted = MckFormat.formatMckNode(clause.getChild(i).getChild(0));
				} else {
					if (TRANSITIONS_WITH_DEFINE && clause.getChild(i).getAtom().equals(GdlNode.DOES)) {
						containsDoes = true;
					}
					mckFormatted = MckFormat.formatMckNode(clause.getChild(i));
				}

				if (isNegated) {
					if (DEBUG) {
						if (nextClause && graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(headNode))
								&& graph.hasTerm(MckFormat.TRUE_PREFIX + mckFormatted + MckFormat.OLD_SUFFIX)) {
							conjuntBody
									.append(MckFormat.NOT + " " + mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						} else {
							conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.AND);
						}
					}

					if (ATc.contains(mckFormatted)) {
						// Negation of contradiction is always true
						if (DEBUG) {
							conjuntBody.append(MckFormat.TRUE + MckFormat.AND);
						}
					} else if (ATt.contains(mckFormatted)) {
						// Negation of tautology is always false
						conjuntBody.append(MckFormat.FALSE + MckFormat.AND);
						conjunctBodyHasFalse = true;
						conjunctBodyHasOtherThanTrue = true;
					} else if (!ATh.contains(mckFormatted) && !ATi.contains(mckFormatted)
							&& !clause.getChild(i).getChild(0).getAtom().equals(GdlNode.DOES)) {
						// TODO: double check logic in this section
						// Negation of contradiction is always true
						if (DEBUG) {
							conjuntBody.append(MckFormat.TRUE + MckFormat.AND);
						}
					} else if (seesClause && ATf.contains(mckFormatted)) {
						// Append sees clause with old ("not" invariant)
						conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					} else if (nextClause && graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(headNode))
							&& graph.hasTerm(MckFormat.TRUE_PREFIX + mckFormatted + MckFormat.OLD_SUFFIX)) {
						// Append next clause with old ("not" invariant)
						conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					} else {
						// Everything else
						conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					}
				} else {
					if (DEBUG) {
						if (nextClause && graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(headNode))
								&& graph.hasTerm(MckFormat.TRUE_PREFIX + mckFormatted + MckFormat.OLD_SUFFIX)) {
							conjuntBody.append(mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						} else {
							conjuntBody.append(mckFormatted + MckFormat.AND);
						}
					}

					if (ATt.contains(mckFormatted)) {
						// Always true
						if (DEBUG) {
							conjuntBody.append(MckFormat.TRUE + MckFormat.AND);
						}
					} else if (ATc.contains(mckFormatted)) {
						// Always false
						conjuntBody.append(MckFormat.FALSE + MckFormat.AND);
						conjunctBodyHasFalse = true;
						conjunctBodyHasOtherThanTrue = true;
					} else if (!ATh.contains(mckFormatted) && !ATi.contains(mckFormatted)
							&& !clause.getChild(i).getAtom().equals(GdlNode.DOES)) {
						// Not in head, init or does is always false
						conjuntBody.append(MckFormat.FALSE + MckFormat.AND);
						conjunctBodyHasFalse = true;
						conjunctBodyHasOtherThanTrue = true;
						if (!ATc.contains(mckFormatted)) {
							ATc.add(mckFormatted);
						}
					} else if (seesClause && ATf.contains(mckFormatted)) {
						// Make clause with sees head old
						conjuntBody.append(mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					} else if (graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(headNode))
							&& graph.hasTerm(MckFormat.TRUE_PREFIX + mckFormatted + MckFormat.OLD_SUFFIX)) {
						// Make clause with next head old
						conjuntBody.append(mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					} else {
						// Default
						conjuntBody.append(mckFormatted + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					}
				}
			}

			if (!conjunctBodyHasOtherThanTrue) {
				// Clause is always true so tautology
				if (!ATt.contains(MckFormat.formatMckNode(headNode))) {
					ATt.add(MckFormat.formatMckNode(headNode));
				}
				// If there is a conjunction that is always true we don't need
				// to compute other clauses
				return "";
			}
			if (conjuntBody.length() >= MckFormat.AND.length()) {
				// Prune last AND
				conjuntBody.delete(conjuntBody.length() - MckFormat.AND.length(), conjuntBody.length());
				conjuntBody.append(" " + GdlParser.CLOSE_P_Str); // ")"
				// If conjunctive body has a false then it's a contradiction and
				// can be pruned
				if (DEBUG || !conjunctBodyHasFalse) {
					disjunctBody.append(conjuntBody.toString() + MckFormat.OR);
					disjunctBodyHasOtherThanFalse = true;
				}
			}
		}
		if (disjunctBody.length() >= MckFormat.OR.length()) {
			// Prune last OR
			disjunctBody.delete(disjunctBody.length() - MckFormat.OR.length(), disjunctBody.length());
		}

		StringBuilder mckNode = new StringBuilder();
		if (!disjunctBodyHasOtherThanFalse) {
			if (!ATc.contains(MckFormat.formatMckNode(headNode))) {
				ATc.add(MckFormat.formatMckNode(headNode));
			}
		} else if (disjunctBody.length() == 0) {
			if (!ATt.contains(MckFormat.formatMckNode(headNode))) {
				ATt.add(MckFormat.formatMckNode(headNode));
			}
		} else if (TRANSITIONS_WITH_DEFINE && !containsDoes) {
			String definedClause = MckFormat.DEFINE + " " + MckFormat.formatMckNode(headNode) + " = " + disjunctBody.toString();
			mckNode.append(System.lineSeparator() + definedClause);
			if (!ATdef.containsKey(MckFormat.formatMckNode(headNode))) {
				ATdef.put(MckFormat.formatMckNode(headNode), definedClause);
			}
		} else if (ONE_LINE_TRANSITIONS) {
			mckNode.append(System.lineSeparator() + MckFormat.formatMckNode(headNode) + " := " + disjunctBody.toString() + ";");
		} else {
			mckNode.append(System.lineSeparator() + "if " + disjunctBody.toString());
			mckNode.append(System.lineSeparator() + " -> " + MckFormat.formatMckNode(headNode) + " := " + MckFormat.TRUE);
			mckNode.append(
					System.lineSeparator() + " [] otherwise -> " + MckFormat.formatMckNode(headNode) + " := " + MckFormat.FALSE);
			mckNode.append(System.lineSeparator() + "fi;");
		}
		return mckNode.toString();
	}

	/**
	 * Do some initializing and pre-processing steps
	 */
	public void initialize() {
		// Pre-processing
		// DependencyGraph
		graph = GdlParser.constructDependencyGraph(root);
		graph.computeStratum();
		for (String old : graph.getDependencyMap().keySet()) {
			if (old.length() >= 5 && old.substring(old.length() - 4).equals(MckFormat.OLD_SUFFIX)) {
				ATf.add(old.substring(5));
			}
		}

		// TODO: find another home for this section. 
		// It's called in constructor but prover is set after construction
		if (USE_PROVER) {
			String mckFormatted = null;
			for (String literal : prover.getLiteralSet()) {
				if (prover.getRuleSet().get(literal) == null) {
					GdlNode literalNode = GdlParser.parseString(literal).getChild(0);
					if (!literalNode.getAtom().equals(GdlNode.TRUE)
							&& !literalNode.getAtom().equals(GdlNode.DOES)) {
						mckFormatted = MckFormat.formatMckNode(literalNode);

						if (!ATc.contains(mckFormatted)) {
							ATc.add(mckFormatted);
						}
					}
				} else if (prover.getRuleSet().get(literal).isEmpty()) {
					GdlNode literalNode = GdlParser.parseString(literal).getChild(0);
					if (!literalNode.getAtom().equals(GdlNode.TRUE)
							&& !literalNode.getAtom().equals(GdlNode.DOES)) {
						mckFormatted = MckFormat.formatMckNode(literalNode);

						if (!ATt.contains(mckFormatted)) {
							ATt.add(mckFormatted);
						}
					}
				}
			}
		}

		for (GdlNode node : root) {
			if (node.getType() == GdlType.CLAUSE) {
				if (!ATh.contains(MckFormat.formatMckNode(node.getChild(0)))) {
					ATh.add(MckFormat.formatMckNode(node.getChild(0)));
				}
			} else if (node.getType() == GdlType.FORMULA) {
				if (node.getParent().getType() == GdlType.ROOT && !node.getAtom().equals(GdlNode.INIT)) {
					if (!ATh.contains(MckFormat.formatMckNode(node))) {
						ATh.add(MckFormat.formatMckNode(node));
					}
					if (!ATt.contains(MckFormat.formatMckNode(node))) {
						ATt.add(MckFormat.formatMckNode(node));
					}
				}

				switch (node.getAtom()) {
				case GdlNode.NOT:
				case GdlNode.BASE:
				case GdlNode.INPUT:
				case GdlNode.DOES:
					// Skip these predicates due to redundancy
					break;
				case GdlNode.DISTINCT:
					if (!AT.contains(MckFormat.formatMckNode(node))) {
						AT.add(MckFormat.formatMckNode(node));
					}
					if (node.getChild(0).toString().equals(node.getChild(1).toString())) {
						if (!ATc.contains(MckFormat.formatMckNode(node))) {
							ATc.add(MckFormat.formatMckNode(node));
						}
					} else {
						if (!ATt.contains(MckFormat.formatMckNode(node))) {
							ATt.add(MckFormat.formatMckNode(node));
						}
					}
				case GdlNode.ROLE:
					if (!AT.contains(MckFormat.formatMckNode(node))) {
						AT.add(MckFormat.formatMckNode(node));
					}
					// Add to ATi
					if (!ATi.contains(MckFormat.formatMckNode(node.getChild(0)))) {
						ATi.add(MckFormat.formatMckNode(node.getChild(0)));
					}
					break;
				case GdlNode.SEES:
					// Add to ATs
					String roleS = MckFormat.formatMckNode(node.getChild(0));
					String sees = MckFormat.formatMckNodeAbs(node.getChild(1));
					if (!ATs.containsKey(roleS)) {
						ATs.put(roleS, new ArrayList<String>());
					}
					if (!ATs.get(roleS).contains(sees)) {
						ATs.get(roleS).add(sees);
					}
					break;
				case GdlNode.LEGAL:
					if (!AT.contains(MckFormat.formatMckNode(node))) {
						AT.add(MckFormat.formatMckNode(node));
					}
					// Add to ATd
					String role = MckFormat.formatMckNode(node.getChild(0));
					String move = MckFormat.formatMckNodeAbs(node.getChild(1));
					if (!ATd.containsKey(role)) {
						ATd.put(role, new ArrayList<String>());
					}
					if (!ATs.containsKey(role)) {
						ATs.put(role, new ArrayList<String>());
					}
					if (!ATd.get(role).contains(move)) {
						ATd.get(role).add(move);
					}
					break;
				case GdlNode.TRUE:
				case GdlNode.NEXT:
					// Add to ATf
					if (!ATf.contains(MckFormat.formatMckNode(node.getChild(0)))) {
						ATf.add(MckFormat.formatMckNode(node.getChild(0)));
					}
					break;
				case GdlNode.INIT:
					// Add to ATi
					if (!ATi.contains(MckFormat.formatMckNode(node.getChild(0)))) {
						ATi.add(MckFormat.formatMckNode(node.getChild(0)));
					}
					break;
				default:
					if (!AT.contains(MckFormat.formatMckNode(node))) {
						AT.add(MckFormat.formatMckNode(node));
					}
				}
			}
		}

		for (String initTrue : ATt) {
			if (!ATi.contains(initTrue)) {
				ATi.add(initTrue);
			}
		}

	}

	/**
	 * Compile all sections of mck to output
	 * 
	 * @return
	 */
	public String toMck() {
		// Pre-process state transitions, agents and protocols

		String stateTrans = "";
		try {
			stateTrans = generateStateTransitions();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Join all of the sections together
		StringBuilder mck = new StringBuilder();
		mck.append("-- MCK file generated using MckTranslator from a GGP game description");
		mck.append(System.lineSeparator() + "-- Vars(AT) = "
				+ (AT.size() + ATf.size() + ATd.keySet().size() + ATs.values().size()));
		mck.append(System.lineSeparator() + "-- Base Vars(ATf) = " + ATf.size());
		mck.append(System.lineSeparator() + "-- Misc Vars(AT/(ATf, ATd, ATi, ATs)) = " + AT.size());
		mck.append(System.lineSeparator() + "-- Does Vars(ATd) = " + ATd.keySet().size());
		mck.append(System.lineSeparator() + "-- Sees Vars(ATs) = " + ATs.size());
		if (DEBUG) {
			mck.append(System.lineSeparator() + "-- Tautologies(ATt) = " + ATt.size());
			mck.append(System.lineSeparator() + "-- Contradictions(ATc) = " + ATc.size());
			mck.append(System.lineSeparator() + "-- Total Removable = " + (ATt.size() + ATc.size()));
		} else {
			mck.append(System.lineSeparator() + "-- Removed Tautologies(ATt) = " + ATt.size());
			mck.append(System.lineSeparator() + "-- Removed Contradictions(ATc) = " + ATc.size());
			mck.append(System.lineSeparator() + "-- Total Removed = " + (ATt.size() + ATc.size()));
		}

		mck.append(System.lineSeparator() + "-- DEBUG: " + DEBUG);
		mck.append(System.lineSeparator() + "-- ONE_LINE_TRANSITIONS: " + ONE_LINE_TRANSITIONS);
		mck.append(System.lineSeparator() + "-- SHOW_PRUNED_VARS: " + SHOW_PRUNED_VARS);
		mck.append(System.lineSeparator() + "-- SYNCHRONIZED_COLLECTIONS: " + SYNCHRONIZED_COLLECTIONS);
		mck.append(System.lineSeparator() + "-- ASSIGNMENTS_IN_ACTION: " + ASSIGNMENT_IN_ACTION);
		mck.append(System.lineSeparator() + "-- DERIVE_INITIAL_CONDITIONS: " + DERIVE_INITIAL_CONDITIONS);
		mck.append(System.lineSeparator() + "-- TRANSITIONS_WITH_DEFINE: " + TRANSITIONS_WITH_DEFINE);
		mck.append(System.lineSeparator() + "-- USE_PROVER: " + USE_PROVER);

		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Environment Variables");
		mck.append(System.lineSeparator() + generateEnvironmentVariables());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Initial Conditions");
		mck.append(System.lineSeparator() + generateInitialConditions());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Agent Definitions");
		mck.append(System.lineSeparator() + generateAgents());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- State Transitions");
		mck.append(System.lineSeparator() + stateTrans);
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Specifications");
		mck.append(System.lineSeparator() + generateSpecification());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Protocol Definitions");
		mck.append(System.lineSeparator() + generateProtocols());
		if (SHOW_PRUNED_VARS) {
			mck.append(System.lineSeparator());
			mck.append(System.lineSeparator() + "-- Tautologies (ATt)");
			mck.append(System.lineSeparator());
			for (String tautology : ATt) {
				mck.append(System.lineSeparator() + "-- " + tautology);
			}
			mck.append(System.lineSeparator());
			mck.append(System.lineSeparator() + "-- Contradiction (ATc)");
			mck.append(System.lineSeparator());
			for (String contradiction : ATc) {
				mck.append(System.lineSeparator() + "-- " + contradiction);
			}
		}

		return mck.toString();
	}

	/**
	 * Generate protocol section of MCK output
	 * @return
	 */
	private String generateProtocols() {
		StringBuilder protocols = new StringBuilder();
		// Signature of protocol
		for (String role : ATd.keySet()) {
			if (TRANSITIONS_WITH_DEFINE) {
				protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
				for (String var : AT) {
					if (!ATdef.containsKey(var)) {
						protocols.append(var + " : Bool, ");
					}
				}
				for (String var : ATf) {
					if (!ATdef.containsKey(var)) {
						protocols.append(var + " : Bool, ");
					}
				}
				for (String move : ATd.get(role)) {
					if (!ATdef.containsKey("legal_" + role + "_" + move)) {
						protocols.append("legal_" + role + "_" + move + " : Bool, ");
					}
				}
				for (String sees : ATs.get(role)) {
					if (!ATdef.containsKey("sees_" + role + "_" + sees)) {
						protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
					}
				}
				if (ASSIGNMENT_IN_ACTION) {
					protocols.append(MckFormat.DOES_PREFIX + role + " : " + MckFormat.ACTION_PREFIX + role + ", ");
				}
				if (protocols.length() > 1 && protocols.charAt(protocols.length() - 2) == ',') {
					protocols.deleteCharAt(protocols.length() - 2);
				}
				protocols.append(")");
				protocols.append(System.lineSeparator());
				
			} else {
				protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
				for (String move : ATd.get(role)) {
					protocols.append("legal_" + role + "_" + move + " : Bool, ");
				}
				for (String sees : ATs.get(role)) {
					protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
				}
				protocols.append("terminal : observable Bool");
				if (ASSIGNMENT_IN_ACTION) {
					protocols.append(", " + MckFormat.DOES_PREFIX + role + " : " + MckFormat.ACTION_PREFIX + role);
				}
				protocols.append(")");
			}
			
			// Local declarations
			protocols.append(System.lineSeparator() + "did : observable " + MckFormat.ACTION_PREFIX + role);
			protocols.append(System.lineSeparator() + "init_cond = did == " + MckFormat.INIT + "_" + role);
			if (TRANSITIONS_WITH_DEFINE) {
				for (String definition : ATdef.values()) {
					protocols.append(definition);
				}
			}
			
			// Transitions of protocol
			protocols.append(System.lineSeparator() + MckFormat.BEGIN);
			if (ASSIGNMENT_IN_ACTION) {
				protocols.append(System.lineSeparator() + "  do terminal -> << " + MckFormat.DOES_PREFIX + role + ".write("
						+ MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role + ") | did := "+ MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role + " >>");
			} else {
				protocols.append(System.lineSeparator() + "  do terminal -> << " + MckFormat.MOVE_PREFIX + MckFormat.STOP + "_"
						+ role + " | did := "+ MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role + " >>");
			}
			protocols.append(System.lineSeparator() + "  [] otherwise ->");
			protocols.append(System.lineSeparator() + "    if  ");
			for (String move : ATd.get(role)) {
				if (ASSIGNMENT_IN_ACTION) {
					protocols.append("legal_" + role + "_" + move + " -> << " + MckFormat.DOES_PREFIX + role + ".write("
							+ MckFormat.MOVE_PREFIX + move + "_" + role + ") | did := "+ MckFormat.MOVE_PREFIX + move + "_" + role + " >>");
				} else {
					protocols.append(
							"legal_" + role + "_" + move + " -> << " + MckFormat.MOVE_PREFIX + move + "_" + role + " | did := "+ MckFormat.MOVE_PREFIX + move + "_" + role + " >>");
				}
				protocols.append(System.lineSeparator() + "    []  ");
			}
			protocols.delete(protocols.length() - 9, protocols.length());
			protocols.append(System.lineSeparator() + "    fi");
			protocols.append(System.lineSeparator() + "  od");
			protocols.append(System.lineSeparator() + MckFormat.END);
		}
		protocols.append(System.lineSeparator());
		return protocols.toString();
	}

	/**
	 * Generate state transition section of MCK output
	 * @return
	 * @throws Exception
	 */
	private String generateStateTransitions() throws Exception {
		StringBuilder state_trans = new StringBuilder();

		// State Transitions
		state_trans.append(System.lineSeparator() + "transitions");
		state_trans.append(System.lineSeparator() + MckFormat.BEGIN);
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Update the did_Agent to current move
		if (!ASSIGNMENT_IN_ACTION) {
			if (!DERIVE_INITIAL_CONDITIONS) {
				state_trans.append(System.lineSeparator() + "if neg initial_state ->");
				state_trans.append(System.lineSeparator() + "begin");
			}
			for (String role : ATd.keySet()) {
				state_trans.append(System.lineSeparator() + "if ");
				for (String move : ATd.get(role)) {
					state_trans.append(MckFormat.ROLE_PREFIX + role + "." + MckFormat.MOVE_PREFIX + move + "_" + role + " -> ");
					state_trans.append(MckFormat.DOES_PREFIX + role + " := " + MckFormat.MOVE_PREFIX + move + "_" + role
							+ System.lineSeparator() + "[] ");
				}
				state_trans.append(MckFormat.ROLE_PREFIX + role + "." + MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role + " -> ");
				state_trans.append(MckFormat.DOES_PREFIX + role + " := " + MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role);
				state_trans.append(System.lineSeparator());
				state_trans.append("[] otherwise -> ");
				state_trans.append(MckFormat.DOES_PREFIX + role + " := " + MckFormat.MOVE_PREFIX + MckFormat.NULL + "_" + role);
				state_trans.append(System.lineSeparator() + "fi;");
			}
			if (!DERIVE_INITIAL_CONDITIONS) {
				if (state_trans.length() > 0) {
					state_trans.deleteCharAt(state_trans.length() - 1);
				}
				state_trans.append(System.lineSeparator() + "end");
				state_trans.append(System.lineSeparator() + "fi;");
			}
		}

		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator() + "if neg terminal ->");
		state_trans.append(System.lineSeparator() + "begin");
		state_trans.append(System.lineSeparator());

		StringBuilder old_values = new StringBuilder();
		if (!DERIVE_INITIAL_CONDITIONS) {
			old_values.append(System.lineSeparator() + "  if neg initial_state ->");
			old_values.append(System.lineSeparator() + "  begin");
		}
		// Update _old variables
		for (String trueNode : ATf) {
			if (trueNode.length() >= 4 && trueNode.substring(trueNode.length() - 4).equals(MckFormat.OLD_SUFFIX)) {
				old_values.append(System.lineSeparator() + "  " + trueNode + " := "
						+ trueNode.substring(0, trueNode.length() - 4) + ";");
			}
		}
		if (old_values.length() > 0 && old_values.charAt(old_values.length() - 1) == ';') {
			if (!DERIVE_INITIAL_CONDITIONS) {			
				old_values.deleteCharAt(old_values.length() - 1);
				old_values.append(System.lineSeparator() + "  end");
				old_values.append(System.lineSeparator() + "  fi;");
			}
			state_trans.append(old_values);
			state_trans.append(System.lineSeparator());
		}

		Set<String> oldSet = new HashSet<String>();
		for (String term : ATf) {
			if (term.length() > MckFormat.OLD_SUFFIX.length()
					&& term.substring(term.length() - MckFormat.OLD_SUFFIX.length()).equals(MckFormat.OLD_SUFFIX)) {
				oldSet.add(term);
			}
		}
		
		StringBuilder reset_initial = new StringBuilder();
		// Make initially true vars false after first turn
		if (!DERIVE_INITIAL_CONDITIONS) {
			state_trans.append(System.lineSeparator() + "  if initial_state -> initial_state := False");
			reset_initial.append(System.lineSeparator() + "  [] otherwise ->");
			reset_initial.append(System.lineSeparator() + "  begin");
		}
		for (String initial : ATi) {
			if (graph.getStratum(initial) == 0 || graph.getStratum(MckFormat.TRUE_PREFIX + initial) == 0) {
				reset_initial.append(System.lineSeparator() + "  " + initial + " := " + MckFormat.FALSE + ";");
			}
		}
		if (reset_initial.length() > 0 && reset_initial.charAt(reset_initial.length() - 1) == ';') {
			if (!DERIVE_INITIAL_CONDITIONS) {
				reset_initial.deleteCharAt(reset_initial.length() - 1);
				reset_initial.append(System.lineSeparator() + "  end");
			}
			state_trans.append(reset_initial);
		}
		if (!DERIVE_INITIAL_CONDITIONS) {
			state_trans.append(System.lineSeparator() + "  fi;");
		}
		state_trans.append(System.lineSeparator());
		
		
		// Add transition rules
		ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
		GdlNode repeatHead = null;
		for (GdlNode clause : root.getChildren()) {
			// Type of clause that isn't BASE or INPUT
			if (clause.getChild(0).getAtom().equals(GdlNode.BASE)
					|| clause.getChild(0).getAtom().equals(GdlNode.INPUT)) {
				continue;
			}
			if (USE_PROVER) {
				if (!(clause instanceof GdlRule)) {
					// Skip if not Rule
					continue;
				}
				if (repeatHead != null && repeatHead.equals(clause.getChild(0).toString())) {
					// Skip multiple clauses for same head
					continue;
				}

				repeatHead = clause.getChild(0);
				
				boolean useDefine = TRANSITIONS_WITH_DEFINE;
				if (useDefine && repeatHead.getAtom().equals(GdlNode.NEXT)) {
					useDefine = false;
				}

				String formattedClause = MckFormat.formatClause(oldSet, prover, (GdlLiteral) repeatHead,
						useDefine, ONE_LINE_TRANSITIONS);

				if (useDefine
						&& formattedClause.length() > (System.lineSeparator() + MckFormat.DEFINE + " ").length()
						&& formattedClause.substring(0, (System.lineSeparator() + MckFormat.DEFINE + " ").length())
								.equals((System.lineSeparator() + MckFormat.DEFINE + " "))) {
					// Check for 'define ' prefix
					defineBasedDeclarations.append(formattedClause);
					String formattedHead = MckFormat.formatMckNode(repeatHead);
					ATdef.put(formattedHead, formattedClause);
				} else {
					state_trans.append(formattedClause);
				}
			} else {
				if (repeatHead != null && clause.getChild(0).toString().equals(repeatHead.toString())) {
					repeatHeadList.add(clause);
				} else {
					if (repeatHead != null) {
						String formattedClause = formatClause(graph, repeatHead, repeatHeadList);
						if (TRANSITIONS_WITH_DEFINE
								&& formattedClause.length() > (System.lineSeparator() + MckFormat.DEFINE + " ").length()
								&& formattedClause
										.substring(0, (System.lineSeparator() + MckFormat.DEFINE + " ").length())
										.equals((System.lineSeparator() + MckFormat.DEFINE + " "))) {
							// Check for 'define ' prefix
							defineBasedDeclarations.append(formattedClause);
						} else {
							state_trans.append(formattedClause);
						}
					}
					repeatHead = clause.getChild(0);
					repeatHeadList = new ArrayList<GdlNode>();
					repeatHeadList.add(clause);
				}
			}
		}
		// Fix to skipping last clause in game
		if (repeatHead != null) {
			String formattedClause = "";
			if (!USE_PROVER) {
				formattedClause = formatClause(graph, repeatHead, repeatHeadList);
			}
			if (TRANSITIONS_WITH_DEFINE
					&& formattedClause.length() >= (System.lineSeparator() + MckFormat.DEFINE + " ").length()
					&& formattedClause.substring(0, (System.lineSeparator() + MckFormat.DEFINE + " ").length())
							.equals(System.lineSeparator() + MckFormat.DEFINE + " ")) {
				// Check for 'define ' prefix
				defineBasedDeclarations.append(formattedClause);
			} else {
				state_trans.append(formattedClause);
			}
		}

		// Conclusion
		state_trans.deleteCharAt(state_trans.length() - 1); // Remove last ';'
		state_trans.append(System.lineSeparator() + MckFormat.END);
		state_trans.append(System.lineSeparator() + "fi");
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator() + MckFormat.END);
		state_trans.append(System.lineSeparator());
		return state_trans.toString();
	}

	/**
	 * Generate specification section of MCK output
	 * @return
	 */
	private String generateSpecification() {
		StringBuilder spec = new StringBuilder();

		// Specification
		for (String role : ATd.keySet()) {
		spec.append(System.lineSeparator() + "--spec_obs = AG(");
			for (String move : ATd.get(role)) {
				spec.append("(legal_" + role + "_" + move + " => Knows " + MckFormat.ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				spec.append(MckFormat.AND);
			}
			if (spec.length() > MckFormat.AND.length()) {
				spec.delete(spec.length() - MckFormat.AND.length(), spec.length());
			}
			spec.append(")");
		}
		spec.append(System.lineSeparator() + "--spec_obs = AG(");
		for (String role : ATd.keySet()) {
			spec.append("((" + MckFormat.DOES_PREFIX + role + " == " + MckFormat.MOVE_PREFIX + MckFormat.STOP + "_"
					+ role + ") => terminal)");
			spec.append(MckFormat.AND);
		}
		spec.delete(spec.length() - MckFormat.AND.length(), spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs = AF terminal");
		spec.append(System.lineSeparator());
		spec.append(System.lineSeparator());

		return spec.toString();
	}

	/**
	 * Generate initial condition section of MCK output
	 * @return
	 */
	private String generateInitialConditions() {
		StringBuilder init_cond = new StringBuilder();

		// Shouldn't need to reference ATt after this
		for (String tautology : ATt) {
			if (!ATi.contains(tautology)) {
				ATi.add(tautology);
			}
		}

		// Add all initial true legal clauses to ATi
		// TODO: fix initial condition bugs
		if (DERIVE_INITIAL_CONDITIONS) {
			for (GdlNode clause : root.getChildren()) {
				if (clause instanceof GdlRule) {
					boolean initHeadHasFalse = false;
					for (int i = 1; i < clause.getChildren().size(); i++) {
						GdlNode bodyLiteral = clause.getChild(i);
						if (bodyLiteral.getAtom().equals(GdlNode.NOT)) {
							bodyLiteral = bodyLiteral.getChild(0); // Child of
																	// NOT
							String formattedNode;
							if (graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(clause.getChild(0)))
									&& graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(bodyLiteral) + MckFormat.OLD_SUFFIX)) {
								formattedNode = MckFormat.formatMckNode(bodyLiteral) + MckFormat.OLD_SUFFIX;
							} else {
								formattedNode = MckFormat.formatMckNode(bodyLiteral);
							}
							if (ATi.contains(formattedNode)) {
								initHeadHasFalse = true;
							}
						} else {
							String formattedNode;
							if (graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(clause.getChild(0)))
									&& graph.hasTerm(MckFormat.TRUE_PREFIX + MckFormat.formatMckNode(bodyLiteral) + MckFormat.OLD_SUFFIX)) {
								formattedNode = MckFormat.formatMckNode(bodyLiteral) + MckFormat.OLD_SUFFIX;
							} else {
								formattedNode = MckFormat.formatMckNode(bodyLiteral);
							}
							if (!ATi.contains(formattedNode)) {
								initHeadHasFalse = true;
							}
						}
					}
					if (!initHeadHasFalse && !ATi.contains(MckFormat.formatMckNode(clause.getChild(0)))) {
						ATi.add(MckFormat.formatMckNode(clause.getChild(0)));
					}
				}
			}
		}

		// Initial Conditions
		init_cond.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {
			if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey(node)) {
				init_cond.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					init_cond.append(MckFormat.TRUE);
				} else {
					init_cond.append(MckFormat.FALSE);
				}
				init_cond.append(MckFormat.AND);
			}
		}
		for (String role : ATd.keySet()) {
			init_cond.append(System.lineSeparator() + MckFormat.DOES_PREFIX + role + " == " + MckFormat.INIT + "_" + role);
			init_cond.append(MckFormat.AND);
		}
		for (String trueVar : ATf) {
			if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey(trueVar)) {
				init_cond.append(System.lineSeparator() + trueVar + " == ");
				if (ATi.contains(trueVar)) {
					init_cond.append(MckFormat.TRUE);
				} else {
					init_cond.append(MckFormat.FALSE);
				}
				init_cond.append(MckFormat.AND);
			}
		}
		if (DERIVE_INITIAL_CONDITIONS) {
			// Remove last conjunction
			init_cond.delete(init_cond.length() - 4, init_cond.length());
		} else {
			init_cond.append(System.lineSeparator() + "initial_state == " + MckFormat.TRUE);
		}
		init_cond.append(System.lineSeparator());
		init_cond.append(System.lineSeparator());

		return init_cond.toString();
	}

	/**
	 * Generate agent declaration section of MCK output
	 * @return
	 */
	private String generateAgents() {
		StringBuilder agents = new StringBuilder();
		for (String role : ATd.keySet()) {
			agents.append(System.lineSeparator() + "agent " + MckFormat.ROLE_PREFIX + role + " \"" + role + "\" (");
			if (TRANSITIONS_WITH_DEFINE) {
				for (String var : AT) {
					if (!ATdef.containsKey(var)) {
						agents.append(var + ", ");
					}
				}
				for (String var : ATf) {
					if (!ATdef.containsKey(var)) {
						agents.append(var + ", ");
					}
				}
			}
			for (String move : ATd.get(role)) {
				if (!ATdef.containsKey("legal_" + role + "_" + move)) {
					agents.append("legal_" + role + "_" + move + ", ");
				}
			}
			for (String sees : ATs.get(role)) {
				if (!ATdef.containsKey("sees_" + role + "_" + sees)) {
					agents.append("sees_" + role + "_" + sees + ", ");
				}
			}
			if (!TRANSITIONS_WITH_DEFINE) {
				agents.append("terminal, ");
			}
			if (!DERIVE_INITIAL_CONDITIONS) {
				agents.append(MckFormat.DOES_PREFIX + role + ", ");
			}
			if (agents.length() > 1 && agents.charAt(agents.length()- 2) == ','){ 
				agents.deleteCharAt(agents.length() - 2);
			}
			agents.append(")");
		}
		agents.append(System.lineSeparator());
		return agents.toString();
	}

	/**
	 * Generate variable declaration section of MCK output
	 * @return
	 */
	private String generateEnvironmentVariables() {

		StringBuilder env_vars = new StringBuilder();

		if (!DEBUG) {
			// Filter out tautologies and contradictions
			for (String tautology : ATt) {
				if (tautology.length() >= 4 && tautology.substring(0, 4).equals(GdlNode.SEES)) {
					continue;
				} else if (tautology.length() >= 5 && tautology.substring(0, 5).equals(GdlNode.LEGAL)) {
					continue;
				}
				if (!ATi.contains(tautology)) {
					ATi.add(tautology);
				}
				if (AT.contains(tautology)) {
					AT.remove(tautology);
				}
			}
		}

		for (String contradiction : ATc) {
			if (contradiction.length() >= 4 && contradiction.substring(0, 4).equals(GdlNode.SEES)) {
				continue;
			} else if (contradiction.length() >= 5 && contradiction.substring(0, 5).equals(GdlNode.LEGAL)) {
				continue;
			}
			if (AT.contains(contradiction)) {
				AT.remove(contradiction);
			}
		}

		// Environment Variables
		for (String role : ATd.keySet()) {
			env_vars.append(System.lineSeparator() + "type " + MckFormat.ACTION_PREFIX + role + " = {");
			for (String move : ATd.get(role)) {
				env_vars.append(MckFormat.MOVE_PREFIX + move + "_" + role + ", ");
			}
			env_vars.append(MckFormat.INIT + "_" + role + ", " + MckFormat.MOVE_PREFIX + MckFormat.STOP + "_" + role + ", "
					+ MckFormat.MOVE_PREFIX + MckFormat.NULL + "_" + role + "}");
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- AT:");
		for (String node : AT) {
			if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey(node)) {
				env_vars.append(System.lineSeparator() + node + " : Bool");
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATf:");
		for (String node : ATf) {
			if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey(node)) {
				env_vars.append(System.lineSeparator() + node + " : Bool");
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATd:");
		for (String role : ATd.keySet()) {
			env_vars.append(System.lineSeparator() + MckFormat.DOES_PREFIX + role + " : " + MckFormat.ACTION_PREFIX + role);
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATs:");
		for (String role : ATs.keySet()) {
			for (String move : ATs.get(role)) {
				if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey("sees_" + role + "_" + move)) {
					env_vars.append(System.lineSeparator() + "sees_" + role + "_" + move + " : Bool");
				}
			}
		}
		env_vars.append(System.lineSeparator());
		if (!DERIVE_INITIAL_CONDITIONS) {
			env_vars.append(System.lineSeparator() + "initial_state : Bool");
			env_vars.append(System.lineSeparator());
		}

		if (TRANSITIONS_WITH_DEFINE) {
			env_vars.append(System.lineSeparator() + "-- Define based Transitions:");
			env_vars.append(defineBasedDeclarations);
			env_vars.append(System.lineSeparator());
		}

		return env_vars.toString();
	}
}