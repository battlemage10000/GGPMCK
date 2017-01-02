package util.grammar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import util.grammar.GdlNode;

public class GdlLiteral implements GdlNode, LparseNode {

	private final String atom;
	private final GdlNode parent;
	private final ArrayList<GdlNode> children;

	public GdlLiteral(String atom, GdlNode parent) {
		this.atom = atom.intern();
		this.parent = parent;
		this.children = new ArrayList<GdlNode>();
	}

	@Override
	public String getAtom() {
		return atom;
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
		if (atom.charAt(0) == '?') {
			return GdlType.VARIABLE;
		} else {
			return GdlType.FORMULA;
		}
	}

	public String toLparse() {
		StringBuilder lparse = new StringBuilder();

		switch (getType()) {
		case FORMULA:
			if (!getChildren().isEmpty()) {
				if (getAtom().equals("not")) {
					lparse.append("t1(");
				} else {
					lparse.append(getAtom() + "(");
				}
				// Parameters
				for (int i = 0; i < getChildren().size() - 1; i++) {
					lparse.append(((LparseNode) getChildren().get(i)).toLparse());
					lparse.append(", ");
				}
				lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparse());
				lparse.append(")");

				// Facts
				if (getParent().getType() == GdlType.ROOT) {
					lparse.append(".\n");
				}
			} else {
				lparse.append(getAtom());
			}
			break;
		case VARIABLE:
			lparse.append(getAtom().replace("?", "V"));
			break;
		default:
			lparse.append(getAtom());
			break;
		}

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

		switch (getType()) {
		case FORMULA:
			// base and inputs
			if (getAtom().equals(GDL_DOES) || getAtom().equals(GDL_LEGAL)) {
				lparse.append("input(");
			} else if (getAtom().equals(GDL_INIT) || getAtom().equals(GDL_TRUE) || getAtom().equals(GDL_NEXT)) {
				lparse.append("base(");
			} else if (getAtom().equals("not")) {
				lparse.append("t1(");
			} else {
				lparse.append(getAtom() + "(");
			}
			// Parameters
			for (int i = 0; i < getChildren().size() - 1; i++) {
				lparse.append(((LparseNode) getChildren().get(i)).toLparseWithBaseInput());
				lparse.append(", ");
			}
			lparse.append(((LparseNode) getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
			lparse.append(")");

			// Facts
			if (getParent().getType() == GdlType.ROOT) {
				lparse.append(".\n");
			}
			break;
		default:
			lparse.append(toLparse());
		}
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
		return atom.hashCode();
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
