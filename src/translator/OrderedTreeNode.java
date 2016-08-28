package translator;

import java.util.ArrayList;

public interface OrderedTreeNode {
	
	public OrderedTreeNode getParent();
	public ArrayList<? extends OrderedTreeNode> getChildren();
}
