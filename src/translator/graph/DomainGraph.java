package translator.graph;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class DomainGraph{
	private Map<Term, ArrayList<Term>> adjacencyMap;
	
	public DomainGraph(){
		adjacencyMap = new HashMap<Term, ArrayList<Term>>();
	}
	
	public boolean hasTerm(String term, int arity){
		return adjacencyMap.containsKey(new Term(term, arity));
	}
	
	public ArrayList<Term> getNeighbours(String term, int arity){
		if(hasTerm(term, arity)){
			return adjacencyMap.get(new Term(term, arity));
		}else{
			return new ArrayList<Term>();
		}
	}
	
	public ArrayList<Term> getDomain(String term, int arity){
		ArrayList<Term> domain = new ArrayList<Term>();
		Term termObj = new Term(term, arity);
		
		for(Term dependency : adjacencyMap.get(termObj)){
			if(dependency.isConstant() && !domain.contains(dependency)){
				domain.add(dependency);
			}else{
				if(!dependency.visited){
					dependency.visited = true;
					for(Term subTerm : getDomain(dependency.getTerm(), dependency.getArity())){
						if(!domain.contains(subTerm)){
							domain.add(subTerm);
						}
					}
					dependency.visited = false;
				}
			}
		}
		return domain;
	}
	
	public Map<Term, ArrayList<Term>> getDomainMap(){
		Map<Term, ArrayList<Term>> domainMap = new HashMap<Term, ArrayList<Term>>();
		
		for(Term term : adjacencyMap.keySet()){
			domainMap.put(term, getDomain(term.getTerm(), term.getArity()));
		}
		
		return domainMap;
	}
	
	public void addTerm(String term, int arity){
		Term newTerm = new Term(term, arity);
		if(!adjacencyMap.containsKey(newTerm)){
			adjacencyMap.put(newTerm, new ArrayList<Term>());
		}
	}
	
	public void addFunction(String term, int functionArity){
		Term function = new Term(term, functionArity, true);
		if(!adjacencyMap.containsKey(function)){
			adjacencyMap.put(function, new ArrayList<Term>());
		}else{
			adjacencyMap.put(function, adjacencyMap.get(function));
		}
		for(int i=1; i<= functionArity; i++){
			Term parameter = new Term(term, i);
			if(!adjacencyMap.containsKey(parameter)){
				addTerm(term, i);
			}	
		}
	}
	
	public void addEdge(String fromTerm, int fromArity, String toTerm, int toArity, boolean toFunction){
		Term from = new Term(fromTerm, fromArity);
		Term to = new Term(toTerm, toArity, toFunction);
		if(!adjacencyMap.containsKey(from)){
			addTerm(fromTerm, fromArity);
		}
		if(!adjacencyMap.containsKey(to)){
			if(toFunction){
				addFunction(toTerm, toArity);
			}else{
				addTerm(toTerm, toArity);
			}
		}
		if(!adjacencyMap.get(from).contains(to)){
			adjacencyMap.get(from).add(to);
		}
	}
	

	public void addEdge(String fromTerm, int fromArity, String toTerm, int toArity){
		addEdge(fromTerm, fromArity, toTerm, toArity, false);
	}
	
	public String dotEncodedGraph(){
		StringBuilder dot = new StringBuilder();
		
		dot.append("strict digraph {");
		
		for(Term node : adjacencyMap.keySet()){
			dot.append(System.lineSeparator() + "d_" + node.getTerm());
			if(node.getArity() > 0){
				dot.append("_" + node.getArity() + " [label=\"" + node.getTerm() + "[" + node.getArity() +"]\",color=blue]");
			}else if(node.getFunctionArity() > 0){
				dot.append("__" + node.getFunctionArity() + " [label=\"" + node.getTerm() + "/" + node.getFunctionArity() +"\",color=red]");
				// Add subgraph that links functor to function parameters
				dot.append(System.lineSeparator() + "subgraph {");
				for(int i=1; i <= node.getFunctionArity(); i++){
					dot.append(System.lineSeparator() + "  d_" + node.getTerm() + "__" + node.getFunctionArity());
					dot.append("  ->  d_" + node.getTerm() + "_" + i);
				}
				dot.append(System.lineSeparator() + "}");
			}else{
				dot.append(" [label=\""+node.getTerm()+"\",color=green]");
			}
		}
		
		for(Term from : adjacencyMap.keySet()){
			if(adjacencyMap.get(from).size() > 0){
				dot.append(System.lineSeparator() +"  d_" + from.getTerm() + "_" + from.getArity() + " -> { ");
				for(Term to : adjacencyMap.get(from)){
					dot.append("d_" + to.getTerm());
					
					if(to.getFunctionArity() > 0){
						dot.append("__" + to.getFunctionArity());
					}else if(to.getArity() > 0){
						dot.append("_" + to.getArity());
					}
					dot.append(" ");
					
				}
				dot.append("}");
			}
		}
		
		dot.append(System.lineSeparator());
		dot.append("}");
		
		return dot.toString();
	}
	
	public void printGraph(){
		for(Term from : adjacencyMap.keySet()){
			System.out.println("From : " + from.toString());
			for(Term to : adjacencyMap.get(from)){
				System.out.println("  To : " + to.toString());
			}
		}
	}
	
	public void printFunction(String term, int arity){
		System.out.println("From : " + term + "[" + arity + "]");
		for(Term to : getDomain(term, arity)){
			System.out.println("  To : " + to.toString());
		}
	}
	
	public void printGraphDomains(){
		for(Term from : adjacencyMap.keySet()){
			System.out.println("From : " + from.toString());
			for(Term to : getDomain(from.getTerm(), from.getArity())){
				System.out.println("  To : " + to.toString());
			}
		}
	}
	
	public static class Term{
		private String term;
		private int arity;
		private int functionArity;
		boolean visited;
		
		public Term(String term, int arity){
			this.term = term;
			this.arity = arity;
			this.functionArity = 0;
		}
		
		public Term(String term, int arity, boolean function){
			if(function){
				this.term = term;
				this.arity = 0;
				this.functionArity = arity;
			}else{
				this.term = term;
				this.arity = arity;
				this.functionArity = 0;
			}
		}
		
		public boolean isConstant(){
			if(arity == 0 && functionArity == 0){
				return true;
			}else{
				return false;
			}
		}
		
		public String getTerm(){
			return term;
		}
		
		public int getArity(){
			return arity;
		}
		
		public int getFunctionArity(){
			return functionArity;
		}
		
		@Override
		public String toString(){
			return term + "[" + arity + "]";
		}
		
		@Override
		public int hashCode(){
			return toString().hashCode();
		}
		
		@Override
		public boolean equals(Object obj){
			if(obj != null && obj instanceof Term){
				Term other = (Term) obj;
				if(this.term.equals(other.getTerm()) && this.arity == other.getArity()){
					if(other.getFunctionArity() > this.functionArity){
						this.functionArity = other.getFunctionArity();
					}else{
						other.functionArity = this.functionArity;
					}
					return true;
				}
			}
			return false;
		}
	}
}