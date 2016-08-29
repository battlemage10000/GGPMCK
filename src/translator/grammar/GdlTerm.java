package translator.grammar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import translator.LparseNode;
import translator.MckTranslator;
import translator.MckTranslator.GdlType;

public class GdlTerm implements GdlNode, LparseNode{
	
	private String atom;
	private GdlNode parent;
	private ArrayList<GdlNode> children;
	
	public GdlTerm(String atom, GdlNode parent){
		this.atom = atom;
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
		if(atom.charAt(0) == '?'){
			return GdlType.VARIABLE;
		}else if(children.isEmpty()){
			return GdlType.CONSTANT;
		}else{
			return GdlType.FUNCTION;
		}
	}
	
	public String toLparse() {
		StringBuilder lparse = new StringBuilder();

		switch (getType()) {
		case ROOT:
			for (GdlNode clause : getChildren()) {
				lparse.append(((LparseNode)clause).toLparse());
				if (clause.getType() == GdlType.CLAUSE) {
					if (clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_INIT)
							|| clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_NEXT)
							|| clause.getChildren().get(0).getAtom().equals(MckTranslator.GDL_LEGAL)) {
						lparse.append(((LparseNode)clause).toLparseWithBaseInput());
					}
				}
			}
			break;
		case CLAUSE:
			lparse.append(((LparseNode)getChildren().get(0)).toLparse());// head
			if (getChildren().size() > 1) {
				lparse.append(" :- ");
				for (int i = 1; i < getChildren().size() - 1; i++) {
					lparse.append(((LparseNode)getChildren().get(i)).toLparse());
					lparse.append(", ");
				}
				lparse.append(((LparseNode)getChildren().get(getChildren().size() - 1)).toLparse());
			}
			lparse.append(".\n");
			break;
		case FUNCTION:
			if (getAtom().equals("not")) {
				lparse.append("t1(");
			} else {
				lparse.append(getAtom() + "(");
			}
			// Parameters
			for (int i = 0; i < getChildren().size() - 1; i++) {
				lparse.append(((LparseNode)getChildren().get(i)).toLparse());
				lparse.append(", ");
			}
			lparse.append(((LparseNode)getChildren().get(getChildren().size() - 1)).toLparse());
			lparse.append(")");

			// Facts
			if (getParent().getType() == GdlType.ROOT) {
				lparse.append(".\n");
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
	 * Recursive method for generating lparse formatted representation of
	 * parse tree
	 * 
	 * @return String lparse of the sub-tree rooted at node
	 */
	@Override
	public String toLparseWithBaseInput() {
		StringBuilder lparse = new StringBuilder();

		switch (getType()) {
		case CLAUSE:
			lparse.append(((LparseNode)getChildren().get(0)).toLparseWithBaseInput());// head
			if (getChildren().size() > 1) {
				lparse.append(" :- ");
				for (int i = 1; i < getChildren().size() - 1; i++) {
					lparse.append(((LparseNode)getChildren().get(i)).toLparseWithBaseInput());
					lparse.append(", ");
				}
				lparse.append(((LparseNode)getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
			}
			lparse.append(".\n");
			break;
		case FUNCTION:
			// base and inputs
			if (getAtom().equals(MckTranslator.GDL_DOES) || getAtom().equals(MckTranslator.GDL_LEGAL)) {
				lparse.append("input(");
			} else if (getAtom().equals(MckTranslator.GDL_INIT) || getAtom().equals(MckTranslator.GDL_TRUE) || getAtom().equals(MckTranslator.GDL_NEXT)) {
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
			lparse.append(((LparseNode)getChildren().get(getChildren().size() - 1)).toLparseWithBaseInput());
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
	public int hashCode(){
		return atom.hashCode();
	}
}
