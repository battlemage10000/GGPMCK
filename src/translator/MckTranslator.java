package translator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import util.GdlParser;
import util.grammar.GdlNode;
import util.grammar.GdlNode.GdlType;
import util.graph.DependencyGraph;

/**
 * Translates GDL-II in infix notation to MCK
 * 
 * @author vedantds
 */
public class MckTranslator {
	public static String MCK_INIT = "INIT";
	public static String MCK_STOP = "STOP";
	public static String MCK_ROLE_PREFIX = "R_";
	public static String MCK_MOVE_PREFIX = "M_";
	public static String MCK_DOES_PREFIX = "did_";
	public static String MCK_ACTION_PREFIX = "Act_";
	public static String MCK_TRUE_PREFIX = "true_";
	public static String MCK_OLD_SUFFIX = "_old";
	public static String MCK_AND = " /\\ ";
	public static String MCK_OR = " \\/ ";
	public static String MCK_TRUE = "True";
	public static String MCK_FALSE = "False";
	public static boolean ONE_LINE_TRANSITIONS = true;
	// Variables
	private Set<String> AT;
	// Variables found in true and/or next
	private Set<String> ATf;
	// List of variables which are true in the initial state
	private Set<String> ATi;
	// List of variables which are always true(totality)
	private Set<String> ATt;
	// List of variables which are always false(contradiction)
	private Set<String> ATc;
	// List of formulae which are heads of clauses or facts
	private Set<String> ATh;
	// [Role -> Move] move map from legal
	private Map<String, List<String>> ATd;
	// [Role -> Sees] observation map from sees
	private Map<String, List<String>> ATs;

	private GdlNode root;

	private boolean DEBUG;

	public MckTranslator(GdlNode root, boolean DEBUG) {
		this.root = root;
		this.DEBUG = DEBUG;
		this.AT = Collections.synchronizedSet(new HashSet<String>());
		this.ATf = Collections.synchronizedSet(new HashSet<String>());
		this.ATi = Collections.synchronizedSet(new HashSet<String>());
		this.ATt = Collections.synchronizedSet(new HashSet<String>());
		this.ATc = Collections.synchronizedSet(new HashSet<String>());
		this.ATh = Collections.synchronizedSet(new HashSet<String>());
		this.ATd = Collections.synchronizedMap(new HashMap<String, List<String>>());
		this.ATs = Collections.synchronizedMap(new HashMap<String, List<String>>());
	}

	public static String orderGdlRules(GdlNode root) {
		return orderGdlRules(root, GdlParser.constructDependencyGraph(root));
	}

	/**
	 * Use a priority list to order by stratum.
	 * 
	 * @param root
	 * @param graph
	 * @return
	 */
	public static String orderGdlRules(GdlNode root, DependencyGraph graph) {
		Comparator<GdlNode> gdlHeadComparator = new Comparator<GdlNode>() {
			@Override
			public int compare(GdlNode fromHead, GdlNode toHead) {
				if (fromHead instanceof util.grammar.GdlRule && toHead instanceof util.grammar.GdlRule) {
					int stratDiff = ((util.grammar.GdlRule) fromHead).getStratum()
							- ((util.grammar.GdlRule) toHead).getStratum();
					if (stratDiff != 0) {
						return stratDiff;
					} else {
						return fromHead.getChildren().get(0).toString().compareTo(toHead.getChildren().get(0).toString());
					}
				} else if (fromHead instanceof util.grammar.GdlRule) {
					return 1;
				} else if (toHead instanceof util.grammar.GdlRule) {
					return -1;
				}
				return fromHead.toString().compareTo(toHead.toString());
			}
		};

		PriorityQueue<GdlNode> unordered = new PriorityQueue<GdlNode>(100, gdlHeadComparator);
		graph.computeStratum();
		for (GdlNode clause : root.getChildren()) {
			if (clause instanceof util.grammar.GdlRule) {
				if (clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_NEXT)) {
					((util.grammar.GdlRule) clause).setStratum(graph
							.getStratum(MCK_TRUE_PREFIX + formatMckNode(clause.getChildren().get(0).getChildren().get(0))));
				} else {
					((util.grammar.GdlRule) clause).setStratum(graph.getStratum(clause.getChildren().get(0).getAtom()));
				}
			}
			unordered.add(clause);
		}

		StringBuilder ordered = new StringBuilder();
		while (!unordered.isEmpty()) {
			GdlNode node = unordered.remove();
			ordered.append(node.toString());
		}
		return ordered.toString();
	}

	/**
	 * @param node
	 * @return
	 */
	public static String formatMckNode(GdlNode node) {
		StringBuilder nodeString = new StringBuilder();
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			nodeString.append(MCK_DOES_PREFIX + node.getChildren().get(0));
			nodeString.append(" == " + MCK_MOVE_PREFIX + formatMckNode(node.getChildren().get(1)));
		} else {
			if (node.getAtom().equals(GdlNode.GDL_NOT)) {
				nodeString.append("neg ");
			} else if (node.getAtom().equals(GdlNode.GDL_TRUE) || node.getAtom().equals(GdlNode.GDL_NEXT)) {

			} else if (node.getAtom().contains("+")) {
				nodeString.append(node.getAtom().replace("+", "plus") + "_");
			} else {
				nodeString.append(node.getAtom() + "_");
			}
			for (GdlNode child : node.getChildren()) {
				nodeString.append(formatMckNode(child) + "_");
			}
			nodeString.deleteCharAt(nodeString.length() - 1);
		}
		return nodeString.toString();
	}

	/**
	 * @param ATf
	 * @param graph
	 * @param headNode
	 * @param bodyList
	 * @return
	 */
	public String formatClause(DependencyGraph graph, GdlNode headNode, List<GdlNode> bodyList) {
		// Recognize sees
		boolean sees = false;
		if (bodyList.isEmpty() || headNode.toString().length() == 0) {
			return "";
		} else if (headNode.getAtom().equals(GdlNode.GDL_SEES)) {
			sees = true;
		}

		/*int headStratum;
		if (headNode.getAtom().equals(GdlNode.GDL_NEXT)) {
			headStratum = graph.getStratum(formatMckNode(headNode.getChildren().get(0)));
		} else {
			headStratum = graph.getStratum(formatMckNode(headNode));
		}*/
		StringBuilder disjunctBody = new StringBuilder();
		boolean disjunctBodyHasOtherThanFalse = false;
		for (GdlNode clause : bodyList) {
			boolean conjunctBodyHasFalse = false;
			boolean conjunctBodyHasOtherThanTrue = false;
			StringBuilder conjuntBody = new StringBuilder();
			conjuntBody.append("(");
			for (int i = 1; i < clause.getChildren().size(); i++) {
				String mckFormatted;
				if (clause.getChildren().get(i).getAtom().equals(GdlNode.GDL_NOT)) {
					mckFormatted = formatMckNode(clause.getChildren().get(i).getChildren().get(0));
				} else {
					mckFormatted = formatMckNode(clause.getChildren().get(i));
				}

				if (DEBUG) {
					conjuntBody.append(mckFormatted + MCK_AND);
				}
				if (ATt.contains(mckFormatted)) {
					if (DEBUG) {
						conjuntBody.append(MCK_TRUE + MCK_AND);
					}
				} else if (ATc.contains(mckFormatted)) {
					conjuntBody.append(MCK_FALSE + MCK_AND);
					conjunctBodyHasFalse = true;
					conjunctBodyHasOtherThanTrue = true;
				} else if (!ATh.contains(mckFormatted) && !clause.getChildren().get(i).getAtom().equals(GdlNode.GDL_DOES)) {
					conjuntBody.append(MCK_FALSE + MCK_AND);
					conjunctBodyHasFalse = true;
					conjunctBodyHasOtherThanTrue = true;
					if (!ATc.contains(mckFormatted)) {
						ATc.add(mckFormatted);
					}
				} else if (sees && ATf.contains(mckFormatted)) {
					conjuntBody.append(mckFormatted + MCK_OLD_SUFFIX + MCK_AND);
					conjunctBodyHasOtherThanTrue = true;
				} else if (graph.hasTerm(MCK_TRUE_PREFIX + formatMckNode(headNode))
						&& graph.hasTerm(MCK_TRUE_PREFIX + mckFormatted + MCK_OLD_SUFFIX)) {
					conjuntBody.append(mckFormatted + MCK_OLD_SUFFIX + MCK_AND);
					conjunctBodyHasOtherThanTrue = true;
				} else {
					conjuntBody.append(mckFormatted + MCK_AND);
					conjunctBodyHasOtherThanTrue = true;
				}
			}
			if (!conjunctBodyHasOtherThanTrue) {
				if (!ATt.contains(formatMckNode(headNode))) {
					ATt.add(formatMckNode(headNode));
				}
				// If there is a conjunction that is always true we don't need
				// to compute other clauses
				return "";
			}
			if (conjuntBody.length() >= MCK_AND.length()) {
				// Prune last AND
				conjuntBody.delete(conjuntBody.length() - MCK_AND.length(), conjuntBody.length());
				conjuntBody.append(")");
				// If conjunctive body has a false then it's a contradiction and
				// can be pruned
				if (DEBUG) {
					disjunctBody.append(conjuntBody.toString() + MCK_OR);
					disjunctBodyHasOtherThanFalse = true;
				} else if (!conjunctBodyHasFalse) {
					disjunctBody.append(conjuntBody.toString() + MCK_OR);
					disjunctBodyHasOtherThanFalse = true;
				}
			}
		}
		if (disjunctBody.length() >= MCK_OR.length()) {
			// Prune last OR
			disjunctBody.delete(disjunctBody.length() - MCK_OR.length(), disjunctBody.length());
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
			
		} else if (ONE_LINE_TRANSITIONS) {
			mckNode.append(System.lineSeparator() + formatMckNode(headNode) + " := " + disjunctBody.toString() + ";");
		} else {
			mckNode.append(System.lineSeparator() + "if " + disjunctBody.toString());
			mckNode.append(System.lineSeparator() + " -> " + formatMckNode(headNode) + " := " + MCK_TRUE);
			mckNode.append(System.lineSeparator() + " [] otherwise -> " + formatMckNode(headNode) + " := " + MCK_FALSE);
			mckNode.append(System.lineSeparator() + "fi;");
		}
		return mckNode.toString();
	}

	/**
	 * @param root
	 * @return
	 */
	public String toMck() {

		// Pre-processing
		DependencyGraph graph = GdlParser.constructDependencyGraph(root);
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
					String sees = formatMckNode(node.getChildren().get(1));
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
					String move = formatMckNode(node.getChildren().get(1));
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

		// Initialize string builders for different parts of the output
		StringBuilder agents = new StringBuilder();
		StringBuilder state_trans = new StringBuilder();
		StringBuilder protocols = new StringBuilder();

		// Agent and Protocol Declarations
		for (String role : ATd.keySet()) {
			protocols.append(System.lineSeparator() + "protocol \"" + role + "\" (");
			agents.append(System.lineSeparator() + "agent " + MCK_ROLE_PREFIX + role + " \"" + role + "\" (");
			for (String move : ATd.get(role)) {
				protocols.append("legal_" + role + "_" + move + " : Bool, ");
				agents.append("legal_" + role + "_" + move + ", ");
			}
			for (String sees : ATs.get(role)) {
				protocols.append("sees_" + role + "_" + sees + " : observable Bool, ");
				agents.append("sees_" + role + "_" + sees + ", ");
			}
			protocols.append(MCK_DOES_PREFIX + role + " : observable " + MCK_ACTION_PREFIX + role + ", ");
			protocols.append("terminal : observable Bool");
			protocols.append(")");
			agents.append(MCK_DOES_PREFIX + role + ", ");
			agents.append("terminal");
			agents.append(")");
			protocols.append(System.lineSeparator() + "begin");
			protocols.append(System.lineSeparator() + "  if terminal -> <<STOP>>");
			protocols.append(System.lineSeparator() + "  [] otherwise ->");
			protocols.append(System.lineSeparator() + "    if  ");
			for (String move : ATd.get(role)) {
				protocols.append("legal_" + role + "_" + move + " -> <<" + MCK_MOVE_PREFIX + move + ">>");
				protocols.append(System.lineSeparator() + "    []  ");
			}
			protocols.delete(protocols.length() - 9, protocols.length());
			protocols.append(System.lineSeparator() + "    fi");
			protocols.append(System.lineSeparator() + "  fi");
			protocols.append(System.lineSeparator() + "end");
		}
		agents.append(System.lineSeparator());
		protocols.append(System.lineSeparator());

		// State Transitions
		state_trans.append(System.lineSeparator() + "transitions");
		state_trans.append(System.lineSeparator() + "begin");
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Update the did_Agent to current move
		for (String role : ATd.keySet()) {
			state_trans.append(System.lineSeparator() + "if ");
			for (String move : ATd.get(role)) {
				state_trans.append(MCK_ROLE_PREFIX + role + "." + MCK_MOVE_PREFIX + move + " -> ");
				state_trans.append(
						MCK_DOES_PREFIX + role + " := " + MCK_MOVE_PREFIX + move + System.lineSeparator() + "[] ");
			}
			state_trans.append(MCK_ROLE_PREFIX + role + "." + MCK_STOP + " -> ");
			state_trans.append(MCK_DOES_PREFIX + role + " := " + MCK_STOP);
			//state_trans.delete(state_trans.length() - 4, state_trans.length());
			state_trans.append(System.lineSeparator() + "fi;");
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
						state_trans.append(formatClause(graph, repeatHead, repeatHeadList));
					}
					repeatHead = clause.getChildren().get(0);
					repeatHeadList = new ArrayList<GdlNode>();
					repeatHeadList.add(clause);
				}
			}
		}
		// Fix to skipping last clause in game
		if (repeatHead != null) {
			state_trans.append(formatClause(graph, repeatHead, repeatHeadList));
		}
		state_trans.deleteCharAt(state_trans.length() - 1); // Remove last ';'
		state_trans.append(System.lineSeparator() + "end");
		state_trans.append(System.lineSeparator());

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
		mck.append(System.lineSeparator() + agents.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- State Transitions");
		mck.append(System.lineSeparator() + state_trans.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Specifications");
		mck.append(System.lineSeparator() + generateSpecification());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Protocol Definitions");
		mck.append(System.lineSeparator() + protocols.toString());
		if (DEBUG) {
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
			spec.append("(neg terminal => neg (" + MCK_DOES_PREFIX + role + " == " + MCK_STOP + "))");
			spec.append(MCK_AND);
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs_ctl = AG(");
		for (String role : ATd.keySet()) {
			for (String move : ATd.get(role)) {
				spec.append("(legal_" + role + "_" + move + " => Knows " + MCK_ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				spec.append(MCK_AND);
			}
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs_ctl = AG(");
		for (String role : ATd.keySet()) {
			spec.append("(neg terminal => neg (" + MCK_DOES_PREFIX + role + " == " + MCK_STOP + "))");
			spec.append(MCK_AND);
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "--spec_obs_ctl = AF terminal");
		spec.append(System.lineSeparator());
		spec.append(System.lineSeparator());

		return spec.toString();
	}

	private String generateInitialConditions() {
		StringBuilder init_cond = new StringBuilder();

		// Add all initial true legal clauses to ATi
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() != GdlType.CLAUSE && clause.getAtom().equals(GdlNode.GDL_LEGAL)) {
				ATi.add(formatMckNode(clause));
			} else {
				boolean headTrue = true;
				for (int i = 1; i < clause.getChildren().size(); i++) {
					if (clause.getChildren().get(i).getAtom().equals(GdlNode.GDL_NOT)) {
						if (ATi.contains(formatMckNode(clause.getChildren().get(i).getChildren().get(0)))) {
							headTrue = false;
						}
					} else {
						if (!ATi.contains(formatMckNode(clause.getChildren().get(i)))) {
							headTrue = false;
						}
					}
				}
				if (headTrue) {
					ATi.add(formatMckNode(clause.getChildren().get(0)));
				}
			}
		}

		// Initial Conditions
		init_cond.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {

			init_cond.append(System.lineSeparator() + node + " == ");
			if (ATi.contains(node)) {
				init_cond.append(MCK_TRUE);
			} else {
				init_cond.append(MCK_FALSE);
			}
			init_cond.append(MCK_AND);
		}
		for (String role : ATd.keySet()) {
			init_cond.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " == " + MCK_INIT);
			init_cond.append(MCK_AND);
		}
		for (String trueVar : ATf) {
			init_cond.append(System.lineSeparator() + trueVar + " == ");
			if (ATi.contains(trueVar)) {
				init_cond.append(MCK_TRUE);
			} else {
				init_cond.append(MCK_FALSE);
			}
			init_cond.append(MCK_AND);
		}
		// Remove last conjunction
		init_cond.delete(init_cond.length() - 4, init_cond.length());
		init_cond.append(System.lineSeparator());
		init_cond.append(System.lineSeparator());

		return init_cond.toString();
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
				env_vars.append(MCK_MOVE_PREFIX + move + ", ");
			}
			env_vars.append(MCK_INIT + ", " + MCK_STOP + "}");
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- AT:");
		for (String node : AT) {
			env_vars.append(System.lineSeparator() + node + " : Bool");
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator() + "-- ATf:");
		for (String node : ATf) {
			env_vars.append(System.lineSeparator() + node + " : Bool");
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
				env_vars.append(System.lineSeparator() + "sees_" + role + "_" + move + " : Bool");
			}
		}
		env_vars.append(System.lineSeparator());
		env_vars.append(System.lineSeparator());

		return env_vars.toString();
	}
}