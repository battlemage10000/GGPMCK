package util.grammar;

public class GdlNodeFactory {

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
		return new GdlTerm(atom, parent);
	}
}
