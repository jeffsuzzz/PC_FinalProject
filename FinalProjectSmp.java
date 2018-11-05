import edu.rit.pj2.Loop;
import edu.rit.pj2.Task;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

public class FinalProjectSmp extends Task {

	public Map<Integer, Double> MinHash = new TreeMap<Integer, Double>();
	public Map<Integer, Double> Pearson = new TreeMap<Integer, Double>();
	public Map<Double, List<KV_pairs>> H = new TreeMap<Double, List<KV_pairs>>();
	public Map<Integer, List<Rating>> itemToUserMap = new TreeMap<Integer, List<Rating>>();
	public Map<Integer, List<Rating>> userToItemMap = new TreeMap<Integer, List<Rating>>();
	public Map<Sim_key, List<Double[]>> itemSimMap = new HashMap<Sim_key, List<Double[]>>();
	
	//HashMapVbl<Integer, Double> pearsonVbl = new HashMapVbl<Integer, Double>();
	//HashMapVbl<Integer, Double> minHashVbl = new HashMapVbl<Integer, Double>();
	//HashMapVbl<Double, List<KV_pairs>> hVbl = new HashMapVbl<Double, List<KV_pairs>>();
	HashMapVbl<Sim_key, Double> sVbl = new HashMapVbl<Sim_key, Double>();
	HashMapVbl<Sim_key, Double[]> itemSimMapVbl = new HashMapVbl<Sim_key, Double[]>();
	
	/**
	 * Main program
	 * @param args   command line argument
	 */
	public void main(String[] args) throws Exception {
		Partition();
		Intra_similarity();
		Inter_similarity();
	}
	
	public void Partition() throws IOException {
		String csvFile = "ratings_small2.csv";
		BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String line = br.readLine();
        while ((line = br.readLine()) != null) {
            storeRating(line);
        }
        
        Iterator it = itemToUserMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Rating>> pair = (Map.Entry)it.next();
            Part_reduce(pair.getKey(), pair.getValue());
        }
        /*
        int N = itemToUserMap.size();
        final Integer[] keyArray = new Integer[N];
        itemToUserMap.keySet().toArray(keyArray);

        parallelFor (0, N - 1).exec (new Loop() {
        	TreeMapVbl<Integer, Double> localPearsonVbl;
        	TreeMapVbl<Integer, Double> localMinHashVbl;
        	TreeMapVbl<Double, List<KV_pairs>> localHVbl;
        	
        	public void start(){
        		localPearsonVbl = threadLocal(pearsonVbl);
        		localMinHashVbl = threadLocal(minHashVbl);
        		localHVbl = threadLocal(hVbl);
        	}
        	
        	public void run (int n) {
        		Part_reduce(keyArray[n], itemToUserMap.get(keyArray[n]),
        				localPearsonVbl, localMinHashVbl, localHVbl);
        	}
        });*/
	}
	
	public void storeRating(String stringRating) {
        String[] ratings = stringRating.split(",");
        int movieId = Integer.parseInt(ratings[1]);
        int userId = Integer.parseInt(ratings[0]);
        Rating rating = new Rating(movieId, userId,
                Double.parseDouble(ratings[2]));
        
        Shuffle(movieId, rating, itemToUserMap);
        Shuffle(userId, rating, userToItemMap);
    }
	
	public void Shuffle(int key, Rating value, Map<Integer,List<Rating>> list) {
        List<Rating> ratingList;
        if(!list.containsKey(key)) {
            ratingList = new ArrayList<Rating>();
            list.put(key, ratingList);
        }
        list.get(key).add(value);
    }
	
	public void Shuffle(Sim_key key, Double[] sim, Map<Sim_key, List<Double[]>> list) {
		List<Double[]> simList;
		if(!list.containsKey(key)) {
			simList = new ArrayList<Double[]>();
            list.put(key, simList);
        }
        list.get(key).add(sim);
	}
	
	public void Shuffle(Map<Sim_key, List<Double[]>> map1, Map<Sim_key, Double[]> map2) {
		Iterator it = map2.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Sim_key, Double[]> pair = (Map.Entry)it.next();
            Shuffle(pair.getKey(), pair.getValue(), map1);
        }
	}
	
	public void Part_reduce(int movieID, List<Rating> list) {
        double sum = 0;
        double hMin = Double.MAX_VALUE;
        int size = list.size();

        ListIterator<Rating> it = list.listIterator();
        while (it.hasNext()) {
            Rating tmp = it.next();
            sum += tmp.rating;
            if(hash(tmp.rating, size) < hMin)
                hMin = hash(tmp.rating, size);
        }

        double rAvg = sum / size;
        Pearson.put(movieID, rAvg);
        MinHash.put(movieID, hMin);

        //Collections.sort(list, new RatingComparator());
        List<KV_pairs> tmp = new ArrayList<KV_pairs>();
        if(H.containsKey(hMin)) {
            tmp = H.get(hMin);
        }
        KV_pairs now = new KV_pairs(movieID, list);
        tmp.add(now);
        H.put(hMin, tmp);
    }
	
	public void Intra_similarity() {    
        int N = H.size();
        final Double[] keyArray = new Double[N];
        H.keySet().toArray(keyArray);
        
        parallelFor (0, N - 1).exec (new Loop() {
        	HashMapVbl<Sim_key, Double> localSVbl;
        	
        	public void start(){
        		localSVbl = threadLocal(sVbl);
        	}
        	
        	public void run (int n) {
        		Intra_sim_map (H.get(keyArray[n]), localSVbl);
        	}
        });
	}
	
	public void Intra_sim_map (List<KV_pairs> L, HashMapVbl<Sim_key, Double> s) {
		if (L.size() == 1) {
			return;
		}

		double sij, sum, pi, pj, riBar, rjBar;
		ListIterator<Rating> iti, itj;
		Rating tmpValue1, tmpValue2;
		int user1, user2;
		double rate1, rate2;
		Sim_key simKey;
		// For every pair in L, compute similarity.
		for (int i = 0; i < L.size() - 1; i++) {
			riBar = Pearson.get(L.get(i).GetMovieID());
			for (int j = i + 1; j < L.size(); j++) {
				sum = 0; pi = 0; pj = 0;
				rjBar = Pearson.get(L.get(j).GetMovieID());
				// Can it be improved using its sorted nature?
				iti = L.get(i).GetListofValues().listIterator();
				while (iti.hasNext()) {
					tmpValue1 = iti.next();
					user1 = tmpValue1.getUser();
					rate1 = tmpValue1.getRating();
					
					itj = L.get(j).GetListofValues().listIterator();
					while (itj.hasNext()) {
						tmpValue2 = itj.next();
						user2 = tmpValue2.getUser();
						rate2 = tmpValue2.getRating();
						
						// If the same user.
						if (user1 == user2) {
							sum += (rate1 - riBar) * (rate2 - rjBar);
							pi += (rate1 - riBar) * (rate1 - riBar);
							pj += (rate2 - rjBar) * (rate2 - rjBar);
						}
					}
				}
				sij = sum / Math.sqrt(pi * pj);
				if (Double.isNaN(sij)) {
					sij = 0;
				}
				simKey = new Sim_key(L.get(i).GetMovieID(), L.get(j).GetMovieID());
				s.put(simKey, sij);
			}
		}
	}
	
	public void Inter_similarity() {
        int N = userToItemMap.size();
        final Integer[] keyArray = new Integer[N];
        userToItemMap.keySet().toArray(keyArray);
        
        parallelFor (0, N - 1).exec (new Loop() {
        	HashMapVbl<Sim_key, Double[]> localItemSimMapVbl;
        	
        	public void start(){
        		localItemSimMapVbl = threadLocal(itemSimMapVbl);
        	}
        	
        	public void run (int n) {
        		Inter_sim_map (keyArray[n], userToItemMap.get(keyArray[n]), 
        				localItemSimMapVbl);
        	}
        });
        Shuffle(itemSimMap, itemSimMapVbl);
        
        N = itemSimMap.size();
        final Sim_key[] keyArray2 = new Sim_key[N];
        itemSimMap.keySet().toArray(keyArray2);
        
        parallelFor (0, N - 1).exec (new Loop() {
        	HashMapVbl<Sim_key, Double>localSVbl;
        	
        	public void start(){
        		localSVbl = threadLocal(sVbl);
        	}
        	
        	public void run (int n) {
        		Inter_sim_reduce(keyArray2[n], itemSimMap.get(keyArray2[n]), localSVbl);
        	}
        });
        
        System.out.println("Size of S: " + sVbl.size());
	}
	
	public void Inter_sim_map(int userId, List<Rating> list, HashMapVbl<Sim_key, Double[]> map) {
		Rating rate1, rate2;
		double riBar, rjBar;
		Double[] itemSim = new Double[2];
		Sim_key simKey;
	
		// For every pair in list, if the movies don't belong in the same group.
		for (int i = 0; i < list.size() - 1; i++) {
			rate1 = list.get(i);
			for (int j = i + 1; j < list.size(); j++) {
				rate2 = list.get(j);
			
				if(!MinHash.get(rate1.getMovieId()).equals(
						MinHash.get(rate2.getMovieId()))) {
					riBar = Pearson.get(rate1.getMovieId());
					rjBar = Pearson.get(rate2.getMovieId());
					itemSim[0] = rate1.getRating() - riBar;
					itemSim[1] = rate2.getRating() - rjBar;
					simKey = new Sim_key(rate1.getMovieId(), rate2.getMovieId());
					map.put(simKey, itemSim);
					//Shuffle(simKey, itemSim, itemSimMap);
				}
			}
		}	
	}
	
	public void Inter_sim_reduce(Sim_key simKey, List<Double[]> L, HashMapVbl<Sim_key, Double> s) {
		Double[] tmp;
		double sij, sum, pi, pj;
		sum = 0; pi = 0; pj = 0;
		
		for (int i = 0; i < L.size(); i++) {
			tmp = L.get(i);
			sum += tmp[0] * tmp[1];
			pi += tmp[0] * tmp[0];
			pj += tmp[1] * tmp[1];
		}
		sij = sum / Math.sqrt(pi * pj);
		s.put(simKey, sij);
	}
	
	public double hash(double s, int k) {
		int a, b, p;
		a = 1; b = 2;
		p = k+1;
		while(!Prime.isPrime(p)) {
			p++;
		}
		return (a * s + b) % p;
	}
	
	
}
