package translator.graph;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class DomainGraph{

	private Map<Term, ArrayList<Term>> adjacencyMap;
	
	public DomainGraph(){
		adjacencyMap = new HashMap<Term, ArrayList<Term>>();
	}
	
	public boolean hasTerm(String term, int arity){
		return adjacencyMap.containsKey(new Term(term, arity));
	}
	
	public ArrayList<Term> getNeighbours(String term, int arity){
		if(hasTerm(term, arity)){	return adjacencyMap.get(new Term(term, arity));	}
		else{	return new ArrayList<Term>();	}
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
	
	public void addFunction(String term, int arity){
		Term function = new Term(term, arity, true);
		if(!adjacencyMap.containsKey(function)){
			adjacencyMap.put(function, new ArrayList<Term>());
		}
		for(int i=1; i<= arity; i++){
			Term parameter = new Term(term, i);
			if(!adjacencyMap.containsKey(parameter)){
				adjacencyMap.put(parameter, new ArrayList<Term>());
			}	
		}
	}
	
	public void addEdge(String fromTerm, int fromArity, String toTerm, int toArity){
		Term from = new Term(fromTerm, fromArity);
		Term to = new Term(toTerm, toArity);
		if(!adjacencyMap.containsKey(from)){
			adjacencyMap.put(from, new ArrayList<Term>());
		}
		if(!adjacencyMap.get(from).contains(to)){
			adjacencyMap.get(from).add(to);
		}
		if(!adjacencyMap.containsKey(to)){
			adjacencyMap.put(to, new ArrayList<Term>());
		}
	}
	
	public String dotEncodedGraph(){
		StringBuilder dot = new StringBuilder();
		
		dot.append("strict digraph {");
		dot.append(System.lineSeparator());
		
		for(Term from : adjacencyMap.keySet()){
			if(adjacencyMap.get(from).size() > 0){
				dot.append(System.lineSeparator() +"  d_" + from.getTerm() + "_" + from.getArity() + " -> { ");
				for(Term to : adjacencyMap.get(from)){
					dot.append("d_" + to.getTerm());
					if(to.getArity() > 0){
						dot.append("_" + to.getArity() + " ");
					}else if(to.getFunctionArity() > 0){
						dot.append("_" + to.getFunctionArity() + " ");
					}else{
						dot.append(" ");
					}
				}
				dot.append("}");
			}
		}
		
		dot.append(System.lineSeparator());
		dot.append("}");
		//dot.append(System.lineSeparator());
		
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
		
		System.out.println("From : legal[1]");
		for(Term to : getDomain("legal", 1)){
			System.out.println("  To : " + to.toString());
		}
		System.out.println("From : legal[2]");
		for(Term to : getDomain("legal", 2)){
			System.out.println("  To : " + to.toString());
		}
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
		
		public String getTerm(){	return term;	}
		
		public int getArity(){	return arity;	}
		
		public int getFunctionArity(){	return functionArity;	}
		
		@Override
		public String toString(){	return term + "[" + arity + "]";	}
		
		@Override
		public int hashCode(){	return toString().hashCode();	}
		
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