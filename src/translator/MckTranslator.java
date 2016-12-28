package translator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import util.GdlParser;
import util.grammar.GdlFormula;
import util.grammar.GdlNode;
import util.grammar.GdlNode.GdlType;
import util.grammar.GdlRule;
import util.graph.DependencyGraph;

/**
 * Translates GDL-II in infix notation to MCK
 * 
 * @author vedantds
 */
public class MckTranslator {
	public static String MCK_INIT = "INIT".intern();
	public static String MCK_STOP = "STOP".intern();
	public static String MCK_TRUE = "True".intern();
	public static String MCK_FALSE = "False".intern();
	public static String MCK_BEGIN = "begin".intern();
	public static String MCK_END = "end".intern();
	public static String MCK_ROLE_PREFIX = "R_".intern();
	public static String MCK_MOVE_PREFIX = "M_".intern();
	public static String MCK_DOES_PREFIX = "did_".intern();
	public static String MCK_ACTION_PREFIX = "Act_".intern();
	public static String MCK_TRUE_PREFIX = GdlParser.TRUE_PREFIX.intern(); // "true_";
	public static String MCK_OLD_SUFFIX = "_old".intern();
	public static String MCK_AND = " /\\ ".intern();
	public static String MCK_OR = " \\/ ".intern();
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

	private boolean DEBUG;
	private boolean ONE_LINE_TRANSITIONS = true;
	private boolean SHOW_PRUNED_VARS = true;
	private boolean SYNCHRONIZED_COLLECTIONS = false;
	private boolean ASSIGNMENT_IN_ACTION = false; // assign did_role in protocol instead of as a state transition
	private boolean DERIVE_INITIAL_CONDITIONS = true;
	private boolean TRANSITIONS_WITH_DEFINE = false;
	
	public MckTranslator(GdlNode root, boolean TRANSITIONS_WITH_DEFINE, boolean DEBUG) {
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
		initialize();
		this.defineBasedDeclarations = new StringBuilder();
	}
	
	public MckTranslator(GdlNode root, boolean DEBUG){
		this(root, false, DEBUG);
	}

	/**
	 * Link to the format node method in MckFormat utility class
	 * @param node
	 * @return
	 */
	public static String formatMckNode(GdlNode node){
		return MckFormat.formatMckNode(node);
	}
	
	/**
	 * Absolute version of format node which doesn't do any manipulation
	 * 
	 * @param node
	 * @return
	 */
	public static String formatMckNodeAbs(GdlNode node){
		return MckFormat.formatMckNodeAbs(node);
	}

	public String formatClause(GdlNode headNode, List<GdlNode> bodyList) throws Exception {
		return formatClause(graph, headNode, bodyList);
	}

	/**
	 * Reformat a clause from gdl to an equivalent one in mck
	 * 
	 * TODO: add typed variable processing (currently only handles rules
	 * resulting true/false)
	 * 
	 * @param ATf
	 * @param graph
	 * @param headNode
	 * @param bodyList
	 * @return
	 */
	public String formatClause(DependencyGraph graph, GdlNode headNode, List<GdlNode> bodyList) throws Exception{
		// Invalid inputs
		if (bodyList.isEmpty() || headNode.toString().length() == 0){
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
		if (headNode.getAtom().equals(GdlNode.GDL_SEES)) {
			seesClause = true;
		}else if (headNode.getAtom().equals(GdlNode.GDL_NEXT)){
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
				if (clause.getChild(i).getAtom().equals(GdlNode.GDL_NOT)) {
					isNegated = true;
					if (TRANSITIONS_WITH_DEFINE && clause.getChild(i).getChild(0).getAtom().equals(GdlNode.GDL_DOES)) {
						containsDoes = true;
					}
					mckFormatted = formatMckNode(clause.getChild(i).getChild(0));
				} else {
					if (TRANSITIONS_WITH_DEFINE && clause.getChild(i).getAtom().equals(GdlNode.GDL_DOES)) {
						containsDoes = true;
					}
					mckFormatted = formatMckNode(clause.getChild(i));
				}

				if (isNegated) {
					if (DEBUG) {
						if (nextClause 
								&& graph.hasTerm(MckFormat.TRUE_PREFIX + formatMckNode(headNode))
								&& graph.hasTerm(MckFormat.TRUE_PREFIX + mckFormatted + MckFormat.OLD_SUFFIX)) {
							conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
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
							&& !clause.getChild(i).getChild(0).getAtom().equals(GdlNode.GDL_DOES)) {
						// TODO: double check logic in this section
						// Negation of contradiction is always true
						if (DEBUG) {
							conjuntBody.append(MckFormat.TRUE + MckFormat.AND);
						}
					} else if (seesClause && ATf.contains(mckFormatted)) {
						// Append sees clause with old ("not" invariant)
						conjuntBody.append(MckFormat.NOT + " " + mckFormatted + MckFormat.OLD_SUFFIX + MckFormat.AND);
						conjunctBodyHasOtherThanTrue = true;
					} else if (nextClause 
							&& graph.hasTerm(MckFormat.TRUE_PREFIX + formatMckNode(headNode))
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
						if (nextClause 
								&& graph.hasTerm(MckFormat.TRUE_PREFIX + formatMckNode(headNode)) 
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
							&& !clause.getChild(i).getAtom().equals(GdlNode.GDL_DOES)) {
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
					} else if (graph.hasTerm(MckFormat.TRUE_PREFIX + formatMckNode(headNode))
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
				if (!ATt.contains(formatMckNode(headNode))) {
					ATt.add(formatMckNode(headNode));
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
			if (!ATc.contains(formatMckNode(headNode))) {
				ATc.add(formatMckNode(headNode));
			}
		} else if (disjunctBody.length() == 0) {
			if (!ATt.contains(formatMckNode(headNode))) {
				ATt.add(formatMckNode(headNode));
			}
		} else if (TRANSITIONS_WITH_DEFINE && !containsDoes) {
			String definedClause = MckFormat.DEFINE + " " + formatMckNode(headNode) + " = " + disjunctBody.toString();
			mckNode.append(System.lineSeparator() + definedClause);
			if (!ATdef.containsKey(formatMckNode(headNode))) {
				ATdef.put(formatMckNode(headNode), definedClause);
			}
		} else if (ONE_LINE_TRANSITIONS) {
			mckNode.append(System.lineSeparator() + formatMckNode(headNode) + " := " + disjunctBody.toString() + ";");
		} else {
			mckNode.append(System.lineSeparator() + "if " + disjunctBody.toString());
			mckNode.append(System.lineSeparator() + " -> " + formatMckNode(headNode) + " := " + MckFormat.TRUE);
			mckNode.append(System.lineSeparator() + " [] otherwise -> " + formatMckNode(headNode) + " := " + MckFormat.FALSE);
			mckNode.append(System.lineSeparator() + "fi;");
		}
		return mckNode.toString();
	}

	public void initialize() {
		// Pre-processing
		// DependencyGraph
		graph = GdlParser.constructDependencyGraph(root);
		graph.computeStratum();
		for (String old : graph.getDependencyMap().keySet()) {
			if (old.length() >= 5 && old.substring(old.length() - 4).equals(MCK_OLD_SUFFIX)) {
				ATf.add(old.substring(5));
			}
		}

		for (GdlNode node : root) {
			if (node.getType() == GdlType.CLAUSE) {
				if (!ATh.contains(formatMckNode(node.getChildren().get(0)))) {
					ATh.add(formatMckNode(node.getChildren().get(0)));
				}
			} else if (node.getType() == GdlType.FORMULA) {
				if (node.getParent().getType() == GdlType.ROOT && !node.getAtom().equals(GdlNode.GDL_INIT)) {
					if (!ATh.contains(formatMckNode(node))) {
						ATh.add(formatMckNode(node));
					}
					if (!ATt.contains(formatMckNode(node))) {
						ATt.add(formatMckNode(node));
					}
				}

				switch (node.getAtom()) {
				case GdlNode.GDL_NOT:
				case GdlNode.GDL_BASE:
				case GdlNode.GDL_INPUT:
				case GdlNode.GDL_DOES:
					// Skip these predicates due to redundancy
					break;
				case GdlNode.GDL_DISTINCT:
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
					if (node.getChildren().get(0).toString().equals(node.getChildren().get(1).toString())) {
						if (!ATc.contains(formatMckNode(node))) {
							ATc.add(formatMckNode(node));
						}
					} else {
						if (!ATt.contains(formatMckNode(node))) {
							ATt.add(formatMckNode(node));
						}
					}
				case GdlNode.GDL_ROLE:
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
					// Add to ATi
					if (!ATi.contains(formatMckNode(node.getChildren().get(0)))) {
						ATi.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				case GdlNode.GDL_SEES:
					// Add to ATs
					String roleS = formatMckNode(node.getChildren().get(0));
					String sees = formatMckNodeAbs(node.getChildren().get(1));
					if (!ATs.containsKey(roleS)) {
						ATs.put(roleS, new ArrayList<String>());
					}
					if (!ATs.get(roleS).contains(sees)) {
						ATs.get(roleS).add(sees);
					}
					break;
				case GdlNode.GDL_LEGAL:
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
					// Add to ATd
					String role = formatMckNode(node.getChildren().get(0));
					String move = formatMckNodeAbs(node.getChildren().get(1));
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
				case GdlNode.GDL_TRUE:
				case GdlNode.GDL_NEXT:
					// Add to ATf
					if (!ATf.contains(formatMckNode(node.getChildren().get(0)))) {
						ATf.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				case GdlNode.GDL_INIT:
					// Add to ATi
					if (!ATi.contains(formatMckNode(node.getChildren().get(0)))) {
						ATi.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				default:
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
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
	 * @return
	 */
	public String toMck() {
		// Pre-process state transitions, agents and protocols
		
		String stateTrans = "";
		try {
			stateTrans = generateStateTransitions();
		}catch (Exception e){
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

	private String generateProtocols(){
		StringBuilder protocols = new StringBuilder();
		for (String role : ATd.keySet()) {
			//protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
			//for (String move : ATd.get(role)) {
			//	protocols.append("legal_" + role + "_" + move + " : Bool, ");
			//}
			//for (String sees : ATs.get(role)) {
			//	protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
			//}
			//protocols.append(MCK_DOES_PREFIX + role + " : " + MCK_ACTION_PREFIX + role + ", ");
			//protocols.append("terminal : observable Bool");
			//protocols.append(")");
			if (TRANSITIONS_WITH_DEFINE) {
				protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
				for (String var : AT) {
					if (!ATdef.containsKey(var)){
						protocols.append(var + " : Bool, ");
					}
				}for (String var : ATf) {
					if (!ATdef.containsKey(var)){
						protocols.append(var + " : Bool, ");
					}
				}
				for (String move : ATd.get(role)) {
					if (!ATdef.containsKey("legal_" + role + "_" + move)){
						protocols.append("legal_" + role + "_" + move + " : Bool, ");
					}
				}
				for (String sees : ATs.get(role)) {
					if (!ATdef.containsKey("sees_" + role + "_" + sees)) {
						protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
					}
				}
				//protocols.append("terminal : observable Bool");
				protocols.append(MCK_DOES_PREFIX + role + " : " + MCK_ACTION_PREFIX + role);
				protocols.append(")");
				protocols.append(System.lineSeparator());
				for (String definition : ATdef.values()) {
					protocols.append(System.lineSeparator() + definition);
				}
			} else {
				protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
				for (String move : ATd.get(role)) {
					protocols.append("legal_" + role + "_" + move + " : Bool, ");
				}
				for (String sees : ATs.get(role)) {
					protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
				}
				protocols.append("terminal : observable Bool, ");
				protocols.append(MCK_DOES_PREFIX + role + " : " + MCK_ACTION_PREFIX + role);
				protocols.append(")");
			}
			protocols.append(System.lineSeparator() + MCK_BEGIN);
			if (ASSIGNMENT_IN_ACTION) {
				protocols.append(System.lineSeparator() + "  if terminal -> << " + MCK_DOES_PREFIX + role + ".write(" + MCK_MOVE_PREFIX + MCK_STOP + "_" + role + ") >>");
			} else {
				protocols.append(System.lineSeparator() + "  if terminal -> << " + MCK_MOVE_PREFIX + MCK_STOP + "_" + role + " >>");
			}
			protocols.append(System.lineSeparator() + "  [] otherwise ->");
			protocols.append(System.lineSeparator() + "    if  ");
			for (String move : ATd.get(role)) {
				if (ASSIGNMENT_IN_ACTION) {
					protocols.append("legal_" + role + "_" + move + " -> << " + MCK_DOES_PREFIX + role + ".write(" + MCK_MOVE_PREFIX + move +  "_" + role + ") >>");
				} else {
					protocols.append("legal_" + role + "_" + move + " -> << " + MCK_MOVE_PREFIX + move + "_" + role + " >>");
				}
				protocols.append(System.lineSeparator() + "    []  ");
			}
			protocols.delete(protocols.length() - 9, protocols.length());
			protocols.append(System.lineSeparator() + "    fi");
			protocols.append(System.lineSeparator() + "  fi");
			protocols.append(System.lineSeparator() + MCK_END);
		}
		protocols.append(System.lineSeparator());
		return protocols.toString();
	}
	
	private String generateStateTransitions() throws Exception{
		StringBuilder state_trans = new StringBuilder();

		// State Transitions
		state_trans.append(System.lineSeparator() + "transitions");
		state_trans.append(System.lineSeparator() + MCK_BEGIN);
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Update the did_Agent to current move
		if (!ASSIGNMENT_IN_ACTION) {	
			for (String role : ATd.keySet()) {
				state_trans.append(System.lineSeparator() + "if ");
				for (String move : ATd.get(role)) {
					state_trans.append(MCK_ROLE_PREFIX + role + "." + MCK_MOVE_PREFIX + move +  "_" + role + " -> ");
					state_trans.append(
						MCK_DOES_PREFIX + role + " := " + MCK_MOVE_PREFIX + move +  "_" + role + System.lineSeparator() + "[] ");
				}
				//state_trans.append(MCK_ROLE_PREFIX + role + "." + MCK_INIT +  "_" + role + " -> ");
				//state_trans.append(MCK_DOES_PREFIX + role + " := " + MCK_INIT +  "_" + role);
				//state_trans.append(System.lineSeparator() + "[] ");
				state_trans.append(MCK_ROLE_PREFIX + role + "." + MCK_MOVE_PREFIX + MCK_STOP + "_"  + role + " -> ");
				state_trans.append(MCK_DOES_PREFIX + role + " := " + MCK_MOVE_PREFIX + MCK_STOP + "_"  + role);
				state_trans.append(System.lineSeparator() + "fi;");
			}
		}
		
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Update _old variables
		for (String trueNode : ATf) {
			if (trueNode.length() >= 4 && trueNode.substring(trueNode.length() - 4).equals(MCK_OLD_SUFFIX)) {
				state_trans.append(System.lineSeparator() + trueNode + " := "
						+ trueNode.substring(0, trueNode.length() - 4) + ";");
			}
		}
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Add transition rules
		ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
		GdlNode repeatHead = null;
		for (GdlNode clause : root.getChildren()) {
			// Type of clause that isn't BASE or INPUT
			if (clause.getType() == GdlType.CLAUSE && !clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_BASE)
					&& !clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_INPUT)) {

				if (repeatHead != null && clause.getChildren().get(0).toString().equals(repeatHead.toString())) {
					repeatHeadList.add(clause);
				} else {
					if (repeatHead != null) {
						String formattedClause = formatClause(graph, repeatHead, repeatHeadList);
						if (TRANSITIONS_WITH_DEFINE 
								&& formattedClause.length() > (System.lineSeparator() + MckFormat.DEFINE + " ").length()
								&& formattedClause.substring(0, (System.lineSeparator() + MckFormat.DEFINE + " ").length()).equals((System.lineSeparator() + MckFormat.DEFINE + " "))) {
							// Check for 'define ' prefix
							defineBasedDeclarations.append(formattedClause);
						} else {
							state_trans.append(formattedClause);
						}
					}
					repeatHead = clause.getChildren().get(0);
					repeatHeadList = new ArrayList<GdlNode>();
					repeatHeadList.add(clause);
				}
			}
		}
		// Fix to skipping last clause in game
		if (repeatHead != null) {
			String formattedClause = formatClause(graph, repeatHead, repeatHeadList);
			if (TRANSITIONS_WITH_DEFINE 
					&& formattedClause.length() >= (System.lineSeparator() + MckFormat.DEFINE + " ").length()
					&& formattedClause.substring(0, (System.lineSeparator() + MckFormat.DEFINE + " ").length()).equals(System.lineSeparator() + MckFormat.DEFINE + " ")) {
				// Check for 'define ' prefix
				defineBasedDeclarations.append(formattedClause);
			} else {
				state_trans.append(formattedClause);
			}
		}
		
		// Make initially true vars false after first turn
		for(String initial : ATi){
			if (graph.getStratum(initial) == 0 || graph.getStratum(MCK_TRUE_PREFIX + initial) == 0) {
				state_trans.append(System.lineSeparator() + initial + " := " + MCK_FALSE + ";");
			}
		}
		
		// Conclusion
		state_trans.deleteCharAt(state_trans.length() - 1); // Remove last ';'
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator() + MCK_END);
		state_trans.append(System.lineSeparator());
		return state_trans.toString();
	}
	
	private String generateSpecification() {
		StringBuilder spec = new StringBuilder();

		// Specification
		spec.append(System.lineSeparator() + "--spec_spr = AG(");
		for (String role : ATd.keySet()) {
			for (String move : ATd.get(role)) {
				spec.append("(legal_" + role + "_" + move + " => Knows " + MCK_ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				spec.append(MCK_AND);
			}
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_spr = AG(");
		for (String role : ATd.keySet()) {
			spec.append("(neg terminal => neg (" + MCK_DOES_PREFIX + role + " == " + MCK_MOVE_PREFIX +MCK_STOP +  "_" + role + "))");
			spec.append(MCK_AND);
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs = AG(");
		for (String role : ATd.keySet()) {
			for (String move : ATd.get(role)) {
				spec.append("(legal_" + role + "_" + move + " => Knows " + MCK_ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				spec.append(MCK_AND);
			}
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs = AG(");
		for (String role : ATd.keySet()) {
			spec.append("(neg terminal => neg (" + MCK_DOES_PREFIX + role + " == " + MCK_MOVE_PREFIX + role + "_" + MCK_STOP + "))");
			spec.append(MCK_AND);
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs = AF terminal");
		spec.append(System.lineSeparator());
		spec.append(System.lineSeparator());

		return spec.toString();
	}

	private String generateInitialConditions() {
		StringBuilder init_cond = new StringBuilder();

		// Shouldn't need to reference ATt after this
		for (String tautology : ATt) {
			if (!ATi.contains(tautology)) {
				ATi.add(tautology);
			}
		}
		
		//System.out.println("gen init start " + ATi.toString());
		// Add all initial true legal clauses to ATi
		// TODO: fix initial condition bugs
		if (DERIVE_INITIAL_CONDITIONS) {
			for (GdlNode clause : root.getChildren()) {
				if (clause instanceof GdlRule) {
					boolean initHeadHasFalse = false;
					for (int i = 1; i < clause.getChildren().size(); i++) {
						GdlNode bodyLiteral = clause.getChild(i);
						if (bodyLiteral.getAtom().equals(GdlNode.GDL_NOT)) {
							bodyLiteral = bodyLiteral.getChild(0); // Child of NOT
							String formattedNode;
							if (graph.hasTerm(MCK_TRUE_PREFIX + formatMckNode(clause.getChild(0))) && 
									graph.hasTerm(MCK_TRUE_PREFIX + formatMckNode(bodyLiteral) + MCK_OLD_SUFFIX)) {
								formattedNode = formatMckNode(bodyLiteral) + MCK_OLD_SUFFIX;
							} else {
								formattedNode = formatMckNode(bodyLiteral);
							}
							if (ATi.contains(formattedNode)) {
								initHeadHasFalse = true;
							}
						} else {
							String formattedNode;
							if (graph.hasTerm(MCK_TRUE_PREFIX + formatMckNode(clause.getChild(0))) && 
									graph.hasTerm(MCK_TRUE_PREFIX + formatMckNode(bodyLiteral) + MCK_OLD_SUFFIX)) {
								formattedNode = formatMckNode(bodyLiteral) + MCK_OLD_SUFFIX;
							} else {
								formattedNode = formatMckNode(bodyLiteral);
							}
							if (!ATi.contains(formattedNode)) {
								initHeadHasFalse = true;
							}
						}
					}
					if (!initHeadHasFalse && !ATi.contains(formatMckNode(clause.getChild(0)))) {
						ATi.add(formatMckNode(clause.getChild(0)));
					}
				}
			}
		}
		//System.out.println("gen init end " + ATi.toString());

		// Initial Conditions
		init_cond.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {
			if (!ATdef.containsKey(node)) {
				init_cond.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					init_cond.append(MCK_TRUE);
				} else {
					init_cond.append(MCK_FALSE);
				}
				init_cond.append(MCK_AND);
			}
		}
		for (String role : ATd.keySet()) {
			init_cond.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " == " + MCK_INIT + "_" + role);
			init_cond.append(MCK_AND);
		}
		for (String trueVar : ATf) {
			if (!ATdef.containsKey(trueVar)) {
				init_cond.append(System.lineSeparator() + trueVar + " == ");
				if (ATi.contains(trueVar)) {
					init_cond.append(MCK_TRUE);
				} else {
					init_cond.append(MCK_FALSE);
				}
				init_cond.append(MCK_AND);
			}
		}
		// Remove last conjunction
		init_cond.delete(init_cond.length() - 4, init_cond.length());
		init_cond.append(System.lineSeparator());
		init_cond.append(System.lineSeparator());

		return init_cond.toString();
	}

	private String generateAgents(){
		StringBuilder agents = new StringBuilder();
		for (String role : ATd.keySet()) {
			agents.append(System.lineSeparator() + "agent " + MCK_ROLE_PREFIX + role + " \"" + role + "\" (");
			if (TRANSITIONS_WITH_DEFINE) {
				for (String var : AT) {
					if (!ATdef.containsKey(var)){
						agents.append(var + ", ");
					}
				}for (String var : ATf) {
					if (!ATdef.containsKey(var)){
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
			agents.append(MCK_DOES_PREFIX + role);
			agents.append(")");
		}
		agents.append(System.lineSeparator());
		return agents.toString();
	}
	
	private String generateEnvironmentVariables() {

		StringBuilder env_vars = new StringBuilder();

		if (!DEBUG) {
			// Filter out tautologies and contradictions
			for (String tautology : ATt) {
				if (tautology.length() >= 4 && tautology.substring(0, 4).equals(GdlNode.GDL_SEES)) {
					continue;
				} else if (tautology.length() >= 5 && tautology.substring(0, 5).equals(GdlNode.GDL_LEGAL)) {
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
			if (contradiction.length() >= 4 && contradiction.substring(0, 4).equals(GdlNode.GDL_SEES)) {
				continue;
			} else if (contradiction.length() >= 5 && contradiction.substring(0, 5).equals(GdlNode.GDL_LEGAL)) {
				continue;
			}
			if (AT.contains(contradiction)) {
				AT.remove(contradiction);
			}
		}

		// Environment Variables
		for (String role : ATd.keySet()) {
			env_vars.append(System.lineSeparator() + "type " + MCK_ACTION_PREFIX + role + " = {");
			for (String move : ATd.get(role)) {
				env_vars.append(MCK_MOVE_PREFIX + move +  "_" + role + ", ");
			}
			env_vars.append(MCK_INIT + "_" + role + ", " + MCK_MOVE_PREFIX+ MCK_STOP +  "_" + role + "}");
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- AT:");
		for (String node : AT) {
			if (!ATdef.containsKey(node)) {
				env_vars.append(System.lineSeparator() + node + " : Bool");
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATf:");
		for (String node : ATf) {
			if (!ATdef.containsKey(node)) {
				env_vars.append(System.lineSeparator() + node + " : Bool");
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATd:");
		for (String role : ATd.keySet()) {
			env_vars.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " : " + MCK_ACTION_PREFIX + role);
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATs:");
		for (String role : ATs.keySet()) {
			for (String move : ATs.get(role)) {
				if (!ATdef.containsKey("sees_" + role + "_" + move)) {
					env_vars.append(System.lineSeparator() + "sees_" + role + "_" + move + " : Bool");
				}
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator());
		
		if (TRANSITIONS_WITH_DEFINE) {
			env_vars.append(System.lineSeparator() + "-- Define based Transitions:");
			env_vars.append(defineBasedDeclarations);
			env_vars.append(System.lineSeparator());
			env_vars.append(System.lineSeparator());
		}

		return env_vars.toString();
	}
}