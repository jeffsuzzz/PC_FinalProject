import java.util.ArrayList;
import edu.rit.pj2.Vbl;

public class ArrayListVbl<K> extends ArrayList<K> implements Vbl {

	public ArrayListVbl() {
		super();
	}
	@Override
	public Object clone() {
		return super.clone();
	}
	
	@Override
	public void reduce(Vbl arg0) {
		// TODO Auto-generated method stub
		super.addAll((ArrayList<K>)arg0);
	}

	@Override
	public void set(Vbl arg0) {
		// TODO Auto-generated method stub
		super.clear();
		reduce(arg0);
	}

}
