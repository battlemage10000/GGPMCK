package util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import prover.GdlRuleSet;
import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
import util.grammar.GdlNode;
import util.grammar.GdlNodeFactory;
import util.grammar.GdlRule;
import util.grammar.GdlType;
import util.grammar.LparseNode;
import util.graph.DependencyGraph;
import util.graph.DomainGraph;

/**
 * Essential utility methods for parsing gdl
 * 
 * @author vedantds
 *
 */
public class GdlParser {
	
	public static boolean GROUND_WITH_TEMP_FILES = false;

	public final static char OPEN_P_Char = '(';// block/scope
	public final static char CLOSE_P_Char = ')';// block/scope
	public final static char SPACE = ' ';// delimiter
	public final static char TAB = '\t';// delimiter
	public final static char SEMICOLON = ';';// comment start
	public final static char NEW_LINE = '\n';// comment end
	public final static char RETURN = '\r';// comment end
	public final static char Q_MARK_Char = '?';

	public final static String OPEN_P_Str = "(";
	public final static String CLOSE_P_Str = ")";
	public final static String Q_MARK_Str = "?";
	public final static String UNDERSCORE = "_"; // underscore
	public final static String TRUE_PREFIX = ("true" + UNDERSCORE).intern();
	public final static String OLD_SUFFIX = (UNDERSCORE + "old").intern();

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
			case OPEN_P_Char:
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString().intern());
				}
				if (!comment) {
					tokens.add(OPEN_P_Str.intern());
				}
				sb = new StringBuilder();
				break;
			case CLOSE_P_Char:
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString().intern());
				}
				if (!comment) {
					tokens.add(CLOSE_P_Str.intern());
				}
				sb = new StringBuilder();
				break;
			case SPACE:
			case TAB:
				// whitespace
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString().intern());
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

		// Case for input with only one variable(partial gdl parsing)
		if (tokens.isEmpty() && sb.length() > 0 && !comment && sb.charAt(0) != OPEN_P_Char
				&& sb.charAt(sb.length() - 1) != CLOSE_P_Char) {
			tokens.add(sb.toString().intern());
		}

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
	 * Takes a list of tokens and produces a parse tree. Returns the root node
	 * of the tree.
	 *
	 * @param tokens
	 * @return
	 */
	public static Gdl expandParseTree(List<String> tokens) {
		Gdl root = GdlNodeFactory.createGdl();

		GdlNode parent = root;
		boolean openBracket = false;
		boolean scopedVariable = false;
		int scopeNumber = 1;
		for (String token : tokens) {
			switch (token) {
			case OPEN_P_Str:
				openBracket = true;
				break;
			case CLOSE_P_Str:
				parent.getChildren().trimToSize();
				parent = parent.getParent();
				if (scopedVariable == true && parent.getType() == GdlType.ROOT) {
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case GdlNode.CLAUSE:
				GdlNode newNode = GdlNodeFactory.createGdlRule(parent);
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				if (token.charAt(0) == Q_MARK_Char) {
					scopedVariable = true;
					token = (Q_MARK_Str + scopeNumber + UNDERSCORE + token).intern();
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

	
	public static GdlRuleSet expandRuleSet(List<String> tokens) {
		GdlRuleSet ruleSet = new GdlRuleSet();
		
		for (String token : tokens) {
			StringBuilder literal = new StringBuilder();
			int bracketLevel = 0;
			boolean isHead = true;
			String workingHead;
			Set<String> workingBody = null;
			
			switch(token){
			case OPEN_P_Str:
				bracketLevel++;
				break;
			case CLOSE_P_Str:
				bracketLevel--;
				if (bracketLevel == 0){
					if (isHead){
						if (ruleSet.getRuleSet().get(literal.toString()) == null) {
							ruleSet.getRuleSet().put(literal.toString(), new HashSet<Set<String>>());
						}
						workingHead = literal.toString();
						isHead = false;
					} else {
						if (workingBody == null) {
							workingBody = new HashSet<String>();
						}
						workingBody.add(literal.toString());
					}
					literal = new StringBuilder();
				} else if (bracketLevel < 0) {
					bracketLevel = 0;
					isHead = true;
				}
				break;
			case GdlNode.CLAUSE:
				bracketLevel = 0;
				isHead = true;
				break;
			default:
				if (literal.length() == 0) {
					
				} else {
					literal.append(token);
				}
			}
		}
		return ruleSet;
	}
	
	/**
	 * Overloaded method which doesn't require casting to Reader for game
	 * descriptions in Files
	 * 
	 * @param filePath
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static Gdl parseFile(String filePath) throws IOException, URISyntaxException {
		return expandParseTree(tokenizeFile(filePath));
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for game
	 * descriptions in Strings
	 * 
	 * @param gdl
	 * @return
	 * @throws IOException
	 */
	public static Gdl parseString(String gdl) {
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
	@Deprecated
	public static DependencyGraph constructDependencyGraph(GdlNode root) {
		DependencyGraph graph = new DependencyGraph();

		for (GdlNode node : root) {
			// if (node.getType() == GdlType.CLAUSE) {
			if (node instanceof GdlRule) {
				String headNodeString = node.getChild(0).getAtom();
				switch (headNodeString) {
				case GdlNode.BASE:
				case GdlNode.INPUT:
					break;
				// Skip base and input clauses
				case GdlNode.NEXT:
					headNodeString = TRUE_PREFIX + formatGdlNode(node.getChild(0).getChild(0));
				default:
					for (int i = 1; i < node.getChildren().size(); i++) {
						boolean isNextTrue = false;
						GdlNode toNode = node.getChildren().get(i);
						while (toNode.getAtom().equals(GdlNode.NOT) || toNode.getAtom().equals(GdlNode.TRUE)
								|| toNode.getAtom().equals(GdlNode.NEXT)) {
							if (toNode.getAtom().equals(GdlNode.TRUE) || toNode.getAtom().equals(GdlNode.NEXT)) {
								isNextTrue = true;
							}
							toNode = toNode.getChildren().get(0);
						}
						String toNodeString = toNode.getAtom();
						if (isNextTrue) {
							toNodeString = TRUE_PREFIX + formatGdlNode(toNode);
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
	 * Format gdl node to graphviz syntax
	 * 
	 * @param node
	 * @return
	 */
	public static String formatGdlNode(GdlNode node) {
		StringBuilder sb = new StringBuilder();
		sb.append(node.getAtom());
		if (!node.getChildren().isEmpty()) {
			for (GdlNode child : node.getChildren()) {
				sb.append(UNDERSCORE + formatGdlNode(child));
			}
		}
		return sb.toString();
	}

	/**
	 * Construct a graph that is used to derive the domain of parameters in
	 * literals and terms. Follows the domain graph definition in the ggp book
	 * 
	 * @param root
	 * @return
	 */
	public static DomainGraph constructDomainGraph(GdlNode root) {
		DomainGraph graph = new DomainGraph();
		HashMap<String, DomainGraph.Term> variableMap = new HashMap<String, DomainGraph.Term>();
		
		for (GdlNode node : root) {
			if ((node.getType() == GdlType.FUNCTION || node.getType() == GdlType.FORMULA)
					&& !node.getAtom().equals(GdlNode.NOT)) {
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

		
		graph.addEdge(GdlNode.BASE, 1, GdlNode.TRUE, 1);
		graph.addEdge(GdlNode.INPUT, 1, GdlNode.DOES, 1);
		graph.addEdge(GdlNode.INPUT, 2, GdlNode.DOES, 2);
		
		/*
		long totalRules = 0;
		for (GdlNode clause : root.getChildren()) {
			int resultingRules = 1;
			for (GdlNode variable : variablesInTree(clause)) {
				resultingRules *= getVariableDomain(variable.getAtom(), clause, graph).size();
			}
			totalRules += resultingRules;
		}
		System.out.println("Ground from " + root.getChildren().size() + " -> " + totalRules);
		*/
		return graph;
	}

	/**
	 * Check if there is a variable in the sub-tree rooted at node
	 * 
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
	 * Find the number of variables in sub-tree rooted at node.
	 * Note: Same variable can be counted multiple times.
	 * 
	 * @param node
	 * @return
	 */
	public static int numVariablesInTree(GdlNode node){
		if (node.getType() == GdlType.VARIABLE) {
			return 1;
		} else if (node.getType() == GdlType.CONSTANT){
			return 0;
		}
		int varsInSubTree = 0;
		for (GdlNode child : node.getChildren()) {
			varsInSubTree += numVariablesInTree(child);
		}
		return varsInSubTree;
	}
	
	/**
	 * Find the number of variables in sub-tree rooted at node.
	 * Note: Same variable can be counted multiple times.
	 * 
	 * @param node
	 * @return
	 */
	public static Set<GdlNode> variablesInTree(GdlNode node){
		if (node.getType() == GdlType.VARIABLE) {
			return new HashSet<GdlNode>(Collections.singleton(node));
		} else if (node.getType() == GdlType.CONSTANT){
			return Collections.emptySet();
		}
		Set<GdlNode> varsInSubTree = new HashSet<GdlNode>();
		for (GdlNode child : node.getChildren()) {
			varsInSubTree.addAll(variablesInTree(child));
		}
		return varsInSubTree;
	}

	/**
	 * Ground a game description. Replaces all variable clauses with grounded
	 * versions and output directly into a GdlRuleSet object.
	 * 
	 * @param root the root of a GdlNode tree
	 * @param graph the DomainGraph of a GdlNode tree
	 * @return groundedRuleSet the GdlRuleSet with grounded rules
	 * @throws IOException
	 * @throws GDLSyntaxException
	 */
	public static GdlRuleSet groundGdlToRuleSet(GdlNode root, DomainGraph domainGraph) throws GDLSyntaxException {
		Gdl groundedRoot = GdlNodeFactory.createGdl();
		GdlRuleSet groundedRuleSet = new GdlRuleSet(GdlNodeFactory.createGdl(), false);

		Map<String, Set<String>> variableDomainMap = new HashMap<String, Set<String>>();
		for (GdlNode clause : root.getChildren()) {
			if (!isVariableInTree(clause)) { // Variable-free so already ground
				groundedRoot.getChildren().add(clause);
				groundedRuleSet.joinRuleSet(groundedRoot);
				groundedRoot = GdlNodeFactory.createGdl();
			} else {
				variableDomainMap = new HashMap<String, Set<String>>();
				for (GdlNode variable : variablesInTree(clause)) {
					variableDomainMap.put(variable.getAtom(),
							getVariableDomain(variable.getAtom(), clause, domainGraph));
				}
				String groundedClauseString = groundClause(clause, variableDomainMap, false);

				// Default root node if parseString throws error
				GdlNode clauseTree = GdlNodeFactory.createGdl();
				clauseTree = GdlParser.parseString(groundedClauseString);
				if (!clauseTree.getChildren().isEmpty()) {
					groundedRuleSet.joinRuleSet((Gdl) clauseTree);
				}
			}
		}
		
		return groundedRuleSet;
	}
	
	/**
	 * Ground a game description
	 * 
	 * @param root
	 * @param domainGraph
	 * @return groundedRoot the root node of the grounded parse tree
	 */
	public static Gdl groundGdl(GdlNode root, DomainGraph domainGraph) {
		Gdl groundedRoot = GdlNodeFactory.createGdl();
		Map<String, Set<String>> variableDomainMap = new HashMap<String, Set<String>>();
		
		for (GdlNode clause : root.getChildren()) {
			if (!isVariableInTree(clause)) { // No variables so already ground
				groundedRoot.getChildren().add(clause);
			} else {
				variableDomainMap = new HashMap<String, Set<String>>();
				for (GdlNode variable : variablesInTree(clause)) {
					variableDomainMap.put(variable.getAtom(),
							getVariableDomain(variable.getAtom(), clause, domainGraph));
				}
				String groundedClauseString = groundClause(clause, variableDomainMap, false);

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
	 * Get the set of terms in the domain of the provided variable name within the scope of the provided clause.
	 * 
	 * @param variable the variable we want the domain of
	 * @param clause the clause that provides the scope of the variable
	 * @param graph the domain graph
	 * @return variableDomainSet
	 */
	public static Set<String> getVariableDomain(String variable, GdlNode clause, DomainGraph graph) {
		Set<String> variableDomainSet = new HashSet<String>();
		for (GdlNode node : clause) {
			if (node.getType() == GdlType.VARIABLE && node.getAtom().equals(variable)) {
				//boolean hasMultiVarInstance = !variableDomainSet.isEmpty();
				
				for (DomainGraph.Term term : graph.getDomain(node.getParent().getAtom(),
						node.getParent().getChildren().indexOf(node) + 1)) {
					//if (hasMultiVarInstance && !term.getTerm().equals(GdlNode.DISTINCT)
					//		&& !variableDomainSet.contains(term.getTerm())) {
						//System.out.println("InconsistentDomainException: Const: " + term.getTerm()
						//		+ " not in domain of var: " + node.getAtom());
					//}
					variableDomainSet.add(term.getTerm());
				}
				if (!clause.toString().contains(GdlNode.DISTINCT)) {
					break;
				}
			}
		}
		return variableDomainSet;
	}

	/**
	 * Ground a clause in a game description
	 * 
	 * @param clauseNode the clause to be grounded
	 * @param constantMap the map from variable to domain of variable
	 * @return groundedClauses the set of ground clauses as a string to be parsed with the parseString(String) method
	 */
	public static String groundClause(GdlNode clauseNode, Map<String, Set<String>> constantMap, boolean useTempFile) {
		// Duplicate method signature error
		StringBuilder groundedClauses = new StringBuilder();
		groundedClauses.append(clauseNode.toString());
		
		PriorityQueue<String> orderedVars = new PriorityQueue<String>(new Comparator<String>() {
			public int compare(String str1, String str2) {
				return str2.length() - str1.length();
			}
		});

		orderedVars.addAll(constantMap.keySet());
		while (!orderedVars.isEmpty()) {
			String variable = orderedVars.poll();
			String varTerm = groundedClauses.toString();
			groundedClauses.replace(0, groundedClauses.length(), "");
			for (String constant : constantMap.get(variable)) {
				groundedClauses.append(varTerm.replace(variable, constant));
			}
		}
		return groundedClauses.toString();
	}

	/**
	 * Ground a clause in a game description
	 * 
	 * @param clauseNode
	 * @param domainMap
	 * @return
	 */
	@Deprecated
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

		Queue<String> subClauses = new ArrayDeque<String>();
		Queue<String> subClausesAlt = new ArrayDeque<String>();
		subClausesAlt.add(clauseNode.toString());
		for (String variable : constantMap.keySet()) {
			subClauses = subClausesAlt;
			subClausesAlt = new ArrayDeque<String>();

			List<String> domain = constantMap.get(variable);

			while (!subClauses.isEmpty()) {
				String subClause = subClauses.remove();
				for (String term : domain) {
					String nextTerm = subClause.replace(variable, term);
					if (nextTerm.contains(Q_MARK_Str)) {
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
	 * Use a priority list to order by stratum
	 * 
	 * @param root
	 * @return
	 */
	@Deprecated
	public static String orderGdlRules(GdlNode root) {
		DependencyGraph graph = constructDependencyGraph(root);
		graph.computeStratum();
		return orderGdlRules(root, graph);
	}

	/**
	 * Use a priority list to order by stratum.
	 * 
	 * @param root
	 * @param graph
	 * @return
	 */
	public static String orderGdlRules(GdlNode root, DependencyGraph graph) {

		PriorityQueue<GdlNode> unordered = new PriorityQueue<GdlNode>(100, new GdlHeadComparator());
		for (GdlNode clause : root.getChildren()) {
			if (clause instanceof GdlRule) {
				if (clause.getChild(0).getAtom().equals(GdlNode.NEXT)) {
					((GdlRule) clause).setStratum(graph.getStratum(
							TRUE_PREFIX + formatGdlNode(clause.getChild(0).getChild(0))));
				} else {
					((GdlRule) clause).setStratum(graph.getStratum(clause.getChild(0).getAtom()));
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

		lparse.append(System.lineSeparator() + "% Generate");
		lparse.append(System.lineSeparator() + "{true(V1):base(V1)}.");
		lparse.append(System.lineSeparator() + "1={does(V2, V3):input(V2, V3)} :- role(V2).");
		lparse.append(System.lineSeparator());
		lparse.append(System.lineSeparator() + "% Define");
		lparse.append(System.lineSeparator() + ((LparseNode) root).toLparse());
		lparse.append(System.lineSeparator());
		lparse.append(System.lineSeparator() + "% Display");

		return lparse.toString();
	}

	/**
	 * Print the names of the nodes of the tree using the getAtom() method
	 * 
	 * @param root
	 * @param prefix
	 * @param indent
	 * @return
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
	 * Print the names of the nodes of the tree using the getAtom() method
	 * 
	 * @param root
	 * @return
	 */
	public static String printParseTree(GdlNode root) {
		return printParseTree(root, ">", " -");
	}

	/**
	 * Print the GdlType of the nodes of the tree. Type is based on the value of
	 * GdlNode.getType()
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
			sb.append(System.lineSeparator() + prefix + "VARIABLE " + root.getAtom());
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
	 * Print the GdlType of the nodes of the tree. Type is based on the value of
	 * GdlNode.getType()
	 * 
	 * @param root
	 */
	public static String printParseTreeTypes(GdlNode root) {
		return printParseTreeTypes(root, ">", " -");
	}

	// Syntactic sugar to help readability
	@Deprecated
	public static GdlNode getRuleHead(GdlRule rule) {
		return rule.getChildren().get(0);
	}

	/**
	 * Output parse tree as a String in kif format
	 * 
	 * @param root
	 */
	public static String prettyPrint(GdlNode root) {
		StringBuilder sb = new StringBuilder();
		for (GdlNode clause : root.getChildren()) {
			if (clause instanceof GdlRule) {
				for (GdlNode literal : clause.getChildren()) {
					if (literal == clause.getChild(0)) {
						sb.append("(<= " + literal.toString());
					} else if (literal == clause.getChildren().get(clause.getChildren().size() - 1)) {
						sb.append(" " + literal.toString() + ")");
					} else {
						sb.append(" " + literal.toString());
					}
				}
			} else {
				sb.append(clause);
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	/**
	 * Comparator used to order clauses in a game description, first by stratum
	 * and then by name to group clauses with the same name together
	 * 
	 * @author vedantds
	 *
	 */
	public static class GdlHeadComparator implements Comparator<GdlNode> {
		@Override
		public int compare(GdlNode fromHead, GdlNode toHead) {
			if (fromHead instanceof GdlRule) {
				if (toHead instanceof GdlRule) {
					int stratDiff = ((GdlRule) fromHead).getStratum() - ((GdlRule) toHead).getStratum();
					if (stratDiff != 0) {
						return stratDiff;
					} else {
						return getRuleHead((GdlRule) fromHead).toString()
								.compareTo(getRuleHead((GdlRule) toHead).toString());
					}
				} else {
					return 1;
				}
			} else if (toHead instanceof GdlRule) {
				return -1;
			} else {
				return fromHead.toString().compareTo(toHead.toString());
			}
		}
	};
}
