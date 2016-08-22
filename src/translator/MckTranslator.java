package translator;

import java.io.*;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;
import translator.graph.DomainGraph;
import translator.graph.DependencyGraph;
import translator.graph.Vertex;

/**
 * Translates GDL-II in infix notation to MCK
 */
public class MckTranslator {

	public static final String GDL_ROLE = "role";
	public static final String GDL_LEGAL = "legal";
	public static final String GDL_DOES = "does";
	public static final String GDL_INIT = "init";
	public static final String GDL_TRUE = "true";
	public static final String GDL_NEXT = "next";
	public static final String GDL_SEES = "sees";
	public static final String GDL_CLAUSE = "<=";

	/**
	 * Tokenises a file for GDL and also removes ';' comments
	 */
	public static List<String> gdlTokenizer(Reader file) throws IOException {
		List<String> tokens = new ArrayList<String>();

		StringBuilder sb = new StringBuilder();
		int character;
		boolean comment = false;
		while ((character = file.read()) != -1) {
			switch (character) {
			case '(':
			case ')':
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				if (!comment) {
					tokens.add(String.valueOf((char) character));
				}
				sb = new StringBuilder();
				break;
			case ' ':
			case '\t':
				// whitespace
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				sb = new StringBuilder();
				break;
			case '\n':
			case '\r':
				// new line (ends comments)
				comment = false;
				sb = new StringBuilder();
				break;
			case ';':
				// comment
				comment = true;
				sb.append((char) character);
				break;
			default:
				// all other characters, usually part of atoms
				sb.append((char) character);
				break;
			}
		}

		return tokens;
	}

	/**
	 * Overloaded method which just asks for filePath as opposed to File object
	 */
	public static List<String> tokenizeFile(String filePath) throws IOException, URISyntaxException {
		return gdlTokenizer(new FileReader(new File(filePath)));
	}

	public static List<String> tokenizeGdl(String gdl) throws IOException {
		return gdlTokenizer(new StringReader(gdl));
	}

	/**
	 * Takes tokens and produces a parse tree returns ParseNode root of tree
	 */
	public static ParseNode expandParseTree(List<String> tokens) {
		ParseNode root = new ParseNode("", null, GdlType.ROOT);

		ParseNode parent = root;
		boolean functionName = false;
		boolean openBracket = false;
		boolean scopedVariable = false;
		int scopeNumber = 1;
		for (String token : tokens) {
			switch (token) {
			case "(":
				openBracket = true;
				break;
			case ")":
				parent = parent.parent;
				if (scopedVariable == true && parent.type == GdlType.ROOT) {
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case GDL_CLAUSE:
				ParseNode newNode = new ParseNode(token, parent, GdlType.CLAUSE);
				parent.children.add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				newNode = new ParseNode(token, parent, GdlType.CONSTANT);
				// if (parent.type == GdlType.CLAUSE &&
				// parent.children.isEmpty()) {
				// newNode.type = GdlType.HEAD;
				// } else
				if (parent.type == GdlType.CONSTANT) {
					parent.type = GdlType.FUNCTION;
				}

				if (newNode.atom.charAt(0) == '?') {
					newNode.type = GdlType.VARIABLE;
					scopedVariable = true;
					newNode.atom = newNode.atom + "__" + scopeNumber;
				}
				parent.children.add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			}
		}
		return root;
	}

	// Follows the domain graph def in the ggp book
	public static DomainGraph constructDomainGraph(ParseNode root) {
		DomainGraph graph = new DomainGraph();
		HashMap<ParseNode, DomainGraph.Term> variableMap = new HashMap<ParseNode, DomainGraph.Term>();

		Queue<ParseNode> queue = new LinkedList<ParseNode>();
		queue.addAll(root.getChildren());

		while (!queue.isEmpty()) {
			ParseNode node = queue.remove();
			switch (node.type) {
			case ROOT:
				System.out.println("Should be impossible to reach root");
				break;
			case CLAUSE:
				break;
			case FUNCTION:
				graph.addFunction(node.getAtom(), node.getChildren().size());
				for (int i = 0; i < node.getChildren().size(); i++) {
					if (node.getChildren().get(i).type == GdlType.CONSTANT) {
						graph.addEdge(node.getAtom(), i + 1, node.getChildren().get(i).getAtom(), 0); //TODO: fix this line
					} else if(node.getChildren().get(i).type == GdlType.FUNCTION){
						
					} else {
						ParseNode variable = node.getChildren().get(i);
						if (variableMap.containsKey(variable)) {
							DomainGraph.Term varLink = variableMap.get(variable);
							graph.addEdge(varLink.getTerm(), varLink.getArity(), node.getAtom(), i + 1);
						} else {
							variableMap.put(variable, new DomainGraph.Term(node.getAtom(), i + 1));
						}
					}
				}
				break;
			case CONSTANT:
				break;
			case VARIABLE:

				break;
			default:
				System.out.println("The parse tree has a data error");
			}

			queue.addAll(node.getChildren());
		}

	graph.addEdge("base",1,"true",1);graph.addEdge("input",1,"does",1);graph.addEdge("input",2,"does",2);

	return graph;

	}

	public static boolean isVariableInTree(ParseNode node) {
		if (node.type == GdlType.VARIABLE) {
			return true;
		}

		for (ParseNode child : node.getChildren()) {
			if (isVariableInTree(child)) {
				return true;
			}
		}

		return false;
	}

	public static ParseNode groundGdl(ParseNode root, DomainGraph domainGraph) {
		ParseNode groundedRoot = new ParseNode();

		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = domainGraph.getDomainMap();
		Map<String, List<String>> domainGraphString = new HashMap<String, List<String>>();
		for (DomainGraph.Term term : domainMap.keySet()) {
			String atom = term.getTerm();
			List<String> domain = new ArrayList<String>();
			for (DomainGraph.Term dependency : domainMap.get(term)) {
				domain.add(dependency.getTerm());
			}
			domainGraphString.put(atom, domain);
		}

		for (ParseNode clause : root.getChildren()) {
			if (!isVariableInTree(clause)) {
				groundedRoot.getChildren().add(clause);
			} else {
				String groundedClauseString = groundClause(clause, domainMap);
				List<String> groundedClauseTokens = null;
				try {
					groundedClauseTokens = tokenizeGdl(groundedClauseString);
				} catch (IOException e) {
					e.printStackTrace();
				}

				ParseNode clauseTree = expandParseTree(groundedClauseTokens);
				if (!clauseTree.getChildren().isEmpty()) {
					groundedRoot.getChildren().add(clauseTree.getChildren().get(0));
				}
			}
		}

		return groundedRoot;
	}

	public static String groundClause(ParseNode clauseNode,
			Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap) {
		StringBuilder groundedClauses = new StringBuilder();

		Map<String, List<String>> constantMap = new HashMap<String, List<String>>();
		for (ParseNode node : clauseNode) {
			if (node.type == GdlType.VARIABLE) {
				if (!constantMap.containsKey(node.getAtom())) {
					constantMap.put(node.getAtom(), new ArrayList<String>());
				}

				DomainGraph.Term varTerm = new DomainGraph.Term(
						node.getParent().getAtom(),
						node.getParent().getChildren().indexOf(node)+1);
				if (domainMap.containsKey(varTerm)) {
					for (DomainGraph.Term term : domainMap.get(varTerm)) {
						constantMap.get(node.getAtom()).add(term.getTerm());
					}
				}
			}
		}
		groundedClauses.append(groundClause(clauseNode.toString(), constantMap));

		return groundedClauses.toString();
	}

	/**
	 * 
	 * @return string with grounded clause which can be expanded into parse tree
	 *         of clause
	 * @Deprecated
	 */
	private static String groundClause(String gdlClause, Map<String, List<String>> vertexToDomainMap) {
		StringBuilder groundedClauses = new StringBuilder();

		// TODO: find out how to iterate over all values of all lists

		String clause = gdlClause;
		for (String variable : vertexToDomainMap.keySet()) {
			if (vertexToDomainMap.get(variable) != null && clause.contains(variable)) {
				for (int i = 0; i < vertexToDomainMap.get(variable).size(); i++) {
					clause = clause.replace(variable, vertexToDomainMap.get(variable).get(i));
				}
			}
		}
		groundedClauses.append(clause);

		return groundedClauses.toString();
	}

	public static List<String> findBoolVarsForMck(ParseNode root) {
		ArrayList<String> boolVars = new ArrayList<String>();

		Queue<ParseNode> childrenQueue = new LinkedList<ParseNode>();
		childrenQueue.addAll(root.children);

		while (!childrenQueue.isEmpty()) {
			ParseNode node = childrenQueue.remove();

			switch (node.atom) {
			case "<=":
				childrenQueue.addAll(node.children);
				break;
			case GDL_LEGAL:
				String move = node.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add(move);
				boolVars.add(move + "_old");
				boolVars.add("legal_" + node.children.get(0).atom + "_" + move);
				boolVars.add("did_" + node.children.get(0).atom + "_" + move);
				break;
			case GDL_SEES:
				move = node.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add("sees_" + node.children.get(0).atom + "_" + move);
				break;
			}
		}

		return boolVars;
	}

	public static Map<String, List<String>> findMovesForMck(ParseNode root) {
		Map<String, List<String>> roleToMoveMap = new HashMap<String, List<String>>();

		for (ParseNode clause : root.getChildren()) {
			if (clause.getType() == GdlType.CLAUSE && (clause.getChildren().get(0).getAtom()).equals(GDL_LEGAL)) {
				ParseNode legal = clause.getChildren().get(0);
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
	public static String toMck(ParseNode root) {

		// List<String> roles = findRolesForMck(root);
		Map<String, List<String>> roleToMoveMap = findMovesForMck(root);
		// List<String> legals = findLegalsForMck(root);
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
	public static String toLparse(ParseNode root) {
		StringBuilder lparse = new StringBuilder();

		lparse.append("{true(V1):base(V1)}.\n");
		lparse.append("1={does(V2, V3):input(V2, V3)} :- role(V2).\n");

		lparse.append(root.toLparse());

		return lparse.toString();
	}

	/**
	 * Print the atoms of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTree(ParseNode root, String prefix, String indent) {
		System.out.println(prefix + root.atom);
		if (!root.children.isEmpty()) {
			for (ParseNode child : root.getChildren()) {
				printParseTree(child, prefix + indent, indent);
			}
		}
	}

	public static void printParseTree(ParseNode root) {
		printParseTree(root, ">", " -");
	}

	/**
	 * Print the GdlType of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTreeTypes(ParseNode root, String prefix, String indent) {
		switch (root.type) {
		case ROOT:
			System.out.println(prefix + "ROOT " + root.getAtom());
			break;
		case CLAUSE:
			System.out.println(prefix + "CLAUSE " + root.getAtom());
			break;
		case VARIABLE:
			System.out.println(prefix + "VARIABLE " + root.getAtom());
			break;
		case FUNCTION:
			System.out.println(prefix + "FORMULA " + root.getAtom());
			break;
		case CONSTANT:
			System.out.println(prefix + "CONSTANT " + root.getAtom());
			break;
		default:
			break;
		}

		if (!root.children.isEmpty()) {
			for (ParseNode child : root.children) {
				printParseTreeTypes(child, prefix + indent, indent);
			}
		}
	}

	public static void printParseTreeTypes(ParseNode root) {
		printParseTreeTypes(root, ">", " -");
	}

	/**
	 * Can be used from the command line by moving to the build directory and
	 * using java translator.MckTranslator path/to/game.gdl or java -jar
	 * MckTranslator.jar path/to/game.gdl which will save output to
	 * path/to/game.gdl.mck
	 */
	public static void main(String[] args) {
		boolean helpSwitch = false;
		boolean inputFileSwitch = false;
		boolean inputFileToken = false;
		boolean outputFileSwitch = false;
		boolean outputFileToken = false;
		boolean groundSwitch = false;
		boolean debugSwitch = false;
		boolean outputMckSwitch = false;
		boolean outputLparseSwitch = false;
		boolean outputDotSwitch = false;
		boolean parseTreeSwitch = false;
		boolean parseTreeTypesSwitch = false;

		String inputFilePath = "";
		String outputFilePath = "";

		for (String arg : args) {
			switch (arg) {
			case "-h":
			case "--help":
				helpSwitch = true;
				break;
			case "-o":
			case "--output":
				outputFileSwitch = true;
				outputFileToken = true;
				break;
			case "-i":
			case "--input":
				inputFileSwitch = true;
				inputFileToken = true;
				break;
			case "-g":
			case "--ground":
				groundSwitch = true;
				break;
			case "-d":
			case "--debug":
				debugSwitch = true;
				break;
			case "--to-mck":
				outputMckSwitch = true;
				break;
			case "--to-lparse":
				outputLparseSwitch = true;
				break;
			case "--to-dot":
				outputDotSwitch = true;
				break;
			case "--parse-tree":
				parseTreeSwitch = true;
				break;
			case "--parse-types":
				parseTreeTypesSwitch = true;
				break;
			default:
				if (outputFileToken) {
					outputFilePath = arg;
					outputFileToken = false;
				} else if (inputFileToken) {
					inputFilePath = arg;
					inputFileToken = false;
				} else if (!inputFileSwitch) {
					inputFilePath = arg;
				}
			}
		}

		if (helpSwitch) {
			System.out.println("usage: java -jar MckTranslator.jar [options] [gdlFileInput]");
			System.out.println("Options:");
			System.out.println("  -h --help     print this help file");
			System.out.println("  -i --input    path to input file (default: stdin)");
			System.out.println("  -o --output   path to output file (default: stdout)");
			System.out.println("  --to-mck      output file is in mck format (default)");
			System.out.println("  --to-lparse   output file is in lparse format");
			System.out.println("  --to-dot      output file is in dot format");
			System.out.println("  -g --ground   use internal grounder");
			System.out.println("  -d --debug    manually sellect outputs in debug mode");
			System.out.println("  --parse-tree  print parse tree for debug");
			System.out.println("  --parse-types print parse tree type for debug");
		} else {
			try {
				// Use either
				List<String> tokens;
				if (inputFilePath.equals("")) {
					tokens = gdlTokenizer(new InputStreamReader(System.in));
				} else {
					tokens = tokenizeFile(inputFilePath);
				}
				ParseNode root = expandParseTree(tokens);

				// Use internal grounder
				if (groundSwitch) {
					DomainGraph domain = constructDomainGraph(root);
					if (outputDotSwitch) {
						System.out.println(domain.dotEncodedGraph());
					}
					root = groundGdl(root, domain);
				}

				// Print parse tree for debugging
				if (parseTreeSwitch) {
					printParseTree(root);
				}

				// Print parse tree types for debugging
				if (parseTreeTypesSwitch) {
					printParseTreeTypes(root);
				}

				String translation;
				if (outputLparseSwitch) {
					translation = toLparse(root);
				} else {
					translation = toMck(root);
				}

				if (outputFileSwitch) {
					saveFile(translation, outputFilePath);
				} else if (!debugSwitch || outputLparseSwitch || outputMckSwitch) {
					System.out.println(translation);
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public enum GdlType {
		ROOT, CLAUSE, FUNCTION, VARIABLE, CONSTANT

	}

	/**
	 * Inner class that represents one node in the parse tree where the children
	 * for a formula are a list of parameters
	 */
	public static class ParseNode implements Iterable<ParseNode> {
		private GdlType type;
		private String atom;
		private ParseNode parent;
		private List<ParseNode> children;

		ParseNode() {
			this("", null, GdlType.ROOT);
			this.children = new ArrayList<ParseNode>();
		}

		ParseNode(String atom, ParseNode parent) {
			this(atom, parent, GdlType.ROOT);
			this.children = new ArrayList<ParseNode>();
		}

		ParseNode(String atom, ParseNode parent, GdlType type) {
			this.atom = atom;
			this.parent = parent;
			this.children = new ArrayList<ParseNode>();
			this.type = type;
		}

		public GdlType getType() {
			return type;
		}

		public String getAtom() {
			return atom;
		}

		public ParseNode getParent() {
			return parent;
		}

		public List<ParseNode> getChildren() {
			return children;
		}

		public String toLparse() {
			StringBuilder lparse = new StringBuilder();

			switch (type) {
			case ROOT:
				for (ParseNode clause : getChildren()) {
					lparse.append(clause.toLparse());
					if (clause.getType() == GdlType.CLAUSE) {
						if (clause.getChildren().get(0).getAtom().equals(GDL_INIT)
								|| clause.getChildren().get(0).getAtom().equals(GDL_NEXT)
								|| clause.getChildren().get(0).getAtom().equals(GDL_LEGAL)) {
							lparse.append(clause.toLparseWithBaseInput());
						}
					}
				}
				break;
			case CLAUSE:
				lparse.append(children.get(0).toLparse());// head
				if (children.size() > 1) {
					lparse.append(" :- ");
					for (int i = 1; i < children.size() - 1; i++) {
						lparse.append(children.get(i).toLparse());
						lparse.append(", ");
					}
					lparse.append(children.get(children.size() - 1).toLparse());
				}
				lparse.append(".\n");
				break;
			case FUNCTION:
				// base and inputs
				/*
				 * if(getAtom().equals(GDL_DOES) ||
				 * getAtom().equals(GDL_LEGAL)){ lparse.append("input("); }else
				 * if(getAtom().equals(GDL_INIT) || getAtom().equals(GDL_TRUE)
				 * || getAtom().equals(GDL_NEXT)){ lparse.append("base("); }else
				 */ if (getAtom().equals("not")) {
					lparse.append("t1(");
				} else {
					lparse.append(getAtom() + "(");
				}
				// Parameters
				for (int i = 0; i < children.size() - 1; i++) {
					lparse.append(children.get(i).toLparse());
					lparse.append(", ");
				}
				lparse.append(children.get(children.size() - 1).toLparse());
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
		private String toLparseWithBaseInput() {
			StringBuilder lparse = new StringBuilder();

			switch (type) {
			case CLAUSE:
				lparse.append(children.get(0).toLparseWithBaseInput());// head
				if (children.size() > 1) {
					lparse.append(" :- ");
					for (int i = 1; i < children.size() - 1; i++) {
						lparse.append(children.get(i).toLparseWithBaseInput());
						lparse.append(", ");
					}
					lparse.append(children.get(children.size() - 1).toLparseWithBaseInput());
				}
				lparse.append(".\n");
				break;
			case FUNCTION:
				// base and inputs
				if (getAtom().equals(GDL_DOES) || getAtom().equals(GDL_LEGAL)) {
					lparse.append("input(");
				} else if (getAtom().equals(GDL_INIT) || getAtom().equals(GDL_TRUE) || getAtom().equals(GDL_NEXT)) {
					lparse.append("base(");
				} else if (getAtom().equals("not")) {
					lparse.append("t1(");
				} else {
					lparse.append(getAtom() + "(");
				}
				// Parameters
				for (int i = 0; i < children.size() - 1; i++) {
					lparse.append(children.get(i).toLparseWithBaseInput());
					lparse.append(", ");
				}
				lparse.append(children.get(children.size() - 1).toLparseWithBaseInput());
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

		public Iterator<ParseNode> iterator() {
			Queue<ParseNode> iterator = new LinkedList<ParseNode>();

			iterator.add(this);
			for (ParseNode child : getChildren()) {
				for (ParseNode node : child) {
					iterator.add(node);
				}
			}

			return iterator.iterator();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (!children.isEmpty() && !atom.equals("")) {
				sb.append("(");
			}
			sb.append(atom);

			for (ParseNode child : children) {
				sb.append(" " + child.toString());
			}

			if (!children.isEmpty() && !atom.equals("")) {
				sb.append(")");
			}

			return sb.toString();
		}
	}
}