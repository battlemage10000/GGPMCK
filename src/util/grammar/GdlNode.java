package util.grammar;

import java.util.ArrayList;

public interface GdlNode extends Iterable<GdlNode> {

	public static final String ROLE = "role";
	public static final String LEGAL = "legal";
	public static final String DOES = "does";
	public static final String INIT = "init";
	public static final String TRUE = "true";
	public static final String NEXT = "next";
	public static final String SEES = "sees";
	public static final String CLAUSE = "<=";
	public static final String NOT = "not";
	public static final String BASE = "base";
	public static final String INPUT = "input";
	public static final String DISTINCT = "distinct";

	public String getAtom();

	public GdlType getType();

	public GdlNode getParent();

	public ArrayList<GdlNode> getChildren();
	
	public GdlNode getChild(int index);
}