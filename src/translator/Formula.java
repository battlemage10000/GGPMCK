package translator;

import java.util.List;
import java.util.ArrayList;

public class Formula extends Arguments{
	String atom;
	List<Arguments> parameters;
	
	public Formula(String atom, List<Arguments> parameters){
		super(atom, parameters.size());
		this.atom = atom;
		this.parameters = parameters;
	}
	
	public Formula(String atom){
		this(atom, new ArrayList<Arguments>());
	}
	
	public String getAtom(){
		return atom;
	}
	
	public int getArity(){
		return parameters.size();
	}
	
	public List<Arguments> getParameters(){
		return parameters;
	}
	
	
	@Override
	public String toString(){
		return atom + " " + parameters.size();
	}
}