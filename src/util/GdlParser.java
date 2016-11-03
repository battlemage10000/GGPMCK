package util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import util.grammar.GdlNode;
import util.grammar.GdlNodeFactory;
import util.grammar.LparseNode;
import util.grammar.GdlNode.GdlType;
import util.graph.DependencyGraph;
import util.graph.DomainGraph;

/**
 * @author vedantds
 *
 */
public class GdlParser {

	public final static char SEMICOLON = ';';
	public final static char OPEN_P = '(';
	public final static char CLOSE_P = ')';
	public final static char SPACE = ' ';
	public final static char TAB = '\t';
	public final static char NEW_LINE = '\n';
	public final static char RETURN = '\r';

	/**
	 * Tokenises a file for GDL and also removes ';' comments
	 * 
	 * @param reader
	 * @return tokens : List<String>
	 * @throws IOException
	 */
	public static List<String> gdlTokenizer(Reader reader) throws IOException {
		List<String> tokens = new ArrayList<String>();

		StringBuilder sb = new StringBuilder();
		int character;
		boolean comment = false;
		while ((character = reader.read()) != -1) {
			switch (character) {
			case OPEN_P:
			case CLOSE_P:
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				if (!comment) {
					tokens.add(String.valueOf((char) character));
				}
				sb = new StringBuilder();
				break;
			case SPACE:
			case TAB:
				// whitespace
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				sb = new StringBuilder();
				break;
			case NEW_LINE:
			case RETURN:
				// new line (ends comments)
				if (comment) {
					sb = new StringBuilder();
				}
				comment = false;
				break;
			case SEMICOLON:
				// start of comment
				comment = true;
				break;
			default:
				// all other characters, usually part of atoms
				sb.append((char) character);
				break;
			}
		}

		reader.close();
		return tokens;
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for common use
	 * cases
	 * 
	 * @param filePath
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static List<String> tokenizeFile(String filePath) throws IOException, URISyntaxException {
		try (FileReader fr = new FileReader(new File(filePath))) {
			return gdlTokenizer(fr);
		}
	}

	/**
	 * Overloaded method which doesn't require casting to Reader and handles
	 * resulting IOException
	 * 
	 * @param gdl
	 * @return tokens as a list of strings
	 */
	public static List<String> tokenizeString(String gdl) {
		List<String> tokens = new ArrayList<String>();
		try {
			tokens = gdlTokenizer(new StringReader(gdl));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tokens;
	}

	/**
	 * Takes tokens and produces a parse tree returns ParseNode root of tree
	 *
	 * @param tokens
	 * @return
	 */
	public static GdlNode expandParseTree(List<String> tokens) {
		GdlNode root = GdlNodeFactory.createGdl();

		GdlNode parent = root;
		boolean openBracket = false;
		boolean scopedVariable = false;
		int scopeNumber = 1;
		for (String token : tokens) {
			switch (token) {
			case "(":
				openBracket = true;
				break;
			case ")":
				parent = parent.getParent();
				if (scopedVariable == true && parent.getType() == GdlType.ROOT) {
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case GdlNode.GDL_CLAUSE:
				GdlNode newNode = GdlNodeFactory.createGdlRule(parent);
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				if (token.charAt(0) == '?') {
					scopedVariable = true;
					token = "?" + scopeNumber + "_" + token;
				}
				if (parent.getType() == GdlType.CLAUSE || parent.getType() == GdlType.ROOT) {
					newNode = GdlNodeFactory.createGdlFormula(token, parent);
				} else {
					newNode = GdlNodeFactory.createGdlTerm(token, parent);
				}
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			}
		}
		return root;
	}

	/**
	 * @param filePath
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static GdlNode parseFile(String filePath) throws IOException, URISyntaxException {
		return expandParseTree(tokenizeFile(filePath));
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for common use
	 * cases
	 * 
	 * @param gdl
	 * @return
	 * @throws IOException
	 */
	public static GdlNode parseString(String gdl) {
		return expandParseTree(tokenizeString(gdl));
	}

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
				String headNodeString = node.getChildren().get(0).getAtom();
				switch (headNodeString) {
				case GdlNode.GDL_BASE:
				case GdlNode.GDL_INPUT:
					break;
				// Skip base and input clauses
				case GdlNode.GDL_NEXT:
					headNodeString = "true_" + formatGdlNode(node.getChildren().get(0).getChildren().get(0));
				default:
					for (int i = 1; i < node.getChildren().size(); i++) {
						boolean isNextTrue = false;
						GdlNode toNode = node.getChildren().get(i);
						while (toNode.getAtom().equals(GdlNode.GDL_NOT) || toNode.getAtom().equals(GdlNode.GDL_TRUE)
								|| toNode.getAtom().equals(GdlNode.GDL_NEXT)) {
							if (toNode.getAtom().equals(GdlNode.GDL_TRUE)
									|| toNode.getAtom().equals(GdlNode.GDL_NEXT)) {
								isNextTrue = true;
							}
							toNode = toNode.getChildren().get(0);
						}
						String toNodeString = toNode.getAtom();
						if (isNextTrue) {
							toNodeString = "true_" + formatGdlNode(toNode);
						}
						if (!headNodeString.equals(toNodeString)) {
							graph.addEdge(headNodeString, toNodeString);
						}
					}
				}
			}
		}

		return graph;
	}

	/**
	 * @param node
	 * @return
	 */
	public static String formatGdlNode(GdlNode node) {
		StringBuilder sb = new StringBuilder();
		sb.append(node.getAtom());
		if (!node.getChildren().isEmpty()) {
			for (GdlNode child : node.getChildren()) {
				sb.append("_" + formatGdlNode(child));
			}
		}
		return sb.toString();
	}

	/**
	 * Follows the domain graph definition in the ggp book
	 * 
	 * @param root
	 * @return
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

	/**
	 * @param node
	 * @return
	 */
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

	/**
	 * @param root
	 * @param domainGraph
	 * @return
	 */
	public static GdlNode groundGdl(GdlNode root, DomainGraph domainGraph) {
		GdlNode groundedRoot = GdlNodeFactory.createGdl();

		for (GdlNode clause : root.getChildren()) {
			if (!isVariableInTree(clause)) { // No variables is already ground
				groundedRoot.getChildren().add(clause);
			} else {
				String groundedClauseString = groundClause(clause, domainGraph.getMap());

				// Default root node if parseString throws error
				GdlNode clauseTree = GdlNodeFactory.createGdl();
				clauseTree = GdlParser.parseString(groundedClauseString);
				if (!clauseTree.getChildren().isEmpty()) {
					groundedRoot.getChildren().addAll(clauseTree.getChildren());
				}
			}
		}
		return groundedRoot;
	}

	/**
	 * @param clauseNode
	 * @param domainMap
	 * @return
	 */
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

	/**
	 * Save string to file.
	 * 
	 * @param text
	 * @param filename
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
	 * 
	 * @param root
	 * @return
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
	 * @param prefix
	 * @param indent
	 */
	public static String printParseTree(GdlNode root, String prefix, String indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator() + prefix + root.getAtom());
		// System.out.println(prefix + root.getAtom());
		if (!root.getChildren().isEmpty()) {
			for (GdlNode child : root.getChildren()) {
				sb.append(System.lineSeparator() + printParseTree(child, prefix + indent, indent));
			}
		}
		return sb.toString();
	}

	/**
	 * @param root
	 */
	public static String printParseTree(GdlNode root) {
		return printParseTree(root, ">", " -");
	}

	/**
	 * Print the GdlType of the nodes of the tree
	 * 
	 * @param root
	 * @param prefix
	 * @param indent
	 */
	public static String printParseTreeTypes(GdlNode root, String prefix, String indent) {
		StringBuilder sb = new StringBuilder();
		switch (root.getType()) {
		case ROOT:
			sb.append(System.lineSeparator() + prefix + "ROOT " + root.getAtom());
			break;
		case CLAUSE:
			sb.append(System.lineSeparator() + prefix + "CLAUSE " + root.getAtom());
			break;
		case FORMULA:
			sb.append(System.lineSeparator() + prefix + "FORMULA " + root.getAtom());
			break;
		case FUNCTION:
			sb.append(System.lineSeparator() + prefix + "FUNCTION " + root.getAtom());
			break;
		case CONSTANT:
			sb.append(System.lineSeparator() + prefix + "CONSTANT " + root.getAtom());
			break;
		case VARIABLE:
			sb.append(System.lineSeparator() + prefix + "VARIABLE" + root.getAtom());
			break;
		}

		if (!root.getChildren().isEmpty()) {
			for (GdlNode child : root.getChildren()) {
				sb.append(printParseTreeTypes(child, prefix + indent, indent));
			}
		}
		return sb.toString();
	}

	/**
	 * @param root
	 */
	public static String printParseTreeTypes(GdlNode root) {
		return printParseTreeTypes(root, ">", " -");
	}

	/**
	 * @param root
	 */
	public static String prettyPrint(GdlNode root) {
		StringBuilder sb = new StringBuilder();
		for (GdlNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE) {
				for (GdlNode literal : clause.getChildren()) {
					if (literal == clause.getChildren().get(0)) {
						sb.append(System.lineSeparator() + "(<= " + literal.toString());
					} else if (literal == clause.getChildren().get(clause.getChildren().size() - 1)) {
						sb.append(System.lineSeparator() + "   " + literal.toString() + ")");
					} else {
						sb.append(System.lineSeparator() + "   " + literal.toString());
					}
				}
			} else {
				sb.append(System.lineSeparator() + clause);
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}
}
