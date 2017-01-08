package prover;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import util.grammar.GdlLiteral;
import util.grammar.GdlNode;
import util.grammar.GdlRule;

public class Model {

	private final Set<String> model;

	public Model(Set<String> trueLiterals) {
		this.model = trueLiterals;
	}

	public Model() {
		model = new HashSet<String>();
	}

	public Set<String> getModel() {
		return model;
	}

	public void addVariable(String trueLiteral) {
		model.add(trueLiteral);
	}

	public boolean contains(String literal) {
		boolean isNegative = false;
		while (literal.substring(0, Prover.NOT_PREFIX.length()).equals(Prover.NOT_PREFIX)) {
			literal = literal.substring(Prover.NOT_PREFIX.length());
			isNegative = !isNegative;
		}
		if (model.contains(literal)) {
			if (isNegative) {
				return false;
			} else {
				return true;
			}
		} else {
			if (isNegative) {
				return true;
			} else {
				return false;
			}
		}
	}

	public boolean contains(GdlLiteral literal) {
		if (literal.getAtom().equals(GdlNode.GDL_NOT)) {
			GdlNode nonNegative = literal;
			boolean isNegative = false; // double negatives
			do {
				nonNegative = nonNegative.getChild(0);
				isNegative = !isNegative;
			} while (literal.getAtom().equals(GdlNode.GDL_NOT));

			if (isNegative) {
				return !model.contains(literal.toString());
			} else {
				return model.contains(literal.toString());
			}
		} else {
			return model.contains(literal.toString());
		}
	}

	public boolean evaluate(GdlRule clause) {
		for (int i = 1; i < clause.getChildren().size(); i++) {
			if (!contains((GdlLiteral) clause.getChild(i))) {
				return false;
			}
		}
		return true;
	}

	public boolean evaluate(GdlNode node) {
		if (node instanceof GdlLiteral) {
			return contains((GdlLiteral) node);
		} else if (node instanceof GdlRule) {
			return evaluate((GdlRule) node);
		} else {
			return false;
		}
	}

	public boolean evaluateMultiple(GdlNode headNode, List<GdlRule> bodyList) throws Exception {

		for (GdlRule clause : bodyList) {
			if (!clause.getChild(0).toString().contentEquals(headNode.toString())) {
				throw new Exception("Head nodes don't match");
			}
			if (!evaluate(clause)) {
				return false;
			}
		}
		return true;
	}
}