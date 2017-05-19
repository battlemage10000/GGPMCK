package translator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import prover.GdlRuleSet;
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
	public Set<String> ATi;
	// List of variables which are always true(totality)
	private Set<String> ATt;
	// List of variables which are always false(contradiction)
	public Set<String> ATc;
	// List of formulae which are heads of clauses or facts
	private Set<String> ATh;
	// [Role -> Move] move map from legal
	public Map<String, List<String>> ATd;
	// [Role -> Sees] observation map from sees
	private Map<String, List<String>> ATs;
	// List of variables using the define keyword
	private Map<String, String> ATdef;
	private StringBuilder defineBasedDeclarations;
	
	private Set<String> oldSet;

	@Deprecated private GdlNode root;
	@Deprecated private DependencyGraph graph;
	private GdlRuleSet ruleSet;

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

	public MckTranslator(GdlRuleSet ruleSet, boolean TRANSITIONS_WITH_DEFINE, boolean DEBUG) {
		//this.root = null;
		this.ruleSet = ruleSet;
		this.USE_PROVER = true;
		this.TRANSITIONS_WITH_DEFINE = TRANSITIONS_WITH_DEFINE;
		this.DEBUG = DEBUG;
		this.SHOW_PRUNED_VARS = DEBUG;
		if (SYNCHRONIZED_COLLECTIONS) {
			this.oldSet = Collections.synchronizedSet(new HashSet<String>());
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
			this.oldSet = new HashSet<String>();
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
		initialize(true);
	}

	
	/**
	 * Set pre-initialized prover
	 * @param prover
	 */
	public void setProver(GdlRuleSet prover) {
		if (prover != null) {
			this.ruleSet = prover;
			USE_PROVER = true;
		}
	}

	public void initialize(boolean useRuleSet) {
		if (!useRuleSet) {
		//	initialize();
			return;
		}

		for (String head : ruleSet.getRuleSet().keySet()) {
			GdlNode headNode = GdlParser.parseString(head).getChild(0);
			switch (headNode.getAtom()) {
			case GdlNode.DOES:
			case GdlNode.INPUT:
			case GdlNode.BASE:
				break;
			case GdlNode.ROLE:
				if (ATd.get(headNode.getChild(0).toString()) == null) {
					ATd.put(headNode.getChild(0).toString(), new ArrayList<String>());
				}
				if (ATs.get(headNode.getChild(0).toString()) == null) {
					ATs.put(headNode.getChild(0).toString(), new ArrayList<String>());
				}
				break;
			case GdlNode.INIT:
				ATi.add(MckFormat.formatMckNode(headNode));
				break;
			//case GdlNode.TRUE: //TODO: TRUE should never be in head
			case GdlNode.NEXT:
				ATf.add(MckFormat.formatMckNode(headNode));
				break;
			case GdlNode.LEGAL:
				AT.add(MckFormat.formatMckNode(headNode));
				if (ATd.get(headNode.getChild(0).toString()) == null) {
					ATd.put(headNode.getChild(0).toString(), new ArrayList<String>());
				}
				if (!ATd.get(headNode.getChild(0).toString())
						.contains(MckFormat.formatMckNode(headNode.getChild(1)))) {
					ATd.get(headNode.getChild(0).toString()).add(MckFormat.formatMckNode(headNode.getChild(1)));
				}
				break;
			case GdlNode.SEES:
				if (ATs.get(headNode.getChild(0).toString()) == null) {
					ATs.put(headNode.getChild(0).toString(), new ArrayList<String>());
				}
				if (!ATs.get(headNode.getChild(0).toString()).contains(MckFormat.formatMckNodeAbs(headNode.getChild(1)).toString())) {
					ATs.get(headNode.getChild(0).toString()).add(MckFormat.formatMckNodeAbs(headNode.getChild(1)));
				}
				break;
			default:
				AT.add(MckFormat.formatMckNode(headNode));
			}
		}
		
		// getLiteralSet is non-negative only
		for (String literal : ruleSet.getLiteralSet()) {
			GdlNode literalNode = GdlParser.parseString(literal).getChild(0);
			if (ruleSet.getRuleSet().get(literal) == null) {
				if (literalNode.getAtom().equals(GdlNode.TRUE)) {
					ATf.add(MckFormat.formatMckNode(literalNode));
				} else if (!literalNode.getAtom().equals(GdlNode.DOES)) {
					ATc.add(MckFormat.formatMckNode(literalNode));
				}
			} else if (ruleSet.getRuleSet().get(literal).isEmpty()) {
				if (!literalNode.getAtom().equals(GdlNode.TRUE) && !literalNode.getAtom().equals(GdlNode.DOES)) {
					ATt.add(MckFormat.formatMckNode(literalNode));
				}
			} else {
				ATh.add(MckFormat.formatMckNode(literalNode));
			}
		}
		
		// Initialize oldSet from ruleSet
		for (String oldLit : ruleSet.getOldSet()) {
			oldSet.add((MckFormat.formatMckNode(GdlParser.parseString(oldLit).getChild(0)) + MckFormat.OLD_SUFFIX).intern());
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
			stateTrans = generateStateTransitions(USE_PROVER);
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
		mck.append(System.lineSeparator() + generateInitialConditions(USE_PROVER));
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
	protected String generateProtocols() {
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
					protocols.append(System.lineSeparator() + definition);
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
	protected String generateStateTransitions(boolean useRuleSet) throws Exception {
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
		for (String trueNode : oldSet) {
			if (trueNode.length() > MckFormat.OLD_SUFFIX.length() && trueNode.substring(trueNode.length() - 4).equals(MckFormat.OLD_SUFFIX)) {
				old_values.append(System.lineSeparator() + "  " + trueNode + " := "
						+ trueNode.substring(0, trueNode.length() - MckFormat.OLD_SUFFIX.length()) + ";");
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
		
		StringBuilder reset_initial = new StringBuilder();
		// Make initially true vars false after first turn
		if (!DERIVE_INITIAL_CONDITIONS) {
			state_trans.append(System.lineSeparator() + "  if initial_state -> initial_state := False");
			reset_initial.append(System.lineSeparator() + "  [] otherwise ->");
			reset_initial.append(System.lineSeparator() + "  begin");
		}
		if (!useRuleSet) {
			for (String initial : ATi) {
				graph.toString();
				if (graph.getStratum(initial) == 0 || graph.getStratum(MckFormat.TRUE_PREFIX + initial + ")") == 0) {
					reset_initial.append(System.lineSeparator() + "  " + initial + " := " + MckFormat.FALSE + ";");
				}
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

		if (useRuleSet) {
			int stratum = 0;
			PriorityQueue<String> orderedGdl = ruleSet.getOrderedSet();
			while(!orderedGdl.isEmpty()) {
				if (ruleSet.getRuleSet().get(orderedGdl.peek()) == null || ruleSet.getRuleSet().get(orderedGdl.peek()).isEmpty()) {
					orderedGdl.poll();
					continue;
				}
				GdlNode headNode = GdlParser.parseString(orderedGdl.poll()).getChild(0);
				
				boolean useDefine = TRANSITIONS_WITH_DEFINE;
				if (useDefine && headNode.getAtom().equals(GdlNode.NEXT)) {
					useDefine = false;
				}
				String formattedClause = MckFormat.formatClause(oldSet, ruleSet, (GdlLiteral) headNode, useDefine,
						ONE_LINE_TRANSITIONS);
				if (!useDefine && ruleSet.getStratum(headNode.toString()) != stratum) {
					stratum = ruleSet.getStratum(headNode.toString());
					state_trans.append(System.lineSeparator() + "  --stratum: " + stratum);
				}
				if (useDefine) {
					ATdef.put(MckFormat.formatMckNode(headNode), formattedClause);
				} else {
					state_trans.append(System.lineSeparator() + "  " + formattedClause);
				}
			}
		} else {
			// Add transition rules
			ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
			GdlNode repeatHead = null;
			for (GdlNode clause : root.getChildren()) {
				// Type of clause that isn't BASE or INPUT
				if (clause.getAtom().equals(GdlNode.BASE) || clause.getAtom().equals(GdlNode.BASE)) {
					continue;
				} else if (!clause.getChildren().isEmpty() && (clause.getChild(0).getAtom().equals(GdlNode.BASE)
						|| clause.getChild(0).getAtom().equals(GdlNode.INPUT))) {
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

					String formattedClause = MckFormat.formatClause(oldSet, ruleSet, (GdlLiteral) repeatHead, useDefine,
							ONE_LINE_TRANSITIONS);

					if (useDefine && formattedClause.length() > (MckFormat.DEFINE + " ").length() && formattedClause
							.substring(0, (MckFormat.DEFINE + " ").length()).equals(MckFormat.DEFINE + " ")) {
						// Check for 'define ' prefix
						defineBasedDeclarations.append(formattedClause);
						ATdef.put(MckFormat.formatMckNode(repeatHead), formattedClause);
					} else if (formattedClause.length() > 0) {
						state_trans.append(System.lineSeparator() + "  " + formattedClause);
					}
				} else {
					/*
					if (repeatHead != null && clause.getChild(0).toString().equals(repeatHead.toString())) {
						repeatHeadList.add(clause);
					} else {
						if (repeatHead != null) {
							String formattedClause = formatClause(graph, repeatHead, repeatHeadList);
							if (TRANSITIONS_WITH_DEFINE && formattedClause.length() > (MckFormat.DEFINE + " ").length()
									&& formattedClause.substring(0, (MckFormat.DEFINE + " ").length())
											.equals(MckFormat.DEFINE + " ")) {
								// Check for 'define ' prefix
								defineBasedDeclarations.append(formattedClause);
								ATdef.put(MckFormat.formatMckNode(repeatHead), formattedClause);
							} else {
								state_trans.append(formattedClause);
							}
						}
						repeatHead = clause.getChild(0);
						repeatHeadList = new ArrayList<GdlNode>();
						repeatHeadList.add(clause);
					}
					*/
				}
			}
			// Fix to skipping last clause in game
			if (repeatHead != null) {
				String formattedClause = "";
				if (!USE_PROVER) {
					//formattedClause = formatClause(graph, repeatHead, repeatHeadList);
				}
				if (TRANSITIONS_WITH_DEFINE && formattedClause.length() >= (MckFormat.DEFINE + " ").length()
						&& formattedClause.substring(0, (MckFormat.DEFINE + " ").length())
								.equals(MckFormat.DEFINE + " ")) {
					// Check for 'define ' prefix
					defineBasedDeclarations.append(formattedClause);
					ATdef.put(MckFormat.formatMckNode(repeatHead), formattedClause);
				} else {
					state_trans.append(formattedClause);
				}
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
	protected String generateSpecification() {
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
	protected String generateInitialConditions(boolean useRuleSet) {
		StringBuilder init_cond = new StringBuilder();

		// Shouldn't need to reference ATt after this
		for (String tautology : ATt) {
			if (!ATi.contains(tautology)) {
				ATi.add(tautology);
			}
		}

		// Add all initial true legal clauses to ATi
		// TODO: fix initial condition bugs
		if (DERIVE_INITIAL_CONDITIONS && useRuleSet) {
			Set<String> initialSet = ruleSet.generateInitialModel().getModel();
			ATi.clear();
			for (String init : initialSet) {
				if (init.length() > GdlNode.NEXT.length() + 1 && !init.substring(1, GdlNode.NEXT.length() + 1).equals(GdlNode.NEXT)) {
					ATi.add(MckFormat.formatMckNode(GdlParser.parseString(init).getChild(0)));
				}
			}
		} else if (DERIVE_INITIAL_CONDITIONS) {
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
	protected String generateAgents() {
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
	protected String generateEnvironmentVariables() {

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
		if (USE_PROVER) {
			for (String oldNode : oldSet) {
				if (!TRANSITIONS_WITH_DEFINE || !ATdef.containsKey(oldNode)) {
					env_vars.append(System.lineSeparator() + oldNode + " : Bool");
				}
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
			for (String defHead : ATdef.keySet()) {
				env_vars.append(System.lineSeparator() + ATdef.get(defHead));
			}
			env_vars.append(System.lineSeparator());
		}

		return env_vars.toString();
	}
}