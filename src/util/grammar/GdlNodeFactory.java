package util.grammar;

public class GdlNodeFactory {

	public static Gdl createGdl() {
		return new Gdl();
	}

	public static GdlRule createGdlRule(GdlNode parent) {
		return new GdlRule(parent);
	}

	public static GdlLiteral createGdlFormula(String atom, GdlNode parent) {
		return new GdlLiteral(atom, parent);
	}

	public static GdlTerm createGdlTerm(String atom, GdlNode parent) {
		return new GdlTerm(atom, parent);
	}
}
