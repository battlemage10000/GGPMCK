import java.io.*;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Translates GDL-II in infix notation to MCK
 */
public class MckTranslator {

	// Old string manipulation constants for use with String.split()
	public static int STRING_HEAD = 0, STRING_BODY = 1, STRING_TAIL = 2;

	/**
	 * Tokenises a file for GDL and also removes ';' comments
	 */
	public static List<String> tokenizer(FileReader file) throws IOException {
		List<String> tokens = new ArrayList<String>();

		StringBuilder sb = new StringBuilder();
		int character;
		boolean comment = false;
		while ((character = file.read()) != -1) {
			switch (character) {
			case '(':
			case ')':
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				if(!comment){
					tokens.add(String.valueOf((char) character));
				}
				sb = new StringBuilder();
				break;
			case ' ':
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				sb = new StringBuilder();
				break;
			case '\n':
			case '\r':
				comment = false;
				sb = new StringBuilder();
				break;
			case ';':
				comment = true;
				sb.append((char) character);
				break;
			default:
				sb.append((char) character);
				break;
			}
		}

		return tokens;
	}
	
	/**
	 * 
	 */
	public static List<String> tokenizer(String filePath) throws IOException, URISyntaxException {
		return tokenizer(new FileReader(new File(new URI(filePath))));
	}

	/**
	 * Takes tokens and produces a parse tree returns ParseTreeNode root of tree
	 */
	public static ParseTreeNode expandParseTree(List<String> tokens) {
		ParseTreeNode root = new ParseTreeNode("", null);
		root.type = GdlType.ROOT;

		ParseTreeNode parent = root;
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
				if(scopedVariable == true && parent.type == GdlType.ROOT){
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case "<=":
				ParseTreeNode newNode = new ParseTreeNode(token, parent, GdlType.CLAUSE);
				parent.children.add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				newNode = new ParseTreeNode(token, parent, GdlType.CONSTANT);
				if (parent.type == GdlType.CLAUSE && parent.children.isEmpty()) {
					newNode.type = GdlType.HEAD;
				} else if(parent.type == GdlType.CONSTANT){
					parent.type = GdlType.FORMULA;
				}
					
				if (newNode.atom.charAt(0) == '?') {
					newNode.type = GdlType.VARIABLE;
					scopedVariable = true;
					newNode.atom = "?"+scopeNumber+"__"+newNode.atom.substring(1);
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

	/**
	 * Change sentences with variables to grounded equivalent. Takes root of
	 * parse tree and returns root of grounded tree
	 */
	public static ParseTreeNode groundClauses(ParseTreeNode root) {

		// Construct domain dependency map
		DependencyGraph graph = constructDependencyGraph(root);
		graph.printGraph();
		
		for(Vertex vertex : graph.verticies){
			System.out.println("Domain of "+vertex.toString());
			for(String atom : vertex.getDomain()){
				System.out.println(atom);
			}
		}
		
		/*
		 * // Find all the domains Map<String, List<Set<String>>> arityMap = new
		 * HashMap<String, List<Set<String>>>();
		 * 
		 * // Add all clauses to queue Queue<ParseTreeNode> queue = new
		 * LinkedList<ParseTreeNode>(); queue.addAll(root.children);
		 * 
		 * while (!queue.isEmpty()) { ParseTreeNode sentence = queue.remove();
		 * 
		 * String functionName = sentence.atom; List<Set<String>> parameters =
		 * arityMap.get(functionName); if (parameters == null) { parameters =
		 * new ArrayList<Set<String>>(); arityMap.put(functionName, parameters);
		 * } for (int i = 0; i < sentence.children.size(); i++) { ParseTreeNode
		 * child = sentence.children.get(i);
		 * 
		 * Set<String> domain = null; if (i > parameters.size() - 1) { domain =
		 * new HashSet<String>(); parameters.add(i, domain); } else { domain =
		 * parameters.get(i); }
		 * 
		 * domain.add(child.atom);
		 * 
		 * if (!child.children.isEmpty()) { queue.add(child); } } }
		 * 
		 * // Print resulting map for (String function : arityMap.keySet()) {
		 * System.out.println("function: " + function); List<Set<String>>
		 * parameters = arityMap.get(function); for (int i = 0; i <
		 * parameters.size(); i++) { System.out.print("para " + (i + 1) + " {");
		 * for (String atom : parameters.get(i)) { System.out.print(atom + ",");
		 * } System.out.println("}"); } }
		 */

		/*
		 * // Find which clauses have variables in them ParseTreeNode
		 * clausesWithVariables = new ParseTreeNode("", null); for
		 * (ParseTreeNode node : root.children) { queue = new
		 * LinkedList<ParseTreeNode>(); queue.add(node); boolean varFound =
		 * false; while (!queue.isEmpty() && !varFound) { ParseTreeNode child =
		 * queue.remove(); if (child.type == GdlType.VARIABLE) { node.parent =
		 * clausesWithVariables; clausesWithVariables.children.add(node);
		 * varFound = true; } else { queue.addAll(child.children); } } }
		 */
		// remove clauses with variables from parse tree
		// root.children.removeAll(clausesWithVariables.children);

		// printParseTree(clausesWithVariables, "clauses with variables:");

		/*
		 * TODO: before we get to this point we need the domain dependency graph
		 * // ground clauses with variables Set<String> variables =
		 * extractVariables(clausesWithVariables.toString()); for (String var :
		 * variables) { System.out.println("Variable: " + var); for
		 * (ParseTreeNode node : clausesWithVariables.children) { ParseTreeNode
		 * newNode = groundedCopyOfSubTree(node, var, "200"); newNode.parent =
		 * root; root.children.add(newNode); } } //queue = new
		 * LinkedList<ParseTreeNode>();
		 * //queue.addAll(clausesWithVariables.children); //while
		 * (!queue.isEmpty()) { //
		 * root.children.add(groundedCopyOfSubTree(queue.remove(), "?d",
		 * "200")); //}
		 * 
		 * // printParseTree(clausesWithVariables, "Clause with variable :");
		 * 
		 */

		return root;
	}

	/**
	 * 
	 * @param root
	 * @return
	 */
	public static DependencyGraph constructDependencyGraph(ParseTreeNode root) {
		DependencyGraph graph = new DependencyGraph();

		Queue<ParseTreeNode> queue = new LinkedList<ParseTreeNode>();
		queue.addAll(root.children);
		Map<String, Vertex> variableToVertexMap = new HashMap<String, Vertex>();

		while (!queue.isEmpty()) {
			ParseTreeNode node = queue.remove();

			switch(node.type){
			case VARIABLE:
				if(variableToVertexMap.containsKey(node.atom) && variableToVertexMap.get(node.atom) != null){
					variableToVertexMap.get(node.atom).addNeighbor(graph.getVertex(node.parent.atom, node.parent.children.indexOf(node)+1));
				}else {
					if(!graph.hasVertex(node.parent.atom, node.parent.children.indexOf(node)+1)){
						graph.addVertex(node.parent.atom, node.parent.children.indexOf(node)+1);
					}	
					variableToVertexMap.put(node.atom, graph.getVertex(node.parent.atom, node.parent.children.indexOf(node)+1));
				}
				break;	
			case FORMULA:
			case HEAD:
			case CONSTANT:
				if (!graph.hasVertex(node.atom, 0)) {
					graph.addVertex(node.atom, 0);
				}
				if (node.parent.type != GdlType.CLAUSE && !graph.hasVertex(node.parent.atom, node.parent.children.indexOf(node) + 1)) {
					graph.addVertex(node.parent.atom, node.parent.children.indexOf(node) + 1);
				}
				if(node.parent.type != GdlType.CLAUSE){
					graph.getVertex(node.parent.atom, node.parent.children.indexOf(node) + 1)
						.addNeighbor(graph.getVertex(node.atom, 0));
				}
			case ROOT:
			case CLAUSE:
			}
			queue.addAll(node.children);
		}

		return graph;
	}

	/**
	 * Recursive method used to duplicate a subtree with a particular variable
	 * instantiated to a particular constant
	 * 
	 * @param root
	 * @param variable
	 * @param constant
	 * @return groundedRoot
	 */
	public static ParseTreeNode groundedCopyOfSubTree(ParseTreeNode oldNode, String variable, String constant) {
		ParseTreeNode newNode = new ParseTreeNode();
		if (!oldNode.distinct(variable)) {
			newNode.atom = constant;
			newNode.type = GdlType.FORMULA;
		} else {
			newNode.atom = oldNode.atom;
			newNode.type = oldNode.type;
		}

		for (ParseTreeNode oldChild : oldNode.children) {
			ParseTreeNode newChild = groundedCopyOfSubTree(oldChild, variable, constant);
			newChild.parent = newNode;
			newNode.children.add(newChild);
		}
		return newNode;
	}

	/**
	 * TODO: takes a parse tree and returns MCK equivalent
	 */
	public static String toMck(ParseTreeNode root) {
		StringBuilder sb = new StringBuilder();
		// Construct MCK version
		sb.append(System.lineSeparator());
		sb.append("-- Environment Variables");
		sb.append(System.lineSeparator());
		sb.append("-- Environment Initial Conditions");
		sb.append(System.lineSeparator());
		sb.append("-- Agent bindings");
		List<String> roles = new ArrayList<String>();
		for (String role : roles) {
			sb.append(System.lineSeparator());
			sb.append("agent " + role + " \"" + role + "\"");
		}
		sb.append(System.lineSeparator());
		sb.append("-- Specification");
		sb.append(System.lineSeparator());
		sb.append("-- Protocol Declarations");
		sb.append(System.lineSeparator());

		return sb.toString();
	}

	/**
	 * Extracts a set of Strings which represent the vocabulary of the GDL
	 * 
	 * @param gdl
	 * @return vocabulary
	 */
	public static Set<String> extractVocabulary(String gdl) {
		// Extracting atoms
		Set<String> vocabulary = new HashSet<String>();
		for (String atom : gdl.split("\\(| |\\)")) {
			vocabulary.add(atom);
		}
		vocabulary.remove("");
		return vocabulary;
	}

	/**
	 * Extract a set of relations in the GDL
	 * 
	 * @param gdl
	 * @return relationsSet
	 */
	public static Set<String> extractRelations(String gdl) {
		// Extracting relations
		Set<String> relationsSet = new HashSet<String>();
		for (String sentence : gdl.split("\\(")) {
			relationsSet.add(sentence.split(" ")[0]);
		}
		relationsSet.remove("");
		return relationsSet;
	}

	/**
	 * Extract a set of variables from the GDL. Should not be used because the
	 * variableSet doesn't take scope into consideration
	 * 
	 * @deprecated
	 * @param gdl
	 * @return
	 */
	public static Set<String> extractVariables(String gdl) {
		Set<String> variables = new HashSet<String>();
		String[] splitString = gdl.split("\\(| |\\)");
		for (String atom : splitString) {
			if (atom.length() > 0 && atom.charAt(0) == '?') {
				variables.add(atom);
			}
		}
		return variables;
	}

	/**
	 * Extracts variables from the GDL in token list form. Shouldn't be used
	 * because doesn't take into consideration variable scope and allows
	 * overlaps
	 * 
	 * @deprecated
	 * @param tokens
	 * @return variables
	 */
	public static Set<String> variableSet(List<String> tokens) {
		Set<String> variables = new HashSet<String>();
		for (String token : tokens) {
			if (token.charAt(0) == '?') {
				variables.add(token);
			}
		}
		return variables;
	}

	/**
	 * Given a string with "(" find everything it's closing ")" Effectively
	 * changing "atom (atom (atom)) (atom)" to { "atom", "atom (atom)",
	 * " (atom)"}
	 * 
	 * @deprecated
	 * @param string
	 * @return splitString : String[] { before, middle, after } or "" if no
	 *         brackets
	 */
	public static String[] findNextBracket(String string) {
		int level;
		String[] splitString = string.split("\\(", 2);// find first cut
		if (splitString.length > 1) {
			level = 1;
		} else {
			splitString[0] = splitString[0].trim();
			return splitString;
		}
		String before = splitString[STRING_HEAD];

		// find second cut
		StringBuilder sb = new StringBuilder();
		while (level > 0) {

			// split on closing bracket
			splitString = splitString[STRING_BODY].split("\\)", 2);
			level--;

			// add all opening brackets to level
			level += splitString[STRING_HEAD].split("\\(").length - 1;

			// add everything before closing bracket
			sb.append(splitString[STRING_HEAD].trim());
			if (level > 0)
				sb.append(")");
		}

		return new String[] { before.trim(), sb.toString().trim(), splitString[STRING_BODY].trim() };
	}

	/**
	 * I will write recursive method that will fill the children of a
	 * ParseTreeNode
	 * 
	 * @deprecated
	 * @param parent
	 * @param gdl
	 * @return
	 */
	private static ParseTreeNode expandParseTree(ParseTreeNode parent, String string) {
		System.out.println(string);
		String[] splitString = string.trim().split("\\(| |\\)", 2);
		ParseTreeNode ParseTreeNode = null;

		if (splitString[0] != "") {
			ParseTreeNode = new ParseTreeNode(splitString[0], parent);
		} else {
			ParseTreeNode = parent;
		}
		// ParseTreeNode.parent = parent;
		// ParseTreeNode.atom = splitString[0];

		System.out.println(ParseTreeNode.atom);

		if (splitString.length > 1) {
			splitString = findNextBracket(splitString[STRING_BODY]);
		} else {
			// return ParseTreeNode;
		}

		while (splitString.length > 1) {
			if (splitString[STRING_HEAD].trim() != "") {
				String[] constants = splitString[STRING_HEAD].trim().split(" ");
				for (String constant : constants) {
					if (constant != "") {
						ParseTreeNode.children.add(new ParseTreeNode(constant, ParseTreeNode));
					}
				}
			}

			ParseTreeNode.children.add(expandParseTree(ParseTreeNode, splitString[STRING_BODY]));
			splitString = findNextBracket(splitString[STRING_TAIL]);
		}

		if (splitString[STRING_HEAD].trim() != "") {
			String[] constants = splitString[STRING_HEAD].trim().split(" ");
			for (String constant : constants) {
				if (constant != "") {
					ParseTreeNode.children.add(new ParseTreeNode(constant, ParseTreeNode));
				}
			}
		}
		return ParseTreeNode;
	}

	/**
	 * Print the atoms of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTree(ParseTreeNode root, String indent) {
		System.out.println(indent + root.atom);
		if (!root.children.isEmpty()) {
			for (ParseTreeNode child : root.children) {
				printParseTree(child, indent + " -");
			}
		}
	}

	/**
	 * Print the GdlType of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTreeTypes(ParseTreeNode root, String indent) {
		switch (root.type) {
		case ROOT:
			System.out.println(indent + "ROOT");
			break;
		case CLAUSE:
			System.out.println(indent + "CLAUSE");
			break;
		case HEAD:
			System.out.println(indent + "HEAD");
			break;
		case VARIABLE:
			System.out.println(indent + "VARIABLE");
			break;
		case FORMULA:
			System.out.println(indent + "FORMULA");
			break;
		case CONSTANT:
			System.out.println(indent + "CONSTANT");
			break;
		default:
			break;
		}

		if (!root.children.isEmpty()) {
			for (ParseTreeNode child : root.children) {
				printParseTreeTypes(child, indent + " -");
			}
		}
	}
	
	public static void main(String[] args) throws IOException{
		final String defaultGamePath = "file:/Users/vedantds/Dropbox/Masters/MCK/MckTranslator/res/gdlii/MontyHall.gdl";
		
		FileReader reader = null;
		try {
			//reader = new FileReader(defaultGamePath);
			List<String> tokens = tokenizer(defaultGamePath);

			ParseTreeNode root = expandParseTree(tokens);

			root = groundClauses(root);

			// System.out.println(root.toString());
			//printParseTreeTypes(root, "=");
		} catch(URISyntaxException e) { 
			e.printStackTrace();
		}catch(IOException e) { 
			e.printStackTrace();
		}finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	public enum GdlType {
		ROOT, CLAUSE, HEAD, FORMULA, VARIABLE, CONSTANT
	}

	static class ParseTreeNode {
		GdlType type;
		String atom;
		ParseTreeNode parent;
		List<ParseTreeNode> children;

		ParseTreeNode() {
			atom = "";
			parent = null;
			children = new ArrayList<ParseTreeNode>();
			type = GdlType.ROOT;
		}

		ParseTreeNode(String atom, ParseTreeNode parent) {
			this.atom = atom;
			this.parent = parent;
			this.children = new ArrayList<ParseTreeNode>();
			this.type = GdlType.ROOT;
		}

		ParseTreeNode(String atom, ParseTreeNode parent, GdlType type) {
			this.atom = atom;
			this.parent = parent;
			this.children = new ArrayList<ParseTreeNode>();
			this.type = type;
		}

		public boolean distinct(String atom) {
			return !this.atom.equals(atom);
		}

		public boolean distinct(ParseTreeNode node) {
			return !this.atom.equals(node.atom);
		}

		@Deprecated
		public int arity() {
			if (parent != null && parent.children.contains(this)) {
				return parent.children.indexOf(this);
			} else {
				return -1;
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (!children.isEmpty() && !atom.equals("")) {
				sb.append("(");
			}
			sb.append(atom);

			for (ParseTreeNode child : children) {
				sb.append(" " + child.toString());
			}

			if (!children.isEmpty() && !atom.equals("")) {
				sb.append(")");
			}

			return sb.toString();
		}
	}
}