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
	// Old string manipulation constants for use with String.split()
	//public static int STRING_HEAD = 0, STRING_BODY = 1, STRING_TAIL = 2;

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
				if(scopedVariable == true && parent.type == GdlType.ROOT){
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
	
	
	
	
	// Follows the domain graph def in the ggp book
	public static DomainGraph constructDomainGraph(ParseNode root){
		DomainGraph graph = new DomainGraph();
		HashMap<ParseNode, DomainGraph.Term> variableMap = new HashMap<ParseNode, DomainGraph.Term>();
		
		
		Queue<ParseNode> queue = new LinkedList<ParseNode>();
		queue.addAll(root.getChildren());
		
		while(!queue.isEmpty()){
			ParseNode node = queue.remove();
			switch(node.type){
			case ROOT:
				System.out.println("Should be impossible to reach root");
				break;
			case CLAUSE:
				break;
			case FORMULA:
				graph.addFunction(node.getAtom(), node.getChildren().size());
				for(int i=0; i<node.getChildren().size(); i++){
					if(node.getChildren().get(i).type != GdlType.VARIABLE){
						graph.addEdge(node.getAtom(), i+1, node.getChildren().get(i).getAtom(), 0);
					}else{
						ParseNode variable = node.getChildren().get(i);
						if(variableMap.containsKey(variable)){
							DomainGraph.Term varLink = variableMap.get(variable);
							graph.addEdge(varLink.getTerm(), varLink.getArity(), node.getAtom(), i+1);
						}else{
							variableMap.put(variable, new DomainGraph.Term(node.getAtom(), i+1));
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
		
		graph.addEdge("base", 1, "true", 1);
		graph.addEdge("input", 1, "does", 1);
		graph.addEdge("input", 2, "does", 2);
		
		return graph;
	}
	
	public static boolean isVariableInTree(ParseNode node){
		if(node.type == GdlType.VARIABLE){
			return true;
		}
		
		for(ParseNode child : node.getChildren()){
			if(isVariableInTree(child)){
				return true;
			}
		}
		
		return false;
	}
	
	
	public static ParseNode groundGdl(ParseNode root, DomainGraph domainGraph){
		ParseNode groundedRoot = new ParseNode();
		
		Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap = domainGraph.getDomainMap();
		Map<String, List<String>> domainGraphString = new HashMap<String, List<String>>();
		for(DomainGraph.Term term : domainMap.keySet()){
			String atom = term.getTerm();
			List<String> domain = new ArrayList<String>();
			for(DomainGraph.Term dependency : domainMap.get(term)){
				domain.add(dependency.getTerm());
			}
			domainGraphString.put(atom, domain);
		}
		
		for(ParseNode clause : root.getChildren()){
			if(!isVariableInTree(clause)){
				groundedRoot.getChildren().add(clause);
			}else{
				String groundedClauseString = groundClause(clause, domainMap);
				List<String> groundedClauseTokens = null;
				try{ 
					groundedClauseTokens = tokenizeGdl(groundedClauseString);
				}catch(IOException e){	e.printStackTrace();	}
				
				ParseNode clauseTree = expandParseTree(groundedClauseTokens);
				if(!clauseTree.getChildren().isEmpty()){
					groundedRoot.getChildren().add(clauseTree.getChildren().get(0));
				}
			}
		}
		
		return groundedRoot;
	}
	
	public static String groundClause(ParseNode clauseNode, Map<DomainGraph.Term, ArrayList<DomainGraph.Term>> domainMap){
		StringBuilder groundedClauses = new StringBuilder();
		
		Map<String, List<String>> constantMap = new HashMap<String, List<String>>();
		for(ParseNode node : clauseNode){
			if(node.type == GdlType.VARIABLE){
				if(!constantMap.containsKey(node.getAtom())){
					constantMap.put(node.getAtom(), new ArrayList<String>());
				}
				
				DomainGraph.Term varTerm = new DomainGraph.Term(node.getParent().getAtom(), node.getParent().getChildren().indexOf(node));
				if(domainMap.containsKey(varTerm)){	
					for(DomainGraph.Term term : domainMap.get(varTerm)){
						constantMap.get(node.getAtom()).add(term.getTerm());
					}
				}
			}
		}
		groundedClauses.append(groundClause(clauseNode.toString(), constantMap));
		
		return groundedClauses.toString();
	}
	
	
	/**
	 * Change sentences with variables to grounded equivalent. Takes root of
	 * parse tree and returns root of grounded tree
	 * @Deprecated
	 */
	/*public static ParseNode groundClauses(ParseNode root) {

		// Construct domain dependency map
		DependencyGraph graph = constructDependencyGraph(root);
		
		ParseNode groundedRoot = new ParseNode();
		
		for(ParseNode clause : root.getChildren()){
			if(clause.getType() == GdlType.CLAUSE && isVariableInTree(clause)){
				Map<String, List<String>> vertexToDomainMapForClause = new HashMap<String, List<String>>();
				
				Queue<ParseNode> headList = new LinkedList<ParseNode>();
				headList.add(clause.getChildren().get(0));
				while(!headList.isEmpty()){
					ParseNode headNode = headList.remove();
					
					if(headNode.type == GdlType.VARIABLE){
						Vertex<Arguments> parameter = graph.getVertex(new Arguments(headNode.getParent().getAtom(), headNode.getParent().getChildren().indexOf(headNode)));
						List<String> domainList = new ArrayList<String>();
						for(Vertex<Arguments> vertex : parameter.getDomain()){
							domainList.add(vertex.getData().getAtom());
						}
						if(!domainList.isEmpty())
							vertexToDomainMapForClause.put(headNode.getAtom(), domainList);
					}
					
					headList.addAll(headNode.getChildren());
				}
				try{
					// Huge oneliner
					// TODO: make this line more readable
					groundedRoot.getChildren().addAll(expandParseTree(tokenizeGdl(groundClause(clause.toString(), vertexToDomainMapForClause))).getChildren());
				}catch(IOException e){
					e.printStackTrace();
				}
			}else{
				groundedRoot.getChildren().add(clause);
			}
		}
		*/
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
/*
		return groundedRoot;
	}*/
	
	/**
	 * 
	 * @param root
	 * @return
	 * @Deprecated
	 */
	/*public static DependencyGraph<Arguments> constructDependencyGraph(ParseNode root) {
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
					// if map has atom then add an edge from map to new node
					variableToVertexMap.get(node.atom).addNeighbor(graph.getVertex(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
				}else {
					// if map doesn't have atom then check if new node exists then add new node to map
					if(!graph.hasVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)))){
						// add new node if doesn't exist
						graph.addVertex(new Vertex<Arguments>(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
					}
					if(node.getParent().getChildren().indexOf(node) == 0){
						variableToVertexMap.put(node.atom, graph.getVertex(new Arguments(node.parent.atom, node.parent.children.indexOf(node)+1)));
					}
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
	}*/
	
	/**
	 * Recursive method used to duplicate a subtree with a particular variable
	 * instantiated to a particular constant
	 * 
	 * @param root
	 * @param variable
	 * @param constant
	 * @return groundedRoot
	 * @Deprecated
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
	
	/**
	 * 
	 * @return string with grounded clause which can be expanded into parse tree of clause
	 * @Deprecated
	 */
	private static String groundClause(String gdlClause, Map<String, List<String>> vertexToDomainMap){
		StringBuilder groundedClauses = new StringBuilder();
		
		//TODO: find out how to iterate over all values of all lists
		
			String clause = gdlClause;
			for(String variable : vertexToDomainMap.keySet()){
				if(vertexToDomainMap.get(variable) != null && clause.contains(variable)){
					for(int i=0; i<vertexToDomainMap.get(variable).size(); i++){
						clause = clause.replace(variable, vertexToDomainMap.get(variable).get(i));
					}
				}
			}
			groundedClauses.append(clause);
		
		
		return groundedClauses.toString();
	}
	
	
	
	
	@Deprecated
	public static List<String> findRolesForMck(ParseNode root){
		ArrayList<String> roles = new ArrayList<String>();
		for(ParseNode child : root.children){
			if(child.atom.equals(GDL_ROLE)){
				roles.add(child.children.get(0).atom);
			}
		}
		return roles;
	}
	
	@Deprecated
	public static List<String> findLegalsForMck(ParseNode root){
		ArrayList<String> legals = new ArrayList<String>();
		
		for(ParseNode node : root.children){
			if(node.atom.equals("<=")){
				ParseNode child = node.children.get(0);
				if(child.atom.equals(GDL_LEGAL)){
					legals.add("legal_" + child.children.get(0).atom + "_" + child.children.get(1).toString().replace("(", "").replace(")", "").replace(" ", "_"));
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
	
	public static Map<String, List<String>> findMovesForMck(ParseNode root){
		Map<String, List<String>> roleToMoveMap = new HashMap<String, List<String>>();
		
		for(ParseNode clause : root.getChildren()){
			if(clause.getType() == GdlType.CLAUSE && (clause.getChildren().get(0).getAtom()).equals(GDL_LEGAL)){
				ParseNode legal = clause.getChildren().get(0);
				String role = legal.getChildren().get(0).toString();
				String move = legal.getChildren().get(1).toString().replace("(", "").replace(")", "").replace(" ", "_");
				
				if(!roleToMoveMap.containsKey(role)){
					roleToMoveMap.put(role, new ArrayList<String>());
				}
				
				if(!roleToMoveMap.get(role).contains(move)){
					roleToMoveMap.get(role).add(move);
				}
			}
		}
		return roleToMoveMap;
	}

	/**
	 * TODO: takes a parse tree and returns MCK equivalent
	 * TODO: rewrite to follow steps in mck paper
	 */
	public static String toMck(ParseNode root) {
		
		List<String> roles = findRolesForMck(root);
		Map<String, List<String>> roleToMoveMap = findMovesForMck(root);
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
		for (String role : roleToMoveMap.keySet()) {
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
		
		for(String role : roleToMoveMap.keySet()){
			for(String move : roleToMoveMap.get(role)){
				sb.append(System.lineSeparator() + "if Player_" + role + ".Move_" + move + " -> did_" + role + "_" + move + " := True");
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
		for(String role : roleToMoveMap.keySet()){
			sb.append(System.lineSeparator() + "protocol \""+role+"\" (");
			for(String move : roleToMoveMap.get(role)){
				sb.append(System.lineSeparator()+"  legal_" + role + "_" + move + ": Bool, ");
				sb.append(System.lineSeparator()+"  did_" + role + "_" + move + ": observable Bool, ");
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append(System.lineSeparator() + ")");
			
			sb.append(System.lineSeparator() + "begin");
			sb.append(System.lineSeparator() + "  do");
			sb.append(System.lineSeparator() + "  ");
			for(String move : roleToMoveMap.get(role)){
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
	public static void saveFile(String text, String filename){
		FileWriter writer = null;
		try{
			File file = new File(filename);
			file.createNewFile();
			
			writer = new FileWriter(file);
			writer.write(text);
			writer.flush();
			writer.close();
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			if(writer != null){
				try{
					writer.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Extracts a set of Strings which represent the vocabulary of the GDL
	 * 
	 * @deprecated
	 * @param gdl
	 * @return vocabulary
	 */
	/*private static Set<String> extractVocabulary(String gdl) {
		// Extracting atoms
		Set<String> vocabulary = new HashSet<String>();
		for (String atom : gdl.split("\\(| |\\)")) {
			vocabulary.add(atom);
		}
		vocabulary.remove("");
		return vocabulary;
	}*/

	/**
	 * Extract a set of relations in the GDL
	 * 
	 * @deprecated
	 * @param gdl
	 * @return relationsSet
	 */
	/*private static Set<String> extractRelations(String gdl) {
		// Extracting relations
		Set<String> relationsSet = new HashSet<String>();
		for (String sentence : gdl.split("\\(")) {
			relationsSet.add(sentence.split(" ")[0]);
		}
		relationsSet.remove("");
		return relationsSet;
	}*/

	/**
	 * Extract a set of variables from the GDL. Should not be used because the
	 * variableSet doesn't take scope into consideration
	 * 
	 * @deprecated
	 * @param gdl
	 * @return
	 */
	/*private static Set<String> extractVariables(String gdl) {
		Set<String> variables = new HashSet<String>();
		String[] splitString = gdl.split("\\(| |\\)");
		for (String atom : splitString) {
			if (atom.length() > 0 && atom.charAt(0) == '?') {
				variables.add(atom);
			}
		}
		return variables;
	}*/

	/**
	 * Extracts variables from the GDL in token list form. Shouldn't be used
	 * because doesn't take into consideration variable scope and allows
	 * overlaps
	 * 
	 * @deprecated
	 * @param tokens
	 * @return variables
	 */
	/*private static Set<String> variableSet(List<String> tokens) {
		Set<String> variables = new HashSet<String>();
		for (String token : tokens) {
			if (token.charAt(0) == '?') {
				variables.add(token);
			}
		}
		return variables;
	}*/

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
	 * Outputs parse tree in lparse format
	 */
	public static String toLparse(ParseNode root){
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
	
	public static void printParseTree(ParseNode root){
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
		case HEAD:
			System.out.println(prefix + "HEAD " + root.getAtom());
			break;
		case VARIABLE:
			System.out.println(prefix + "VARIABLE " + root.getAtom());
			break;
		case FORMULA:
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
	
	public static void printParseTreeTypes(ParseNode root){
		printParseTreeTypes(root, ">", " -");
	}
	
	/**
	 * Can be used from the command line by moving to the build directory and using
	 *     java translator.MckTranslator path/to/game.gdl
	 *   or
	 *     java -jar MckTranslator.jar path/to/game.gdl
	 * which will save output to path/to/game.gdl.mck
	 */
	public static void main(String[] args){
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
		
		for(String arg : args){
			switch(arg){
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
				if(outputFileToken){
					outputFilePath = arg;
					outputFileToken = false;
				}else if(inputFileToken){
					inputFilePath = arg;
					inputFileToken = false;
				}else if(!inputFileSwitch){
					inputFilePath = arg;
				}
			}
		}
		
		if(helpSwitch){
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
		}else{
			try {
				// Use either 
				List<String> tokens;
				if(inputFilePath.equals("")){
					tokens = gdlTokenizer(new InputStreamReader(System.in));
				}else{
					tokens = tokenizeFile(inputFilePath);
				}
				ParseNode root = expandParseTree(tokens);
				
				// Use internal grounder
				if(groundSwitch){
					DomainGraph domain = constructDomainGraph(root);
					if(outputDotSwitch){
						System.out.println(domain.dotEncodedGraph());
					}
					root = groundGdl(root, domain);
				}
				
				// Print parse tree for debugging
				if(parseTreeSwitch){
					printParseTree(root);
				}
				
				// Print parse tree types for debugging
				if(parseTreeTypesSwitch){
					printParseTreeTypes(root);
				}
				
				
				String translation;
				if(outputLparseSwitch){
					translation = toLparse(root);
				}else{
					translation = toMck(root);
				}
				
				
				if(outputFileSwitch){
					saveFile(translation, outputFilePath);
				}else if(!debugSwitch || outputLparseSwitch || outputMckSwitch){
					System.out.println(translation);
				}
			} catch(URISyntaxException e) { 
				e.printStackTrace();
			}catch(IOException e) { 
				e.printStackTrace();
			}
		}
	}
	
	// TODO: get rid of head keyword
	public enum GdlType {
		ROOT, CLAUSE, HEAD, FORMULA, VARIABLE, CONSTANT
		
	}
	
	/** 
	 * Inner class that represents one node in the parse tree
	 * where the children for a formula are a list of parameters
	 */
	public static class ParseNode implements Iterable<ParseNode>{
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
			return !this.atom.equals(node.getAtom());
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
		
		public String toLparse(){
			StringBuilder lparse = new StringBuilder();
			
			switch(type){
			case ROOT:
				for(ParseNode clause : getChildren()){
					lparse.append(clause.toLparse());
					if(clause.getType() == GdlType.CLAUSE){
						if(clause.getChildren().get(0).getAtom().equals(GDL_INIT) ||
							clause.getChildren().get(0).getAtom().equals(GDL_NEXT) ||
							clause.getChildren().get(0).getAtom().equals(GDL_LEGAL)){
							lparse.append(clause.toLparseWithBaseInput());
						}
					}
				}
				break;
			case CLAUSE:
				lparse.append(children.get(0).toLparse());//head
				if(children.size() > 1){
					lparse.append(" :- ");
					for(int i=1; i < children.size() - 1; i++){
						lparse.append(children.get(i).toLparse());
						lparse.append(", ");
					}
					lparse.append(children.get(children.size() - 1).toLparse());	
				}
				lparse.append(".\n");
				break;
			case FORMULA:
				//base and inputs
				/*if(getAtom().equals(GDL_DOES) || getAtom().equals(GDL_LEGAL)){
					lparse.append("input(");
				}else if(getAtom().equals(GDL_INIT) || getAtom().equals(GDL_TRUE) || getAtom().equals(GDL_NEXT)){
					lparse.append("base(");
				}else*/ if(getAtom().equals("not")){
					lparse.append("t1(");
				}else{
					lparse.append(getAtom()+"(");
				}
				//Parameters
				for(int i=0; i < children.size() - 1; i++){
					lparse.append(children.get(i).toLparse());
					lparse.append(", ");
				}
				lparse.append(children.get(children.size() - 1).toLparse());
				lparse.append(")");
				
				//Facts
				if(getParent().getType() == GdlType.ROOT){
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
		 * Recursive method for generating lparse formatted representation of parse tree
		 * @return String lparse of the sub-tree rooted at node
		 */
		private String toLparseWithBaseInput(){
			StringBuilder lparse = new StringBuilder();
			
			switch(type){
			case CLAUSE:
				lparse.append(children.get(0).toLparseWithBaseInput());//head
				if(children.size() > 1){
					lparse.append(" :- ");
					for(int i=1; i < children.size() - 1; i++){
						lparse.append(children.get(i).toLparseWithBaseInput());
						lparse.append(", ");
					}
					lparse.append(children.get(children.size() - 1).toLparseWithBaseInput());	
				}
				lparse.append(".\n");
				break;
			case FORMULA:
				//base and inputs
				if(getAtom().equals(GDL_DOES) || getAtom().equals(GDL_LEGAL)){
					lparse.append("input(");
				}else if(getAtom().equals(GDL_INIT) || getAtom().equals(GDL_TRUE) || getAtom().equals(GDL_NEXT)){
					lparse.append("base(");
				}else if(getAtom().equals("not")){
					lparse.append("t1(");
				}else{
					lparse.append(getAtom()+"(");
				}
				//Parameters
				for(int i=0; i < children.size() - 1; i++){
					lparse.append(children.get(i).toLparseWithBaseInput());
					lparse.append(", ");
				}
				lparse.append(children.get(children.size() - 1).toLparseWithBaseInput());
				lparse.append(")");
				
				//Facts
				if(getParent().getType() == GdlType.ROOT){
					lparse.append(".\n");
				}
				break;
			default:
				lparse.append(toLparse());
			}
			return lparse.toString();
		}
		
		public Iterator<ParseNode> iterator(){
			Queue<ParseNode> iterator = new LinkedList<ParseNode>();
			
			iterator.add(this);
			for(ParseNode child : getChildren()){
				for(ParseNode node : child){
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