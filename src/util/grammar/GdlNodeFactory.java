package util.grammar;

public class GdlNodeFactory {

	public static Gdl createGdl() {
		return new Gdl();
	}

	public static GdlRule createGdlRule(GdlNode parent) {
		return new GdlRule(parent);
	}

	public static GdlFormula createGdlFormula(String atom, GdlNode parent) {
		return new GdlFormula(atom, parent);
	}

	public static GdlTerm createGdlTerm(String atom, GdlNode parent) {
		return new GdlTerm(atom, parent);
	}
}
