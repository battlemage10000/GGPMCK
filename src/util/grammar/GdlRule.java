package util.grammar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import util.grammar.GdlNode;

public class GdlRule implements GdlNode, LparseNode {

	private int stratum;
	private final GdlNode parent;
	private final ArrayList<GdlNode> children;

	public GdlRule(GdlNode parent) {
		this.parent = parent;
		this.children = new ArrayList<GdlNode>();
		this.stratum = -1;
	}

	public int getStratum() {
		return stratum;
	}
	
	public void setStratum(int stratum) {
		this.stratum = stratum;
	}
	
	public GdlNode getHead() {
		return children.get(0);
	}

	public ArrayList<GdlNode> getBody() {
		return (ArrayList<GdlNode>) children.subList(1, children.size() - 1);
	}

	@Override
	public String getAtom() {
		return CLAUSE;
	}

	@Override
	public GdlNode getParent() {
		return parent;
	}

	@Override
	public ArrayList<GdlNode> getChildren() {
		return children;
	}

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
	public GdlType getType() {
		return GdlType.CLAUSE;
	}

	public String toLparse() {
		StringBuilder lparse = new StringBuilder();

		lparse.append(((LparseNode) getChildren().get(0)).toLparse());// head
		if (getChildren().size() > 1) {
			lparse.append(" :- ");
			for (int i = 1; i < getChildren().size() - 1; i++) {
				lparse.append(((LparseNode) getChildren().get(i)).toLparse());
				lparse.append(", ");
			}
			lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparse());
		}
		lparse.append(".\n");

		return lparse.toString();
	}

	/**
	 * Recursive method for generating lparse formatted representation of parse
	 * tree
	 * 
	 * @return String lparse of the sub-tree rooted at node
	 */
	@Override
	public String toLparseWithBaseInput() {
		StringBuilder lparse = new StringBuilder();

		lparse.append(((LparseNode) getChildren().get(0)).toLparseWithBaseInput());// head
		if (getChildren().size() > 1) {
			lparse.append(" :- ");
			for (int i = 1; i < getChildren().size() - 1; i++) {
				lparse.append(((LparseNode) getChildren().get(i)).toLparseWithBaseInput());
				lparse.append(", ");
			}
			lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
		}
		lparse.append(".\n");

		return lparse.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (!getChildren().isEmpty() && !getAtom().equals("")) {
			sb.append("(");
		}
		sb.append(getAtom());

		for (GdlNode child : getChildren()) {
			sb.append(" " + child.toString());
		}

		if (!getChildren().isEmpty() && !getAtom().equals("")) {
			sb.append(")");
		}

		return sb.toString();
	}

	@Override
	public int hashCode() {
		return getAtom().hashCode();
	}

	@Override
	public GdlNode getChild(int index) {
		return children.get(index);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this.toString().equals(obj.toString())) {
			return true;
		}
		return false;
	}
}
