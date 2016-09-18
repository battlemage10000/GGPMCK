package util.grammar;

public class GdlNodeFactory {
	// private static final HashMap<String, GdlNode> termMap = new
	// HashMap<String, GdlNode>();

	public static GdlNode createGdl() {
		return new Gdl();
	}

	public static GdlNode createGdlRule(GdlNode parent) {
		return new GdlRule(parent);
	}

	public static GdlNode createGdlFormula(String atom, GdlNode parent) {
		return new GdlFormula(atom, parent);
	}

	public static GdlNode createGdlTerm(String atom, GdlNode parent) {
		// if (!termMap.containsKey(atom)) {
		// termMap.put(atom, new GdlTerm(atom, parent));
		// }
		// return termMap.get(atom);
		return new GdlTerm(atom, parent);
	}
}
