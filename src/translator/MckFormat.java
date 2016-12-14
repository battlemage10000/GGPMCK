package translator;

import util.GdlParser;
import util.grammar.GdlNode;

public class MckFormat {

	public static String INIT = "INIT".intern();
	public static String STOP = "STOP".intern();
	public static String ROLE_PREFIX = "R_";
	public static String MOVE_PREFIX = "M_";
	public static String DOES_PREFIX = "did_";
	public static String ACTION_PREFIX = "Act_";
	public static String TRUE_PREFIX = GdlParser.TRUE_PREFIX; // "true_";
	public static String OLD_SUFFIX = "_old";
	public static String AND = " /\\ ";
	public static String OR = " \\/ ";
	public static String TRUE = "True".intern();
	public static String FALSE = "False".intern();

	public boolean ONE_LINE_TRANSITIONS = true;
	public boolean DEBUG = true;

	/**
	 * @param node
	 * @return
	 */
	public static String formatMckNode(GdlNode node) {
		StringBuilder nodeString = new StringBuilder();
		if (node.getAtom().equals(GdlNode.GDL_DOES)) {
			nodeString.append(DOES_PREFIX + node.getChildren().get(0));
			nodeString.append(" == " + MOVE_PREFIX + formatMckNode(node.getChildren().get(1)));
		} else {
			if (node.getAtom().equals(GdlNode.GDL_NOT)) {
				nodeString.append("neg ");
				node = node.getChildren().get(0);
			} else if (node.getAtom().equals(GdlNode.GDL_TRUE) || node.getAtom().equals(GdlNode.GDL_NEXT)) {

			//}
			
			//nodeString.append(formatMckNodeAbs(node));
			
			} else if (node.getAtom().contains("+")) {
				nodeString.append(node.getAtom().replace("+", "plus") + GdlParser.UNDERSCORE);
			} else {
				nodeString.append(node.getAtom() + GdlParser.UNDERSCORE);
			}
			for (GdlNode child : node.getChildren()) {
				nodeString.append(formatMckNodeAbs(child) + GdlParser.UNDERSCORE);
			}
			nodeString.deleteCharAt(nodeString.length() - 1);
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

		// if (node.getAtom().equals(GdlNode.GDL_NOT)) {
		// nodeString.append("neg ");
		// } else
		// if (node.getAtom().equals(GdlNode.GDL_TRUE) ||
		// node.getAtom().equals(GdlNode.GDL_NEXT)) {
		//
		// } else
		if (node.getAtom().contains("+")) {
			nodeString.append(node.getAtom().replace("+", "plus") + "_");
		} else {
			nodeString.append(node.getAtom() + GdlParser.UNDERSCORE);
		}
		for (GdlNode child : node.getChildren()) {
			nodeString.append(formatMckNodeAbs(child) + GdlParser.UNDERSCORE);
		}
		nodeString.deleteCharAt(nodeString.length() - 1);
		return nodeString.toString();
	}
}
