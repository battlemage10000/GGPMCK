package translator.grammar;

import java.util.ArrayList;
import translator.OrderedTreeNode;

public class GdlTerm implements OrderedTreeNode, GdlAtom{
	
	private String atom;
	private OrderedTreeNode parent;
	private ArrayList<OrderedTreeNode> children;
	
	public GdlTerm(String atom, OrderedTreeNode parent){
		this.atom = atom;
		this.parent = parent;
		this.children = new ArrayList<OrderedTreeNode>();
	}
	
	@Override
	public String getAtom() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderedTreeNode getParent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArrayList<? extends OrderedTreeNode> getChildren() {
		// TODO Auto-generated method stub
		return null;
	}

}
