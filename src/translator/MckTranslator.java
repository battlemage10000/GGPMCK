package translator;

import java.io.*;
import java.util.*;

import translator.grammar.GdlNode;
import translator.grammar.GdlNode.GdlType;
import translator.graph.DependencyGraph;
import translator.graph.DomainGraph;

/**
 * Translates GDL-II in infix notation to MCK
 */
public class MckTranslator {

	/**
	 * Constructs a dependency graph used to order rules for mck translation The
	 * structure is a variation on the dependency graph defined for stratisfied
	 * Datalog rules in the GDL Spec for GGP paper
	 * 
	 * @param root
	 * @return
	 */
	public static DependencyGraph constructDependencyGraph(GdlNode root) {
		DependencyGraph graph = new DependencyGraph();

		for (GdlNode node : root) {
			if (node.getType() == GdlType.CLAUSE) {
				GdlNode headNode = node.getChildren().get(0);
				if (!headNode.getAtom().equals(GdlNode.GDL_BASE) && !headNode.getAtom().equals(GdlNode.GDL_INPUT)) {
					for (int i = 1; i < node.getChildren().size(); i++) {
						GdlNode toNode = node.getChildren().get(i);
						if (toNode.getAtom().equals(GdlNode.GDL_NOT)) {
							toNode = toNode.getChildren().get(0);
						}
						if (toNode.getAtom().equals(GdlNode.GDL_TRUE)) {
							// toNode = toNode.getChildren().get(0);
						}
						graph.addEdge(headNode.getAtom(), toNode.getAtom());
					}
				}
			}
		}

		return graph;
	}

	/**
	 * Follows the domain graph definition in the ggp book
	 */
	public static DomainGraph constructDomainGraph(GdlNode root) {
		DomainGraph graph = new DomainGraph();
		HashMap<String, DomainGraph.Term> variableMap = new HashMap<String, DomainGraph.Term>();

		for (GdlNode node : root) {
			if ((node.getType() == GdlType.FUNCTION || node.getType() == GdlType.FORMULA)
					&& !node.getAtom().equals(GdlNode.GDL_NOT)) {
				if (node.getType() == GdlType.FUNCTION) {
					graph.addFunction(node.getAtom(), node.getChildren().size());
				} else if (node.getType() == GdlType.FORMULA) {
					graph.addFormula(node.getAtom(), node.getChildren().size());
				} else {
					continue;
				}

				for (int i = 0; i < node.getChildren().size(); i++) {
					GdlNode childNode = node.getChildren().get(i);
					if (childNode.getType() == GdlType.VARIABLE) {
						if (variableMap.containsKey(childNode.getAtom())) {
							DomainGraph.Term varLink = variableMap.get(childNode.getAtom());
							graph.addEdge(varLink.getTerm(), varLink.getArity(), node.getAtom(), i + 1);
						} else {
							variableMap.put(childNode.getAtom(), new DomainGraph.Term(node.getAtom(), i + 1));
						}
					} else {
						graph.addEdge(node.getAtom(), i + 1, childNode.getAtom(), childNode.getChildren().size(),
								childNode.getType());
					}
				}
			}
		}

		graph.addEdge("base", 1, "true", 1);
		graph.addEdge("input", 1, "does", 1);
		graph.addEdge("input", 2, "does", 2);
		return graph;
	}

	public static boolean isVariableInTree(GdlNode node) {
		if (node.getType() == GdlType.VARIABLE) {
			return true;
		}

		for (GdlNode child : node.getChildren()) {
			if (isVariableInTree(child)) {
				return true;
			}
		}
		return false;
	}

	public static GdlNode groundGdl(GdlNode root, DomainGraph domainGraph) {
		GdlNode groundedRoot = new ParseNode();

		for (GdlNode clause : root.getChildren()) {
			if (!isVariableInTree(clause)) { // No variables is already ground
				groundedRoot.getChildren().add(clause);
			} else {
				String groundedClauseString = groundClause(clause, domainGraph.getMap());

				GdlNode clauseTree = new ParseNode(); // Default root node if
														// parseString throws
														// error
				try {
					clauseTree = GdlParser.parseString(groundedClauseString);
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (!clauseTree.getChildren().isEmpty()) {
					groundedRoot.getChildren().addAll(clauseTree.getChildren());
				}
			}
		}
		return groundedRoot;
	}

	public static String groundClause(GdlNode clauseNode,
			Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap) {
		Map<String, List<String>> constantMap = new HashMap<String, List<String>>();
		for (GdlNode node : clauseNode) {
			if (node.getType() == GdlType.VARIABLE) {
				if (!constantMap.containsKey(node.getAtom())) {
					constantMap.put(node.getAtom(), new ArrayList<String>());
				}

				DomainGraph.Term varTerm = new DomainGraph.Term(node.getParent().getAtom(),
						node.getParent().getChildren().indexOf(node) + 1);

				if (domainMap.containsKey(varTerm)) {
					for (DomainGraph.Term term : domainMap.get(varTerm)) {
						if (!constantMap.get(node.getAtom()).contains(term.getTerm())) {
							constantMap.get(node.getAtom()).add(term.getTerm());
						}
					}
				}
			}
		}

		StringBuilder groundedClauses = new StringBuilder();

		Queue<String> subClauses = new LinkedList<String>();
		Queue<String> subClausesAlt = new LinkedList<String>();
		subClausesAlt.add(clauseNode.toString());
		for (String variable : constantMap.keySet()) {
			subClauses = subClausesAlt;
			subClausesAlt = new LinkedList<String>();

			List<String> domain = constantMap.get(variable);

			while (!subClauses.isEmpty()) {
				String subClause = subClauses.remove();
				for (String term : domain) {
					String nextTerm = subClause.replace(variable, term);
					if (nextTerm.contains("?")) {
						subClausesAlt.add(nextTerm);
					} else {
						groundedClauses.append(nextTerm);
					}
				}
			}
		}

		return groundedClauses.toString();
	}

	public static String MCK_INIT = "INIT";
	public static String MCK_STOP = "STOP";
	public static String MCK_ROLE_PREFIX = "R_";
	public static String MCK_MOVE_PREFIX = "M_";
	public static String MCK_ACTION_PREFIX = "Act_";
	// public static String MCK_TRUE = "True";
	// public static String MCK_FALSE = "False";

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

		DependencyGraph graph = constructDependencyGraph(root);
		graph.computeStratum();
		Map<String, Integer> stratumMap = graph.getStratumMap();
		StringBuilder ordered = new StringBuilder();
		int stratum = -2;

		while (!unordered.isEmpty()) {
			while (!unordered.isEmpty()) {
				GdlNode node = unordered.remove();
				if (stratumMap.get(node.getChildren().get(0).getAtom()) == null
						|| stratumMap.get(node.getChildren().get(0).getAtom()) == stratum) {
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

		// if (node.getAtom().equals(GdlNode.GDL_DISTINCT)) {
		// if
		// (node.getChildren().get(0).toString().equals(node.getChildren().get(1).toString()))
		// {
		// sb.append(MCK_FALSE);
		// } else {
		// sb.append(MCK_TRUE);
		// }
		// } else
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			sb.append("did_" + node.getChildren().get(0));
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

	public static String formatClause(GdlNode headNode, List<GdlNode> bodyList) {
		if (bodyList.isEmpty() || headNode.toString().equals("")) {
			return "";
		}

		StringBuilder mckNode = new StringBuilder();
		// boolean containsTrue = false;
		mckNode.append("if ");
		for (GdlNode clause : bodyList) {
			// boolean containsFalse = false;
			StringBuilder mckSubNode = new StringBuilder();
			mckSubNode.append("(");
			for (int i = 1; i < clause.getChildren().size(); i++) {
				String mckFormatted = formatMckNode(clause.getChildren().get(i));
				// if (mckFormatted.equals(MCK_FALSE)) {
				// containsFalse = true;
				// break;
				// } else if (!mckFormatted.equals(MCK_TRUE)) {
				mckSubNode.append(mckFormatted + " /\\ ");
				// }

			}

			// if (!containsFalse) {
			mckSubNode.delete(mckSubNode.length() - 4, mckSubNode.length());
			mckSubNode.append(")");
			mckNode.append(mckSubNode.toString() + " \\/ ");
			// }
		}
		// if(containsTrue){
		// return formatMckNode(headNode) + " := True;";
		// }

		mckNode.delete(mckNode.length() - 4, mckNode.length());

		mckNode.append(System.lineSeparator() + " -> " + formatMckNode(headNode) + " := True");
		mckNode.append(System.lineSeparator() + " [] otherwise -> " + formatMckNode(headNode) + " := False");
		mckNode.append(System.lineSeparator() + "fi;");

		return mckNode.toString();
	}

	public static String toMck(GdlNode root) {
		ArrayList<String> AT = new ArrayList<String>();
		ArrayList<String> ATf = new ArrayList<String>();
		HashMap<String, List<String>> ATd = new HashMap<String, List<String>>();
		ArrayList<String> ATi = new ArrayList<String>();
		ArrayList<String> ATs = new ArrayList<String>();

		for (GdlNode node : root) {
			if (node.getType() == GdlType.FORMULA) {
				switch (node.getAtom()) {
				case GdlNode.GDL_NOT:
				case GdlNode.GDL_BASE:
				case GdlNode.GDL_INPUT:
				case GdlNode.GDL_ROLE:
					// Skip these predicates due to redundancy
					break;
				case GdlNode.GDL_SEES:
					if (!ATs.contains(formatMckNode(node))) {
						ATs.add(formatMckNode(node));
					}
					if (!AT.contains(formatMckNode(node))) {
						AT.add(formatMckNode(node));
					}
					break;
				case GdlNode.GDL_TRUE:
				case GdlNode.GDL_NEXT:
					if (!ATf.contains(formatMckNode(node.getChildren().get(0)))) {
						ATf.add(formatMckNode(node.getChildren().get(0)));
					}
					break;
				case GdlNode.GDL_DOES:
					String role = formatMckNode(node.getChildren().get(0));
					String move = formatMckNode(node.getChildren().get(1));
					if (!ATd.containsKey(role)) {
						ATd.put(role, new ArrayList<String>());
					}
					if (!ATd.get(role).contains(move)) {
						ATd.get(role).add(move);
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
			// if (!node.equals(MCK_TRUE) && !node.equals(MCK_FALSE)) {
			mck.append(System.lineSeparator() + node + " : Bool");
			// }
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATf:");
		for (String node : ATf) {
			mck.append(System.lineSeparator() + node + " : Bool");
			mck.append(System.lineSeparator() + node + "_old : Bool");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATd:");
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "did_" + role + " : " + MCK_ACTION_PREFIX + role);
			mck.append(System.lineSeparator());
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "-- ATs:");
		for (String seen : ATs) {
			mck.append(System.lineSeparator() + seen + " : Bool");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// Initial Conditions
		mck.append(System.lineSeparator() + "-- Initial Conditions");
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator() + "init_cond = ");
		for (String node : AT) {
			mck.append(System.lineSeparator() + node + " == ");
			if (ATi.contains(node)) {
				mck.append("True");
			} else {
				mck.append("False");
			}
			mck.append(" /\\ ");
		}
		for (String role : ATd.keySet()) {
			mck.append(System.lineSeparator() + "did_" + role + " == " + MCK_INIT);
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
			for (String seen : ATs) {
				mck.append(seen + ", ");
			}
			mck.append("did_" + role);
			mck.append(")");
		}
		mck.append(System.lineSeparator());
		mck.append(System.lineSeparator());

		// State Transitions
		mck.append(System.lineSeparator() + "-- State Transitions");
		mck.append(System.lineSeparator() + "transitions");
		mck.append(System.lineSeparator() + "begin");
		mck.append(System.lineSeparator());

		ArrayList<GdlNode> repeatHeadList = new ArrayList<GdlNode>();
		GdlNode repeatHead = null;
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE) {
				if (repeatHead != null && clause.getChildren().get(0).toString().equals(repeatHead.toString())) {
					repeatHeadList.add(clause);
				} else {
					if (repeatHead != null) {
						mck.append(System.lineSeparator() + formatClause(repeatHead, repeatHeadList));
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
			for (String seen : ATs) {
				mck.append(seen + " : observable Bool, ");
			}
			mck.append("did_" + role + " : observable " + MCK_ACTION_PREFIX + role);
			mck.append(")");
			mck.append(System.lineSeparator() + "begin");
			// mck.append(System.lineSeparator() + " do neg terminal ->");
			mck.append(System.lineSeparator() + "    if  ");
			for (String move : ATd.get(role)) {
				mck.append("legal_" + role + "_" + move + " -> <<" + MCK_MOVE_PREFIX + move + ">>");
				mck.append(System.lineSeparator() + "    []  ");
			}
			mck.delete(mck.length() - 9, mck.length());
			mck.append(System.lineSeparator() + "    fi");
			// mck.append(System.lineSeparator() + " od");
			mck.append(System.lineSeparator() + "end");
		}
		mck.append(System.lineSeparator());

		return mck.toString();
	}

	public static List<String> findBoolVarsForMck(GdlNode root) {
		ArrayList<String> boolVars = new ArrayList<String>();

		Queue<GdlNode> childrenQueue = new LinkedList<GdlNode>();
		childrenQueue.addAll(root.getChildren());

		while (!childrenQueue.isEmpty()) {
			GdlNode node = childrenQueue.remove();

			switch (node.getAtom()) {
			case "<=":
				childrenQueue.addAll(node.getChildren());
				break;
			case GdlNode.GDL_LEGAL:
				String move = node.getChildren().get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add(move);
				boolVars.add(move + "_old");
				boolVars.add("legal_" + node.getChildren().get(0).getAtom() + "_" + move);
				boolVars.add("did_" + node.getChildren().get(0).getAtom() + "_" + move);
				break;
			case GdlNode.GDL_SEES:
				move = node.getChildren().get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add("sees_" + node.getChildren().get(0).getAtom() + "_" + move);
				break;
			}
		}

		return boolVars;
	}

	public static Map<String, List<String>> findMovesForMck(GdlNode root) {
		Map<String, List<String>> roleToMoveMap = new HashMap<String, List<String>>();

		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE
					&& (clause.getChildren().get(0).getAtom()).equals(GdlNode.GDL_LEGAL)) {
				GdlNode legal = clause.getChildren().get(0);
				String role = legal.getChildren().get(0).toString();
				String move = legal.getChildren().get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");

				if (!roleToMoveMap.containsKey(role)) {
					roleToMoveMap.put(role, new ArrayList<String>());
				}

				if (!roleToMoveMap.get(role).contains(move)) {
					roleToMoveMap.get(role).add(move);
				}
			}
		}
		return roleToMoveMap;
	}

	/**
	 * TODO: takes a parse tree and returns MCK equivalent TODO: rewrite to
	 * follow steps in mck paper
	 */
	public static String toMckOld(GdlNode root) {
		Map<String, List<String>> roleToMoveMap = findMovesForMck(root);
		List<String> boolVars = findBoolVarsForMck(root);

		StringBuilder sb = new StringBuilder();
		// Construct MCK version
		sb.append("-- MCK file generated using MckTranslator from a GGP game description");
		sb.append(System.lineSeparator());

		sb.append(System.lineSeparator() + "-- Environment Variables");
		for (String boolVar : boolVars) {
			sb.append(System.lineSeparator());
			sb.append(boolVar + ": Bool");
		}
		sb.append(System.lineSeparator());

		sb.append(System.lineSeparator() + "-- Environment Initial Conditions");
		sb.append(System.lineSeparator() + "init_cond = ");
		for (String var : boolVars) {
			sb.append(System.lineSeparator());
			sb.append(var + "==False /\\ ");
		}
		sb.delete(sb.length() - 4, sb.length());
		sb.append(System.lineSeparator());

		sb.append(System.lineSeparator() + "-- Agent bindings");
		for (String role : roleToMoveMap.keySet()) {
			sb.append(System.lineSeparator() + "agent Player_" + role + " \"" + role + "\" (");
			for (String var : boolVars) {
				if (var.contains("_" + role + "_")) {
					sb.append(System.lineSeparator() + var + ", ");
				}
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append(System.lineSeparator() + ")");
		}
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator() + "transitions");
		sb.append(System.lineSeparator() + "begin");

		for (String role : roleToMoveMap.keySet()) {
			for (String move : roleToMoveMap.get(role)) {
				sb.append(System.lineSeparator() + "if Player_" + role + ".Move_" + move + " -> did_" + role + "_"
						+ move + " := True");
				sb.append(System.lineSeparator() + "[] otherwise -> did_" + role + "_" + move + " := False");
				sb.append(System.lineSeparator() + "fi;");
			}
		}

		sb.append(System.lineSeparator() + "end");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());

		sb.append("-- Specification");
		sb.append(System.lineSeparator());
		sb.append("spec_spr = AG(");
		for (String role : roleToMoveMap.keySet()) {
			for (String legal : roleToMoveMap.get(role)) {
				sb.append("(" + legal + " => Knows Player_" + role + " " + legal + ")");
				sb.append(" /\\ ");
			}
		}
		sb.delete(sb.length() - 4, sb.length());
		sb.append(")");
		sb.append(System.lineSeparator());

		sb.append(System.lineSeparator() + "-- Protocol Declarations");
		for (String role : roleToMoveMap.keySet()) {
			sb.append(System.lineSeparator() + "protocol \"" + role + "\" (");
			for (String move : roleToMoveMap.get(role)) {
				sb.append(System.lineSeparator() + "  legal_" + role + "_" + move + ": Bool, ");
				sb.append(System.lineSeparator() + "  did_" + role + "_" + move + ": observable Bool, ");
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append(System.lineSeparator() + ")");

			sb.append(System.lineSeparator() + "begin");
			sb.append(System.lineSeparator() + "  do");
			sb.append(System.lineSeparator() + "  ");
			for (String move : roleToMoveMap.get(role)) {
				sb.append("legal_" + role + "_" + move + " -> <<Move_" + move + ">>");
				sb.append(System.lineSeparator() + "  [] ");
			}
			sb.delete(sb.length() - 6, sb.length());
			sb.append(System.lineSeparator() + "  od");
			sb.append(System.lineSeparator() + "end");
			sb.append(System.lineSeparator());
		}

		return sb.toString();
	}

	/**
	 * Save string to file.
	 */
	public static void saveFile(String text, String filename) {
		FileWriter writer = null;
		try {
			File file = new File(filename);
			file.createNewFile();

			writer = new FileWriter(file);
			writer.write(text);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Outputs parse tree in lparse format
	 */
	public static String toLparse(GdlNode root) {
		StringBuilder lparse = new StringBuilder();

		lparse.append("{true(V1):base(V1)}.\n");
		lparse.append("1={does(V2, V3):input(V2, V3)} :- role(V2).\n");

		lparse.append(((LparseNode) root).toLparse());

		return lparse.toString();
	}

	/**
	 * Print the getAtom()s of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTree(GdlNode root, String prefix, String indent) {
		System.out.println(prefix + root.getAtom());
		if (!root.getChildren().isEmpty()) {
			for (GdlNode child : root.getChildren()) {
				printParseTree(child, prefix + indent, indent);
			}
		}
	}

	public static void printParseTree(GdlNode root) {
		printParseTree(root, ">", " -");
	}

	/**
	 * Print the GdlType of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTreeTypes(GdlNode root, String prefix, String indent) {
		switch (root.getType()) {
		case ROOT:
			System.out.println(prefix + "ROOT " + root.getAtom());
			break;
		case CLAUSE:
			System.out.println(prefix + "CLAUSE " + root.getAtom());
			break;
		case FORMULA:
			System.out.println(prefix + "FORMULA " + root.getAtom());
			break;
		case FUNCTION:
			System.out.println(prefix + "FUNCTION " + root.getAtom());
			break;
		case CONSTANT:
			System.out.println(prefix + "CONSTANT " + root.getAtom());
			break;
		case VARIABLE:
			System.out.println(prefix + "VARIABLE " + root.getAtom());
			break;
		}

		if (!root.getChildren().isEmpty()) {
			for (GdlNode child : root.getChildren()) {
				printParseTreeTypes(child, prefix + indent, indent);
			}
		}
	}

	public static void printParseTreeTypes(GdlNode root) {
		printParseTreeTypes(root, ">", " -");
	}

	public static void prettyPrint(GdlNode root) {
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE) {
				for (GdlNode literal : clause.getChildren()) {
					if (literal == clause.getChildren().get(0)) {
						System.out.println("(<= " + literal.toString());
					} else if (literal == clause.getChildren().get(clause.getChildren().size() - 1)) {
						System.out.println("   " + literal.toString() + ")");
					} else {
						System.out.println("   " + literal.toString());
					}
				}
			} else {
				System.out.println(clause);
			}
			System.out.println();
		}
	}

	// public enum GdlType {
	// ROOT, CLAUSE, FORMULA, FUNCTION, CONSTANT, VARIABLE
	// }

	/**
	 * Inner class that represents one node in the parse tree where the
	 * getChildren() for a formula are a list of parameters
	 */
	public static class ParseNode implements GdlNode, LparseNode {
		GdlType type;
		String atom;
		GdlNode parent;
		private ArrayList<GdlNode> children;

		ParseNode() {
			this("", null, GdlType.ROOT);
			this.children = new ArrayList<GdlNode>();
		}

		ParseNode(String atom, GdlNode parent, GdlType type) {
			this.atom = atom;
			this.parent = parent;
			this.children = new ArrayList<GdlNode>();
			this.type = type;
		}

		@Override
		public GdlType getType() {
			return type;
		}

		@Override
		public String getAtom() {
			return atom;
		}

		@Override
		public GdlNode getParent() {
			return parent;
		}

		@Override
		public ArrayList<GdlNode> getChildren() {
			return children;
		}

		@Override
		public String toLparse() {
			StringBuilder lparse = new StringBuilder();

			switch (getType()) {
			case ROOT:
				for (GdlNode clause : getChildren()) {
					lparse.append(((LparseNode) clause).toLparse());
					if (clause.getType() == GdlType.CLAUSE) {
						if (clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_INIT)
								|| clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_NEXT)
								|| clause.getChildren().get(0).getAtom().equals(GdlNode.GDL_LEGAL)) {
							lparse.append(((LparseNode) clause).toLparseWithBaseInput());
						}
					}
				}
				break;
			case CLAUSE:
				lparse.append(((LparseNode) getChildren().get(0)).toLparse());// head
				if (getChildren().size() > 1) {
					lparse.append(" :- ");
					for (int i = 1; i < getChildren().size() - 1; i++) {
						lparse.append(((LparseNode) getChildren().get(i)).toLparse());
						lparse.append(", ");
					}
					lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparse());
				}
				lparse.append(".\n");
				break;
			case FORMULA:
			case FUNCTION:
				if (getAtom().equals("not")) {
					lparse.append("t1(");
				} else {
					lparse.append(getAtom() + "(");
				}
				// Parameters
				for (int i = 0; i < getChildren().size() - 1; i++) {
					lparse.append(((LparseNode) getChildren().get(i)).toLparse());
					lparse.append(", ");
				}
				lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparse());
				lparse.append(")");

				// Facts
				if (getParent().getType() == GdlType.ROOT) {
					lparse.append(".\n");
				}
				break;
			case VARIABLE:
				lparse.append(getAtom().replace("?", "V"));
				break;
			default:
				lparse.append(getAtom());
				break;
			}

			return lparse.toString();
		}

		/**
		 * Recursive method for generating lparse formatted representation of
		 * parse tree
		 * 
		 * @return String lparse of the sub-tree rooted at node
		 */
		@Override
		public String toLparseWithBaseInput() {
			StringBuilder lparse = new StringBuilder();

			switch (getType()) {
			case CLAUSE:
				lparse.append(((LparseNode) getChildren().get(0)).toLparseWithBaseInput());// head
				if (getChildren().size() > 1) {
					lparse.append(" :- ");
					for (int i = 1; i < getChildren().size() - 1; i++) {
						lparse.append(((LparseNode) getChildren().get(i)).toLparseWithBaseInput());
						lparse.append(", ");
					}
					lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
				}
				lparse.append(".\n");
				break;
			case FORMULA:
			case FUNCTION:
				// base and inputs
				if (getAtom().equals(GdlNode.GDL_DOES) || getAtom().equals(GdlNode.GDL_LEGAL)) {
					lparse.append("input(");
				} else if (getAtom().equals(GdlNode.GDL_INIT) || getAtom().equals(GdlNode.GDL_TRUE)
						|| getAtom().equals(GdlNode.GDL_NEXT)) {
					lparse.append("base(");
				} else if (getAtom().equals("not")) {
					lparse.append("t1(");
				} else {
					lparse.append(getAtom() + "(");
				}
				// Parameters
				for (int i = 0; i < getChildren().size() - 1; i++) {
					lparse.append(((LparseNode) getChildren().get(i)).toLparseWithBaseInput());
					lparse.append(", ");
				}
				lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
				lparse.append(")");

				// Facts
				if (getParent().getType() == GdlType.ROOT) {
					lparse.append(".\n");
				}
				break;
			default:
				lparse.append(toLparse());
			}
			return lparse.toString();
		}

		@Override
		public Iterator<GdlNode> iterator() {
			Queue<GdlNode> iterator = new LinkedList<GdlNode>();

			iterator.add(this);
			for (GdlNode child : getChildren()) {
				for (GdlNode node : child) {
					iterator.add(node);
				}
			}

			return iterator.iterator();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (!getChildren().isEmpty() && !getAtom().equals("")) {
				sb.append("(");
			}
			sb.append(getAtom());

			for (GdlNode child : getChildren()) {
				sb.append(" " + child.toString());
			}

			if (!getChildren().isEmpty() && !getAtom().equals("")) {
				sb.append(")");
			}

			return sb.toString();
		}

		@Override
		public int hashCode() {
			return atom.hashCode();
		}
	}
}