
/**
 * This class will be used as key object for item similarity table.
 */
public class Sim_key {
	int movieId1;
	int movieId2;
	
	public Sim_key(int k1, int k2) {
		if(k1 < k2) {
			this.movieId1 = k1;
			this.movieId2 = k2;
		} else {
			this.movieId1 = k2;
			this.movieId2 = k1;
		}
	}
	
	public int getKey1() {
		return this.movieId1;
	}
	
	public int getKey2() {
		return this.movieId2;
	}
	
	@Override
	public int hashCode() {
		return Integer.hashCode(movieId1) + Integer.hashCode(movieId2);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Sim_key && ( 
				(((Sim_key) obj).movieId1 == this.movieId1 &&
				((Sim_key) obj).movieId2 == this.movieId2) || 
				(((Sim_key) obj).movieId1 == this.movieId2 &&
				((Sim_key) obj).movieId2 == this.movieId1)
		)) {
			return true;
		} 
		return false;
	}
	
	public void Print() {
		System.out.print(movieId1 + "," + movieId2);
	}
}
