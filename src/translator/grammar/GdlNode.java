package translator.grammar;

import translator.MckTranslator.GdlType;

import java.util.ArrayList;

public interface GdlNode extends Iterable<GdlNode> {
	public String getAtom();

	public GdlType getType();

	public GdlNode getParent();

	public ArrayList<GdlNode> getChildren();
}
