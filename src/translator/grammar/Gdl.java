package translator.grammar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import translator.LparseNode;
import translator.MckTranslator;
import translator.MckTranslator.GdlType;

public class Gdl implements GdlNode, LparseNode {
	public final ArrayList<GdlNode> children = new ArrayList<GdlNode>();

	@Override
	public Iterator<GdlNode> iterator() {
		Queue<GdlNode> iterator = new LinkedList<GdlNode>();

		iterator.add(this);
		for (GdlNode child : getChildren()) {
			for (GdlNode node : child) {
				iterator.add(node);
			}
		}

		return iterator.iterator();
	}

	@Override
	public String toLparse() {
		StringBuilder lparse = new StringBuilder();
		
		for (GdlNode clause : getChildren()) {
			lparse.append(((LparseNode) clause).toLparse());
			if (clause.getType() == GdlType.CLAUSE) {
				if (clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_INIT)
						|| clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_NEXT)
						|| clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_LEGAL)) {
					lparse.append(((LparseNode) clause).toLparseWithBaseInput());
				}
			}
		}
		
		return lparse.toString();
	}

	@Override
	public String toLparseWithBaseInput() {
		return toLparse();
	}

	@Override
	public String getAtom() {
		return "";
	}

	@Override
	public GdlType getType() {
		return GdlType.ROOT;
	}

	@Override
	public GdlNode getParent() {
		return null;
	}

	@Override
	public ArrayList<GdlNode> getChildren() {
		return children;
	}

	@Override
	public int hashCode() {
		return getAtom().hashCode();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (GdlNode node : getChildren()) {
			sb.append(System.lineSeparator() + node.toString());
		}
		return sb.toString();
	}
}
