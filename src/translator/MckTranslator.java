package translator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.PriorityQueue;

import util.GdlParser;
import util.grammar.GdlNode;
import util.grammar.GdlNode.GdlType;
import util.graph.DependencyGraph;

/**
 * Translates GDL-II in infix notation to MCK
 */
/**
 * @author vedantds
 *
 */
public class MckTranslator {
	public static String MCK_INIT = "INIT";
	public static String MCK_STOP = "STOP";
	public static String MCK_ROLE_PREFIX = "R_";
	public static String MCK_MOVE_PREFIX = "M_";
	public static String MCK_DOES_PREFIX = "did_";
	public static String MCK_ACTION_PREFIX = "Act_";
	public static String MCK_AND = " /\\ ";
	public static String MCK_OR = " \\/ ";
	// Variables
	ArrayList<String> AT;
	// Variables found in true and/or next
	ArrayList<String> ATf;
	// List of variables which are true in the initial state
	ArrayList<String> ATi;
	// List of variables which are always true(totality)
	ArrayList<String> ATt;
	// List of variables which are always false(contradiction)
	ArrayList<String> ATc;
	// [Role -> Move] move map from legal
	HashMap<String, List<String>> ATd;
	// [Role -> Sees] observation map from sees
	HashMap<String, List<String>> ATs;

	GdlNode root;

	public MckTranslator(GdlNode root) {
		this.root = root;
		AT = new ArrayList<String>();
		ATf = new ArrayList<String>();
		ATi = new ArrayList<String>();
		ATt = new ArrayList<String>();
		ATc = new ArrayList<String>();
		ATd = new HashMap<String, List<String>>();
		ATs = new HashMap<String, List<String>>();
	}

	public static String orderGdlRules(GdlNode root) {
		Comparator<GdlNode> gdlHeadComparator = new Comparator<GdlNode>() {
			@Override
			public int compare(GdlNode fromHead, GdlNode toHead) {
				if (fromHead.getType() == GdlType.CLAUSE) {
					fromHead = fromHead.getChildren().get(0);
				}
				if (toHead.getType() == GdlType.CLAUSE) {
					toHead = toHead.getChildren().get(0);
				}
				if (fromHead.toString().hashCode() < toHead.toString().hashCode()) {
					return -1;
				} else if (fromHead.toString().hashCode() > toHead.toString().hashCode()) {
					return 1;
				} else {
					return 0;
				}
			}
		};
		PriorityQueue<GdlNode> unordered = new PriorityQueue<GdlNode>(gdlHeadComparator);
		PriorityQueue<GdlNode> unorderedAlt = new PriorityQueue<GdlNode>(gdlHeadComparator);
		unordered.addAll(root.getChildren());

		DependencyGraph graph = GdlParser.constructDependencyGraph(root);
		graph.computeStratum();
		Map<String, Integer> stratumMap = graph.getStratumMap();
		StringBuilder ordered = new StringBuilder();
		int stratum = -2;

		while (!unordered.isEmpty()) {
			while (!unordered.isEmpty()) {
				GdlNode node = unordered.remove();
				String nodeID = node.getChildren().get(0).getAtom();
				if (nodeID.equals(GdlNode.GDL_TRUE) || nodeID.equals(GdlNode.GDL_NEXT)) {
					nodeID = "true_" + GdlParser.formatGdlNode(node.getChildren().get(0).getChildren().get(0));
				}
				if (stratumMap.get(nodeID) == null || stratumMap.get(nodeID) == stratum) {
					ordered.append(node.toString());
				} else {
					unorderedAlt.add(node);
				}
			}
			stratum++;
			unordered = unorderedAlt;
			unorderedAlt = new PriorityQueue<GdlNode>(gdlHeadComparator);
		}

		return ordered.toString();
	}

	/**
	 * @param node
	 * @return
	 */
	public static String formatMckNode(GdlNode node) {
		StringBuilder sb = new StringBuilder();
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			sb.append(MCK_DOES_PREFIX + node.getChildren().get(0));
			sb.append(" == " + MCK_MOVE_PREFIX + formatMckNode(node.getChildren().get(1)));
		} else {
			if (node.getAtom().equals(GdlNode.GDL_NOT)) {
				sb.append("neg ");
			} else if (node.getAtom().equals(GdlNode.GDL_TRUE) || node.getAtom().equals(GdlNode.GDL_NEXT)) {

			} else if (node.getAtom().contains("+")) {
				sb.append(node.getAtom().replace("+", "plus") + "_");
			} else {
				sb.append(node.getAtom() + "_");
			}
			for (GdlNode child : node.getChildren()) {
				sb.append(formatMckNode(child) + "_");
			}
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	/**
	 * @param ATf
	 * @param graph
	 * @param headNode
	 * @param bodyList
	 * @return
	 */
	public String formatClause(DependencyGraph graph, GdlNode headNode, List<GdlNode> bodyList) {
		boolean sees = false;
		if (bodyList.isEmpty() || headNode.toString().equals("")) {
			return "";
		} else if (headNode.getAtom().equals(GdlNode.GDL_SEES)) {
			sees = true;
		}

		StringBuilder body = new StringBuilder();
		for (GdlNode clause : bodyList) {
			StringBuilder mckSubNode = new StringBuilder();
			mckSubNode.append("(");
			for (int i = 1; i < clause.getChildren().size(); i++) {
				String mckFormatted = formatMckNode(clause.getChildren().get(i));
				// TODO: change if statement to use added _old vars from graph
				// if (graph.getDependencyMap().keySet().contains(mckFormatted +
				// "_old")) {
				if (ATt.contains(mckFormatted)) {
					mckSubNode.append("True" + MCK_AND);
				} else if (ATc.contains(mckFormatted)) {
					mckSubNode.append("False" + MCK_AND);
				} else if (sees && ATf.contains(mckFormatted)) {
					mckSubNode.append(mckFormatted + "_old" + MCK_AND);
				} else {
					mckSubNode.append(mckFormatted + MCK_AND);
				}
			}
			if (mckSubNode.length() >= 4) {
				mckSubNode.delete(mckSubNode.length() - 4, mckSubNode.length());
				mckSubNode.append(")");
				body.append(mckSubNode.toString() + MCK_OR);
			}
		}
		if (body.length() >= 4) {
			body.delete(body.length() - 4, body.length());
		}

		StringBuilder mckNode = new StringBuilder();
		if (body.length() == 0) {
			ATt.add(formatMckNode(headNode));
			mckNode.append(System.lineSeparator() + formatMckNode(headNode) + " := True");
		} else {
			mckNode.append("if ");
			mckNode.append(body.toString());
			mckNode.append(System.lineSeparator() + " -> " + formatMckNode(headNode) + " := True");
			mckNode.append(System.lineSeparator() + " [] otherwise -> " + formatMckNode(headNode) + " := False");
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
			if (old.length() >= 5 && old.substring(old.length() - 4).equals("_old")) {
				ATf.add(old.substring(5));
			}
		}

		for (GdlNode node : root) {
			if (node.getType() == GdlType.FORMULA) {
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
						if (!ATi.contains(formatMckNode(node.getChildren().get(0)))) {
							ATi.add(formatMckNode(node.getChildren().get(0)));
						}
						if (!ATt.contains(formatMckNode(node.getChildren().get(0)))) {
							ATt.add(formatMckNode(node.getChildren().get(0)));
						}
					} else {
						if (!ATc.contains(formatMckNode(node.getChildren().get(0)))) {
							ATc.add(formatMckNode(node.getChildren().get(0)));
						}
					}
				case GdlNode.GDL_ROLE:
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
					if (!ATi.contains(formatMckNode(node.getChildren().get(0)))) {
						ATi.add(formatMckNode(node.getChildren().get(0)));
					}
					if (!ATt.contains(formatMckNode(node.getChildren().get(0)))) {
						ATt.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				case GdlNode.GDL_SEES:
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
					if (!ATf.contains(formatMckNode(node.getChildren().get(0)))) {
						ATf.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				case GdlNode.GDL_INIT:
					if (!ATi.contains(formatMckNode(node.getChildren().get(0)))) {
						ATi.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				default:
					if (node.getParent().getType().equals(GdlType.ROOT)) {
						if (!ATt.contains(formatMckNode(node))) {
							ATt.add(formatMckNode(node));
						}
					}
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

		// Initialize string builders for different parts of the output
		StringBuilder env_vars = new StringBuilder();
		StringBuilder init_cond = new StringBuilder();
		StringBuilder agents = new StringBuilder();
		StringBuilder state_trans = new StringBuilder();
		StringBuilder spec = new StringBuilder();
		StringBuilder protocols = new StringBuilder();

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
			// mck.append(System.lineSeparator() + node + "_old : Bool");
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
			protocols.append(MCK_DOES_PREFIX + role + " : observable " + MCK_ACTION_PREFIX + role);
			protocols.append(")");
			agents.append(MCK_DOES_PREFIX + role);
			agents.append(")");
			protocols.append(System.lineSeparator() + "begin");
			protocols.append(System.lineSeparator() + "  if  ");
			for (String move : ATd.get(role)) {
				protocols.append("legal_" + role + "_" + move + " -> <<" + MCK_MOVE_PREFIX + move + ">>");
				protocols.append(System.lineSeparator() + "  []  ");
			}
			protocols.delete(protocols.length() - 7, protocols.length());
			protocols.append(System.lineSeparator() + "  fi");
			protocols.append(System.lineSeparator() + "end");
		}
		agents.append(System.lineSeparator());
		protocols.append(System.lineSeparator());

		// Initial Conditions
		init_cond.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {
			if (node.length() >= 5 && node.substring(0, 5).equals(GdlNode.GDL_LEGAL)) {
				init_cond.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					init_cond.append("True");
				} else {
					init_cond.append("False");
				}
				init_cond.append(MCK_AND);
			} else if (node.length() >= 8 && node.substring(0, 8).equals(GdlNode.GDL_DISTINCT)) {
				init_cond.append(System.lineSeparator() + node + " == ");
				if (node.substring(9, 9 + (node.length() - 9) / 2)
						.equals(node.substring(9 + (node.length() - 9) / 2 + 1))) {
					init_cond.append("False");
				} else {
					init_cond.append("True");
				}
				init_cond.append(MCK_AND);
			} else {
				init_cond.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					init_cond.append("True");
				} else {
					init_cond.append("False");
				}
				init_cond.append(MCK_AND);
			}
		}
		for (String role : ATd.keySet()) {
			init_cond.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " == " + MCK_INIT);
			init_cond.append(" /\\ ");
		}
		for (String trueVar : ATf) {
			init_cond.append(System.lineSeparator() + trueVar + " == ");
			if (ATi.contains(trueVar)) {
				init_cond.append("True");
			} else {
				init_cond.append("False");
			}
			init_cond.append(MCK_AND);
		}
		// Remove last conjunction
		init_cond.delete(init_cond.length() - 4, init_cond.length());
		init_cond.append(System.lineSeparator());
		init_cond.append(System.lineSeparator());

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
			state_trans.delete(state_trans.length() - 4, state_trans.length());
			state_trans.append(System.lineSeparator() + "fi;");
		}
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		// Update _old variables
		for (String trueNode : ATf) {
			if (trueNode.length() >= 4 && trueNode.substring(trueNode.length() - 4).equals("_old")) {
				state_trans.append(System.lineSeparator() + trueNode + " := "
						+ trueNode.substring(0, trueNode.length() - 4) + ";");
			}
		}
		state_trans.append(System.lineSeparator());
		state_trans.append(System.lineSeparator());

		ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
		GdlNode repeatHead = null;
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE && !clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_BASE)
					&& !clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_INPUT)) {
				if (repeatHead != null && clause.getChildren().get(0).toString().equals(repeatHead.toString())) {
					repeatHeadList.add(clause);
				} else {
					if (repeatHead != null) {
						state_trans.append(System.lineSeparator() + formatClause(graph, repeatHead, repeatHeadList));
					}
					repeatHead = clause.getChildren().get(0);
					repeatHeadList = new ArrayList<GdlNode>();
					repeatHeadList.add(clause);
				}
			}
		}
		state_trans.deleteCharAt(state_trans.length() - 1);
		state_trans.append(System.lineSeparator() + "end");
		state_trans.append(System.lineSeparator());

		// Specification
		spec.append(System.lineSeparator() + "spec_spr = AG(");
		for (String role : ATd.keySet()) {
			for (String move : ATd.get(role)) {
				spec.append("(legal_" + role + "_" + move + " => Knows " + MCK_ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				spec.append(MCK_AND);
			}
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator() + "spec_spr = AG(");
		for (String role : ATd.keySet()) {
			spec.append("(neg terminal => neg (" + MCK_DOES_PREFIX + role + " == " + MCK_STOP + "))");
			spec.append(MCK_AND);
		}
		spec.delete(spec.length() - 4, spec.length());
		spec.append(")");
		spec.append(System.lineSeparator());
		spec.append(System.lineSeparator());

		// Join all of the sections together
		StringBuilder mck = new StringBuilder();
		mck.append("-- MCK file generated using MckTranslator from a GGP game description");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Environment Variables");
		mck.append(System.lineSeparator() + env_vars.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Initial Conditions");
		mck.append(System.lineSeparator() + init_cond.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Agent Definitions");
		mck.append(System.lineSeparator() + agents.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- State Transitions");
		mck.append(System.lineSeparator() + state_trans.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Specifications");
		mck.append(System.lineSeparator() + spec.toString());
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- Protocol Definitions");
		mck.append(System.lineSeparator() + protocols.toString());
		mck.append(System.lineSeparator());

		return mck.toString();
	}
}