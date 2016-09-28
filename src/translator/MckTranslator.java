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
public class MckTranslator {
	public static String MCK_INIT = "INIT";
	public static String MCK_STOP = "STOP";
	public static String MCK_ROLE_PREFIX = "R_";
	public static String MCK_MOVE_PREFIX = "M_";
	public static String MCK_DOES_PREFIX = "did_";
	public static String MCK_ACTION_PREFIX = "Act_";

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

	public static String formatMckNode(GdlNode node) {
		StringBuilder sb = new StringBuilder();
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			sb.append(MCK_DOES_PREFIX + node.getChildren().get(0));
			sb.append(" == " + MCK_MOVE_PREFIX + formatMckNode(node.getChildren().get(1)));
		} else {
			if (node.getAtom().equals(GdlNode.GDL_NOT)) {
				sb.append("neg ");
			} else if (node.getAtom().equals(GdlNode.GDL_TRUE) || node.getAtom().equals(GdlNode.GDL_NEXT)) {

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

	public static String formatClause(ArrayList<String> ATf, DependencyGraph graph, GdlNode headNode,
			List<GdlNode> bodyList) {
		boolean sees = false;
		if (bodyList.isEmpty() || headNode.toString().equals("")) {
			return "";
		} else if (headNode.getAtom().equals(GdlNode.GDL_SEES)) {
			sees = true;
		}

		StringBuilder mckNode = new StringBuilder();
		mckNode.append("if ");
		for (GdlNode clause : bodyList) {
			StringBuilder mckSubNode = new StringBuilder();
			mckSubNode.append("(");
			for (int i = 1; i < clause.getChildren().size(); i++) {
				String mckFormatted = formatMckNode(clause.getChildren().get(i));
				// TODO: change if statement to use added _old vars from graph
				// if (graph.getDependencyMap().keySet().contains(mckFormatted +
				// "_old")) {
				if (sees && ATf.contains(mckFormatted)) {
					mckSubNode.append(mckFormatted + "_old /\\ ");
				} else {
					mckSubNode.append(mckFormatted + " /\\ ");
				}
			}
			mckSubNode.delete(mckSubNode.length() - 4, mckSubNode.length());
			mckSubNode.append(")");
			mckNode.append(mckSubNode.toString() + " \\/ ");
		}
		mckNode.delete(mckNode.length() - 4, mckNode.length());
		mckNode.append(System.lineSeparator() + " -> " + formatMckNode(headNode) + " := True");
		mckNode.append(System.lineSeparator() + " [] otherwise -> " + formatMckNode(headNode) + " := False");
		mckNode.append(System.lineSeparator() + "fi;");
		return mckNode.toString();
	}

	public static String toMck(GdlNode root) {
		ArrayList<String> AT = new ArrayList<String>();
		ArrayList<String> ATf = new ArrayList<String>();
		ArrayList<String> ATi = new ArrayList<String>();
		HashMap<String, List<String>> ATd = new HashMap<String, List<String>>();
		HashMap<String, List<String>> ATs = new HashMap<String, List<String>>();
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
				case GdlNode.GDL_ROLE:
				case GdlNode.GDL_DOES:
					// Skip these predicates due to redundancy
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
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
				}
			}
		}

		// Add all true distinct to ATi
		for (GdlNode distinct : root) {
			if (distinct.getAtom().equals(GdlNode.GDL_DISTINCT)) {
				if (distinct.getChildren().get(0).toString().equals(distinct.getChildren().get(1).toString())) {
					ATi.add(formatMckNode(distinct));
				}
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

		StringBuilder mck = new StringBuilder();
		mck.append("-- MCK file generated using MckTranslator from a GGP game description");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Environment Variables
		mck.append(System.lineSeparator() + "-- Environment Variables");
		mck.append(System.lineSeparator());
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "type " + MCK_ACTION_PREFIX + role + " = {");
			for (String move : ATd.get(role)) {
				mck.append(MCK_MOVE_PREFIX + move + ", ");
			}
			mck.append(MCK_INIT + ", " + MCK_STOP + "}");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- AT:");
		for (String node : AT) {
			mck.append(System.lineSeparator() + node + " : Bool");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATf:");
		for (String node : ATf) {
			mck.append(System.lineSeparator() + node + " : Bool");
			// mck.append(System.lineSeparator() + node + "_old : Bool");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATd:");
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " : " + MCK_ACTION_PREFIX + role);
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATs:");
		for (String role : ATs.keySet()) {
			for (String move : ATs.get(role)) {
				mck.append(System.lineSeparator() + "sees_" + role + "_" + move + " : Bool");
			}
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Initial Conditions
		mck.append(System.lineSeparator() + "-- Initial Conditions");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {
			if (node.length() >= 5 && node.substring(0, 5).equals(GdlNode.GDL_LEGAL)) {
				mck.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					mck.append("True");
				} else {
					mck.append("False");
				}
				mck.append(" /\\ ");
			} else if (node.length() >= 8 && node.substring(0, 8).equals(GdlNode.GDL_DISTINCT)) {
				mck.append(System.lineSeparator() + node + " == ");
				if (node.substring(9, 9 + (node.length() - 9) / 2)
						.equals(node.substring(9 + (node.length() - 9) / 2 + 1))) {
					mck.append("False");
				} else {
					mck.append("True");
				}
				mck.append(" /\\ ");
			} else {
				mck.append(System.lineSeparator() + node + " == ");
				if (ATi.contains(node)) {
					mck.append("True");
				} else {
					mck.append("False");
				}
				mck.append(" /\\ ");
			}
		}
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + MCK_DOES_PREFIX + role + " == " + MCK_INIT);
			mck.append(" /\\ ");
		}
		for (String trueVar : ATf) {
			mck.append(System.lineSeparator() + trueVar + " == ");
			if (ATi.contains(trueVar)) {
				mck.append("True");
			} else {
				mck.append("False");
			}
			mck.append(" /\\ ");
		}
		mck.delete(mck.length() - 4, mck.length()); // Remove last conjunction
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Agent Declaration
		mck.append(System.lineSeparator() + "-- Agent Declaration");
		mck.append(System.lineSeparator());

		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "agent " + MCK_ROLE_PREFIX + role + " \"" + role + "\" (");
			for (String move : ATd.get(role)) {
				mck.append("legal_" + role + "_" + move + ", ");
			}
			for (String move : ATs.get(role)) {
				mck.append("sees_" + role + "_" + move + ", ");
			}
			mck.append(MCK_DOES_PREFIX + role);
			mck.append(")");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// State Transitions
		mck.append(System.lineSeparator() + "-- State Transitions");
		mck.append(System.lineSeparator() + "transitions");
		mck.append(System.lineSeparator() + "begin");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "if ");
			for (String move : ATd.get(role)) {
				mck.append(MCK_ROLE_PREFIX + role + "." + MCK_MOVE_PREFIX + move + " -> ");
				mck.append(MCK_DOES_PREFIX + role + " := " + MCK_MOVE_PREFIX + move + System.lineSeparator() + "[] ");
			}
			mck.delete(mck.length() - 4, mck.length());
			mck.append(System.lineSeparator() + "fi;");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		for (String trueNode : ATf) {
			if (trueNode.length() >= 4 && trueNode.substring(trueNode.length() - 4).equals("_old")) {
				mck.append(System.lineSeparator() + trueNode + " := " + trueNode.substring(0, trueNode.length() - 4)
						+ ";");
			}
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
		GdlNode repeatHead = null;
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE) {
				if (repeatHead != null && clause.getChildren().get(0).toString().equals(repeatHead.toString())) {
					repeatHeadList.add(clause);
				} else {
					if (repeatHead != null) {
						mck.append(System.lineSeparator() + formatClause(ATf, graph, repeatHead, repeatHeadList));
					}
					repeatHead = clause.getChildren().get(0);
					repeatHeadList = new ArrayList<GdlNode>();
					repeatHeadList.add(clause);
				}
			}
		}
		mck.deleteCharAt(mck.length() - 1);
		mck.append(System.lineSeparator() + "end");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Specification
		mck.append("-- Specification");
		mck.append(System.lineSeparator());
		mck.append("spec_spr = AG(");
		for (String role : ATd.keySet()) {
			for (String move : ATd.get(role)) {
				mck.append("(legal_" + role + "_" + move + " => Knows " + MCK_ROLE_PREFIX + role + " legal_" + role
						+ "_" + move + ")");
				mck.append(" /\\ ");
			}
		}
		mck.delete(mck.length() - 4, mck.length());
		mck.append(")");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Protocol Declaration
		mck.append(System.lineSeparator() + "-- Protocol Declaration");
		mck.append(System.lineSeparator());
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "protocol \"" + role + "\" (");
			for (String move : ATd.get(role)) {
				mck.append("legal_" + role + "_" + move + " : Bool, ");
			}
			for (String sees : ATs.get(role)) {
				mck.append("sees_" + role + "_" + sees + " : observable Bool, ");
			}
			mck.append(MCK_DOES_PREFIX + role + " : observable " + MCK_ACTION_PREFIX + role);
			mck.append(")");
			mck.append(System.lineSeparator() + "begin");
			mck.append(System.lineSeparator() + "  if  ");
			for (String move : ATd.get(role)) {
				mck.append("legal_" + role + "_" + move + " -> <<" + MCK_MOVE_PREFIX + move + ">>");
				mck.append(System.lineSeparator() + "  []  ");
			}
			mck.delete(mck.length() - 7, mck.length());
			mck.append(System.lineSeparator() + "  fi");
			mck.append(System.lineSeparator() + "end");
		}
		mck.append(System.lineSeparator());

		return mck.toString();
	}
}