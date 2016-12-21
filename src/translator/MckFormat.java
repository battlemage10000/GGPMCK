package translator;

import util.GdlParser;
import util.grammar.GdlNode;

public class MckFormat {

	public static String INIT = "INIT".intern();
	public static String STOP = "STOP".intern();
	public static String NOT = "neg".intern();
	public static String ROLE_PREFIX = "R_";
	public static String MOVE_PREFIX = "M_";
	public static String DOES_PREFIX = "did_";
	public static String ACTION_PREFIX = "Act_";
	public static String TRUE_PREFIX = GdlParser.TRUE_PREFIX; // "true_";
	public static String OLD_SUFFIX = "_old";
	public static String AND = " /\\ ";
	public static String OR = " \\/ ";
	public static String UNDERSCORE = "_";
	public static String TRUE = "True".intern();
	public static String FALSE = "False".intern();

	public boolean ONE_LINE_TRANSITIONS = true;
	public boolean DEBUG = true;

	/**
	 * Converts GdlNode to mck readable format
	 * @param node
	 * @return
	 */
	public static String formatMckNode(GdlNode node) {
		StringBuilder nodeString = new StringBuilder();
		
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			nodeString.append(DOES_PREFIX + formatMckNode(node.getChild(0)));
			nodeString.append(" == " + MOVE_PREFIX + formatMckNode(node.getChild(1)) + UNDERSCORE + formatMckNode(node.getChild(0)));
		} else {
			while(node.getAtom().contentEquals(GdlNode.GDL_NOT) ||
					node.getAtom().contentEquals(GdlNode.GDL_INIT) ||
					node.getAtom().contentEquals(GdlNode.GDL_TRUE) ||
					node.getAtom().contentEquals(GdlNode.GDL_NEXT)){
				
				if (node.getAtom().equals(GdlNode.GDL_NOT)) {
					nodeString.append(NOT + " ");
				}
				node = node.getChild(0);
			}
			
			nodeString.append(formatMckNodeAbs(node));	
		}
		return nodeString.toString().intern();
	}

	/**
	 * Absolute version of format node which doesn't do any manipulation
	 * 
	 * @param node
	 * @return
	 */
	public static String formatMckNodeAbs(GdlNode node) {
		StringBuilder nodeString = new StringBuilder();

		nodeString.append(formatSpecialCharacters(node.getAtom()));
		
		for (GdlNode child : node.getChildren()) {
			nodeString.append(GdlParser.UNDERSCORE + formatMckNodeAbs(child));
		}
		return nodeString.toString();
	}
	
	/**
	 * Method for handling character which are special in mck
	 * TODO: add missing special characters
	 * @param string
	 * @return
	 */
	public static String formatSpecialCharacters(String string){
		if (string.contains("+")){
			string.replace("+", "plus");
		}
		return string;
	}
}
