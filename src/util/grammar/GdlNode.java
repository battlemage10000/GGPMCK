package util.grammar;

import java.util.ArrayList;

public interface GdlNode extends Iterable<GdlNode> {

	public enum GdlType {
		ROOT, CLAUSE, FORMULA, FUNCTION, CONSTANT, VARIABLE
	}

	public static final String GDL_ROLE = "role";
	public static final String GDL_LEGAL = "legal";
	public static final String GDL_DOES = "does";
	public static final String GDL_INIT = "init";
	public static final String GDL_TRUE = "true";
	public static final String GDL_NEXT = "next";
	public static final String GDL_SEES = "sees";
	public static final String GDL_CLAUSE = "<=";
	public static final String GDL_NOT = "not";
	public static final String GDL_BASE = "base";
	public static final String GDL_INPUT = "input";
	public static final String GDL_DISTINCT = "distinct";

	public String getAtom();

	public GdlType getType();

	public GdlNode getParent();

	public ArrayList<GdlNode> getChildren();
}
