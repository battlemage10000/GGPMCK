package translator;

import java.io.*;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;
import translator.graph.DependencyGraph;
import translator.graph.Vertex;

/**
 * Translates GDL-II in infix notation to MCK
 */
public class MckTranslator {

	public static String GDL_ROLE = "role";
	public static String GDL_LEGAL = "legal";
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
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				if(!comment){
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
	public static List<String> tokenizer(String filePath) throws IOException, URISyntaxException {
		return tokenizer(new FileReader(new File(filePath)));
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
				if(scopedVariable == true && parent.type == GdlType.ROOT){
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case "<=":
				ParseNode newNode = new ParseNode(token, parent, GdlType.CLAUSE);
				parent.children.add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				newNode = new ParseNode(token, parent, GdlType.CONSTANT);
				//if (parent.type == GdlType.CLAUSE && parent.children.isEmpty()) {
					//newNode.type = GdlType.HEAD;
				//} else 
				if(parent.type == GdlType.CONSTANT){
					parent.type = GdlType.FORMULA;
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

	/**
	 * Change sentences with variables to grounded equivalent. Takes root of
	 * parse tree and returns root of grounded tree
	 */
	public static ParseNode groundClauses(ParseNode root) {

		// Construct domain dependency map
		DependencyGraph graph = constructDependencyGraph(root);
		//graph.printGraph();
		
		// TODO: replace variables with domain(grounding)
		
		/*
		for(DependencyGraph.Vertex vertex : graph.verticies){
			System.out.println("Domain of "+vertex.toString());
			for(String atom : vertex.getDomain()){
				System.out.println(atom);
			}
		}
		*/
		
		/*
		 * // Find all the domains Map<String, List<Set<String>>> arityMap = new
		 * HashMap<String, List<Set<String>>>();
		 * 
		 * // Add all clauses to queue Queue<ParseNode> queue = new
		 * LinkedList<ParseNode>(); queue.addAll(root.children);
		 * 
		 * while (!queue.isEmpty()) { ParseNode sentence = queue.remove();
		 * 
		 * String functionName = sentence.atom; List<Set<String>> parameters =
		 * arityMap.get(functionName); if (parameters == null) { parameters =
		 * new ArrayList<Set<String>>(); arityMap.put(functionName, parameters);
		 * } for (int i = 0; i < sentence.children.size(); i++) { ParseNode
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
		 * // Find which clauses have variables in them ParseNode
		 * clausesWithVariables = new ParseNode("", null); for
		 * (ParseNode node : root.children) { queue = new
		 * LinkedList<ParseNode>(); queue.add(node); boolean varFound =
		 * false; while (!queue.isEmpty() && !varFound) { ParseNode child =
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
		 * (ParseNode node : clausesWithVariables.children) { ParseNode
		 * newNode = groundedCopyOfSubTree(node, var, "200"); newNode.parent =
		 * root; root.children.add(newNode); } } //queue = new
		 * LinkedList<ParseNode>();
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
	public static DependencyGraph constructDependencyGraph(ParseNode root) {
		// Initialize empty graph
		DependencyGraph<Arguments> graph = new DependencyGraph<Arguments>();
		
		
		// Initialize queue and add all the branches of the root node
		Queue<ParseNode> queue = new LinkedList<ParseNode>();
		queue.addAll(root.children);
		// Initialize a map that is used to link different times a variable is called in a clause
		Map<String, Vertex> variableToVertexMap = new HashMap<String, Vertex>();

		while (!queue.isEmpty()) {
			ParseNode node = queue.remove(); // Get next node in queue
			
			// TODO: Fix issue where some domains aren't followed properly
			
			switch(node.type){
			
				// Variables first instance added to variableToVertexMap which is then retrieved every time variable is called again
			case VARIABLE:
				if(variableToVertexMap.containsKey(node.atom) && variableToVertexMap.get(node.atom) != null){
					variableToVertexMap.get(node.atom).addNeighbor(graph.getVertex(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
				}else {
					if(!graph.hasVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)))){
						graph.addVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
					}	
					variableToVertexMap.put(node.atom, graph.getVertex(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
				}
				break;
				
				// Non-Variables{Formula, Head, Constant} add node as depencency of parent
			case FORMULA:
			case HEAD:
			case CONSTANT:
				if (!graph.hasVertex(new Vertex<Arguments>(new Arguments(node.atom, 0)))) {
					graph.addVertex(new Vertex<Arguments>(new Arguments(node.atom, 0)));
				}
				if (node.parent.type != GdlType.CLAUSE && !graph.hasVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node) + 1)))) {
					graph.addVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node) + 1)));
				}
				if(node.parent.type != GdlType.CLAUSE){
					graph.getVertex(new Arguments(node.parent.atom, node.parent.children.indexOf(node) + 1))
						.addNeighbor(graph.getVertex(new Arguments(node.atom, 0)));
				}
				
				// Root or Clause do nothing
			case ROOT:
			case CLAUSE:
			}
			queue.addAll(node.children); // Add branches of node to queue
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
	public static ParseNode groundedCopyOfSubTree(ParseNode oldNode, String variable, String constant) {
		ParseNode newNode = new ParseNode();
		if (!oldNode.distinct(variable)) {
			newNode.atom = constant;
			newNode.type = GdlType.FORMULA;
		} else {
			newNode.atom = oldNode.atom;
			newNode.type = oldNode.type;
		}

		for (ParseNode oldChild : oldNode.children) {
			ParseNode newChild = groundedCopyOfSubTree(oldChild, variable, constant);
			newChild.parent = newNode;
			newNode.children.add(newChild);
		}
		return newNode;
	}

	public static List<String> findRolesForMck(ParseNode root){
		ArrayList<String> roles = new ArrayList<String>();
		for(ParseNode child : root.children){
			if(child.atom.equals(GDL_ROLE)){
				roles.add(child.children.get(0).atom);
			}
		}
		return roles;
	}

	public static List<String> findLegalsForMck(ParseNode root){
		ArrayList<String> legals = new ArrayList<String>();
		
		for(ParseNode node : root.children){
			if(node.atom.equals("<=")){
				ParseNode child = node.children.get(0);
				if(child.atom.equals("legal")){
					legals.add("legal_"+child.children.get(0).atom+"_"+child.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_"));
				}
			}
		}
		return legals;
	}

	public static List<String> findBoolVarsForMck(ParseNode root){
		ArrayList<String> boolVars = new ArrayList<String>();
		
		Queue<ParseNode> childrenQueue = new LinkedList<ParseNode>();
		childrenQueue.addAll(root.children);
		
		while(!childrenQueue.isEmpty()){
			ParseNode node = childrenQueue.remove();
			
			switch(node.atom){
			case "<=":
				childrenQueue.addAll(node.children);
				break;
			case "legal":
				String move = node.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add(move);
				boolVars.add(move + "_old");
				boolVars.add("legal_" + node.children.get(0).atom + "_" + move);
				boolVars.add("did_" + node.children.get(0).atom + "_" + move);
				break;
			case "sees":
				move = node.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				boolVars.add("sees_" + node.children.get(0).atom + "_" + move);
				break;
			}
		}
		
		return boolVars;
	}

	/**
	 * TODO: takes a parse tree and returns MCK equivalent
	 * Take 
	 */
	public static String toMck(ParseNode root) {
		
		List<String> roles = findRolesForMck(root);
		List<String> legals = findLegalsForMck(root);
		List<String> boolVars = findBoolVarsForMck(root);
		
		StringBuilder sb = new StringBuilder();
		// Construct MCK version
		sb.append("-- MCK file generated using MckTranslator from a GGP game description");
		sb.append(System.lineSeparator());
		
		sb.append(System.lineSeparator() + "-- Environment Variables");
		for(String boolVar : boolVars){
			sb.append(System.lineSeparator());
			sb.append(boolVar + ": Bool");
		}
		sb.append(System.lineSeparator());
		
		
		sb.append(System.lineSeparator() + "-- Environment Initial Conditions");
		sb.append(System.lineSeparator() + "init_cond = ");
		for(String var : boolVars){
			sb.append(System.lineSeparator());
			sb.append(var + "==False /\\ ");
		}
		sb.delete(sb.length() - 4, sb.length());
		sb.append(System.lineSeparator());
		
		sb.append(System.lineSeparator() + "-- Agent bindings");
		for (String role : roles) {
			sb.append(System.lineSeparator() + "agent Player_" + role + " \"" + role + "\" (");
			for(String var : boolVars){
				if(var.contains("_"+role+"_")){
					sb.append(System.lineSeparator() + var + ", ");
				}
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append(System.lineSeparator() + ")");
		}
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator() + "transitions");
		sb.append(System.lineSeparator() + "begin");
		
		sb.append(System.lineSeparator() + "if Player_red.Move_red_hand_rock -> did_red_red_hand_rock:=True");
		sb.append(System.lineSeparator() + "[] otherwise -> did_red_red_hand_rock := False");
		sb.append(System.lineSeparator() + "fi;");
		
		sb.append(System.lineSeparator() + "end");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		
		sb.append("-- Specification");
		sb.append(System.lineSeparator());
		sb.append("spec_spr = AG(");
		for(String legal : legals){
			for(String role : roles){
				sb.append("(" + legal + " => Knows Player_" + role + " " + legal + ")");
				sb.append(" /\\ ");
			}
		}
		sb.delete(sb.length() - 4, sb.length());
		sb.append(")");
		sb.append(System.lineSeparator());
		
		sb.append(System.lineSeparator() + "-- Protocol Declarations");
		for(String role : roles){
			sb.append(System.lineSeparator() + "protocol \""+role+"\" (");
			for(String var : boolVars){
				if(var.contains("_"+role+"_")){
					sb.append(System.lineSeparator()+"  ");
					if(var.contains("legal")){
						sb.append(var + ": Bool, ");
					}else{
						sb.append(var + ": observable Bool, ");
					}
				}
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append(System.lineSeparator() + ")");
			
			sb.append(System.lineSeparator() + "begin");
			sb.append(System.lineSeparator() + "  do");
			sb.append(System.lineSeparator() + "  ");
			for(String var : boolVars){
				if(var.contains("legal_" + role + "_")){
					sb.append(var + " -> <<Move_" + var.substring(7 + role.length(), var.length()) + ">>");
					sb.append(System.lineSeparator() + "  [] ");
				}
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
	public static void saveFile(String text, String filename){
		try(FileWriter writer = new FileWriter(filename)){
			writer.write(text);
			writer.flush();
			writer.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * Extracts a set of Strings which represent the vocabulary of the GDL
	 * 
	 * @deprecated
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
	 * @deprecated
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
	/*public static String[] findNextBracket(String string) {
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
	}*/

	/**
	 * A recursive method that will expand the children of a ParseNode
	 * 
	 * @deprecated
	 * @param parent
	 * @param gdl
	 * @return
	 */
	/*private static ParseNode expandParseTree(ParseNode parent, String string) {
		//System.out.println(string);
		String[] splitString = string.trim().split("\\(| |\\)", 2);
		ParseNode ParseNode = null;

		if (splitString[0] != "") {
			ParseNode = new ParseNode(splitString[0], parent);
		} else {
			ParseNode = parent;
		}
		// ParseNode.parent = parent;
		// ParseNode.atom = splitString[0];

		//System.out.println(ParseNode.atom);

		if (splitString.length > 1) {
			splitString = findNextBracket(splitString[STRING_BODY]);
		} else {
			// return ParseNode;
		}

		while (splitString.length > 1) {
			if (splitString[STRING_HEAD].trim() != "") {
				String[] constants = splitString[STRING_HEAD].trim().split(" ");
				for (String constant : constants) {
					if (constant != "") {
						ParseNode.children.add(new ParseNode(constant, ParseNode));
					}
				}
			}

			ParseNode.children.add(expandParseTree(ParseNode, splitString[STRING_BODY]));
			splitString = findNextBracket(splitString[STRING_TAIL]);
		}

		if (splitString[STRING_HEAD].trim() != "") {
			String[] constants = splitString[STRING_HEAD].trim().split(" ");
			for (String constant : constants) {
				if (constant != "") {
					ParseNode.children.add(new ParseNode(constant, ParseNode));
				}
			}
		}
		return ParseNode;
	}*/

	/**
	 * Print the atoms of the nodes of the tree
	 * 
	 * @param root
	 * @param indent
	 */
	public static void printParseTree(ParseNode root, String indent) {
		System.out.println(indent + root.atom);
		if (!root.children.isEmpty()) {
			for (ParseNode child : root.children) {
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
	public static void printParseTreeTypes(ParseNode root, String indent) {
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
			for (ParseNode child : root.children) {
				printParseTreeTypes(child, indent + " -");
			}
		}
	}
	
	/**
	 * Can be used from the command line by moving to the build directory and using
	 *     java MckTranslator path/to/game.gdl
	 * which will save output to path/to/game.gdl.mck
	 */
	public static void main(String[] args){
		final String defaultGamePath = "gdlii/MontyHall.gdl";
		String gamePath;
		if(args.length > 0) {
			gamePath = args[0];
		}else{
			gamePath = defaultGamePath;
		}
		
		try {
			List<String> tokens = tokenizer(gamePath);
			ParseNode root = expandParseTree(tokens);
			root = groundClauses(root);
			
			String translation = toMck(root);
			saveFile(translation, gamePath + ".mck");
		} catch(URISyntaxException e) { 
			e.printStackTrace();
		}catch(IOException e) { 
			e.printStackTrace();
		}
	}
	
	// TODO: add hierarchy to types
	public enum GdlType {
		ROOT, CLAUSE, HEAD, FORMULA, VARIABLE, CONSTANT
		
	}
	
	/** 
	 * Inner class that represents one node in the parse tree
	 * where the children for a formula are a list of parameters
	 */
	public static class ParseNode {
		GdlType type;
		String atom;
		ParseNode parent;
		List<ParseNode> children;

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

		public boolean distinct(String atom) {
			return !this.atom.equals(atom);
		}

		public boolean distinct(ParseNode node) {
			return !this.atom.equals(node.atom);
		}
		
		public GdlType getType(){
			return type;
		}
		
		public String getAtom(){
			return atom;
		}
		
		public ParseNode getParent(){
			return parent;
		}
		
		public List<ParseNode> getChildren(){
			return children;
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