import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class FinalProject {

	public static Map<Integer, Double> MinHash = new HashMap<Integer, Double>();
	public static Map<Integer, Double> Pearson = new HashMap<Integer, Double>();
	public static Map<Double, List<KV_pairs>> H = new HashMap<Double, List<KV_pairs>>();
	public static Map<Sim_key, Double> S = new HashMap<Sim_key, Double>();
	static Map<Integer, List<Value>> intra = new HashMap<Integer, List<Value>>();
	
	/**
	 * Main program.
	 * @param args   command line argument
	 */
	public static void main (String[] args) throws Exception {
		Partition();
	}
	
	/**
	 * Partition phase.
	 */
	public static void Partition() {
		String csvFile = "ratings_small.csv";
        Map<Integer, List<Value>> map = new HashMap<Integer, List<Value>>();
        
        try {
        	BufferedReader br = new BufferedReader(new FileReader(csvFile));
            List<Value> partValue;
            Value tmp1, tmp2;
            String[] ratings;
            int userID, movieID;
            double rating;
            String line = br.readLine();

            while ((line = br.readLine()) != null) {
                // use comma as separator
            	ratings = line.split(",");
            	userID = Integer.parseInt(ratings[0]);
            	movieID = Integer.parseInt(ratings[1]);
            	rating = Double.parseDouble(ratings[2]);
            	tmp1 = new Value(userID, 0, rating);
            	tmp2 = new Value(0, movieID, rating);

            	Shuffle(movieID, tmp1, map);
            	Shuffle(userID, tmp2, intra);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Value>> pair = (Map.Entry)it.next();
            Part_reduce(pair.getKey(), pair.getValue());
        }
        
        System.out.println(MinHash.size());
        System.out.println(Pearson.size());
        System.out.println(H.size());
        
        it = H.entrySet().iterator();
        //it.next();
        Map.Entry<Double, List<KV_pairs>> entry = (Map.Entry)it.next();
        ListIterator<KV_pairs> its = entry.getValue().listIterator();
        System.out.println("h: "+entry.getKey());
        while (its.hasNext()) {
        	its.next().Print();
        }
	}
	
	public static void Shuffle(int key, Value value, Map<Integer,List<Value>> list) {
		List<Value> partValue = new ArrayList<Value>();
    	if(list.containsKey(key)) {
    		partValue = list.get(key);
    	}
    	partValue.add(value);
    	list.put(key, partValue);
	}
	
	public static void Part_reduce(int movieID, List<Value> list) {
		double sum = 0;
		double hMin = Double.MAX_VALUE;
		int size = list.size();
		
		ListIterator<Value> it = list.listIterator();
        while (it.hasNext()) {
        	/*if(size == 1) {
        		System.out.println(tmp.userID+" "+movieID + " " + tmp.rating);
    		}*/
        	Value tmp = it.next();
        	sum += tmp.rating;
        	if(hash(tmp.rating, size) < hMin) 
        		hMin = hash(tmp.rating, size);
        }
        
        double rAvg = sum / size;
    	Pearson.put(movieID, rAvg);
    	MinHash.put(movieID, hMin);
    	
    	Collections.sort(list, new CustomComparator());
    	List<KV_pairs> tmp = new ArrayList<KV_pairs>();
    	if(H.containsKey(hMin)) {
    		tmp = H.get(hMin);
    	}
    	KV_pairs now = new KV_pairs(movieID, list);
    	tmp.add(now);
    	H.put(hMin, tmp);
	}
	
	public static void Intra_similarity() {
		Iterator it = H.entrySet().iterator();
        Map.Entry<Double, List<KV_pairs>> entry;
        while (it.hasNext()) {
        	entry = (Map.Entry)it.next();
        	Intra_sim_map (entry.getValue());
        }
	}
	
	public static void Intra_sim_map (List<KV_pairs> L) {
		if (L.size() == 1) {
			return;
		}
		
		double sij, sum, pi, pj, riBar, rjBar;
		ListIterator<Value> iti, itj;
		Value tmpValue1, tmpValue2;
		int user1, user2;
		double rate1, rate2;
		Sim_key simKey;
		// For every pair in L, compute similarity.
		for (int i = 0; i < L.size() - 1; i++) {
			for (int j = i; j < L.size(); j++) {
				sum = 0; pi = 0; pj = 0;
				riBar = Pearson.get(i);
				rjBar = Pearson.get(j);
				// Can it be improved using its sorted nature?
				iti = L.get(i).GetListofValues().listIterator();
				while (iti.hasNext()) {
					tmpValue1 = iti.next();
					user1 = tmpValue1.Getuser();
					rate1 = tmpValue1.GetRating();
					
					itj = L.get(j).GetListofValues().listIterator();
					while (itj.hasNext()) {
						tmpValue2 = itj.next();
						user2 = tmpValue2.Getuser();
						rate2 = tmpValue2.GetRating();
						
						// If the same user.
						if (user1 == user2) {
							sum += (rate1 - riBar) * (rate2 - rjBar);
							pi += (rate1 - riBar) * (rate1 - riBar);
							pj += (rate2 - rjBar) * (rate2 - rjBar);
						}
					}
				}
				
				sij = sum / Math.sqrt(pi * pj);
				simKey = new Sim_key(L.get(i).GetMovieID(), L.get(j).GetMovieID());
				S.put(simKey, sij);
			}
			
			iti = L.get(i).GetListofValues().listIterator();
			tmpValue1 = iti.next();
			user1 = tmpValue1.Getuser();
			rate1 = tmpValue1.GetRating();
			Value tmp = new Value(0, L.get(i).GetMovieID(), rate1);
			while (iti.hasNext()) {
				Shuffle(user1, tmp, intra);
			}
		}
	}
	
	public static class CustomComparator implements Comparator<Value> {
	    @Override
	    public int compare(Value v1, Value v2) {
	    	int user1 = v1.Getuser();
	    	int user2 = v2.Getuser();
	    	if()
	    	if(user1 > user2)
	    		return 1;
	    	else if (user1 < user2)
	    		return -1;
	    	else 
	    		return 0;
	    }
	}
	
	public static double hash(double s, int k) {
		int a, b, p;
		a = 1; b = 2;
		p = k+1;
		while(!Prime.isPrime(p)) {
			p++;
		}
		return (a * s + b) % p;
	}
	
	public static class Value{
		int userID;
		int movieID;
		double rating;
		public Value() {}
		
		public Value(int userID, int movieID, double rating) {
			this.userID = userID;
			this.movieID = movieID;
			this.rating = rating;
		}
		
		public int Getuser() {
			return this.userID;
		}
		
		public int GetMovieID() {
			return this.movieID;
		}
		
		public double GetRating() {
			return this.rating;
		}
		
		public void Print() {
			if(this.movieID == 0)
				System.out.print("("+userID+","+rating+") ");
			else if(this.userID == 0)
				System.out.print("("+movieID+","+rating+") ");
		}
	}
	
	public static class KV_pairs{
		int movieID;
		List<Value> user_rateList;
		public KV_pairs() {}
		
		public KV_pairs(int movieID, List<Value> user_rateList) {
			this.movieID = movieID;
			this.user_rateList = user_rateList;
		}
		
		public int GetMovieID() {
			return this.movieID;
		}
		
		public List<Value> GetListofValues() {
			return this.user_rateList;
		}
		
		public void Print() {
			System.out.print(movieID+", (");
			ListIterator<Value> it = user_rateList.listIterator();
	        while (it.hasNext()) {
	        	it.next().Print();
	        }
	        System.out.println(")");
		}
	}
	
	public static class Sim_key{
		int movieID1;
		int movieID2;
		
		public Sim_key(int k1, int k2) {
			this.movieID1 = k1;
			this.movieID2 = k2;
		}
		
		@Override
		public int hashCode() {
			return movieID1 + movieID2;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof Sim_key && ( 
					(((Sim_key) obj).movieID1 == this.movieID1 &&
					((Sim_key) obj).movieID2 == this.movieID2) || 
					(((Sim_key) obj).movieID1 == this.movieID2 &&
					((Sim_key) obj).movieID2 == this.movieID1)
			)) {
				return true;
			} 
			return false;
		}
	}
}
