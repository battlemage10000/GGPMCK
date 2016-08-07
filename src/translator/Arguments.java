package translator;

public class Arguments{
	private String atom;
	private int arity;
	
	public Arguments(String atom, int arity){
		this.atom = atom;
		this.arity = arity;
	}
	
	public String getAtom(){
		return atom;
	}
	
	public int getArity(){
		return arity;
	}
	
	@Override
	public boolean equals(Object obj){
		if(obj != null && ((Arguments)obj).getArity() == arity && ((Arguments)obj).getAtom().equals(atom)) {
			return true;
		}
		return false;
	}
	
	@Override
	public String toString(){
		return atom + "[" + arity + "]";
	}
}