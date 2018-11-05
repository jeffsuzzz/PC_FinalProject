import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

public class FinalProject {

	public Map<Integer, Double> MinHash = new TreeMap<Integer, Double>();
	public Map<Integer, Double> averageItemRating = new TreeMap<Integer, Double>();
	public Map<Double, List<KV_pairs>> H = new TreeMap<Double, List<KV_pairs>>();
	public Map<Sim_key, Double> S = new HashMap<Sim_key, Double>();
	public Integer[] Sindex;
	public double[][] S2;
	public Map<Integer, List<Rating>> itemToUserMap = new TreeMap<Integer, List<Rating>>();
	public Map<Integer, List<Rating>> userToItemMap = new TreeMap<Integer, List<Rating>>();
	public Map<Sim_key, List<Double[]>> itemSimMap = new HashMap<Sim_key, List<Double[]>>();
	
	/**
	 * Main program.
	 * @param args   command line argument
	 */
	public static void main (String[] args) throws Exception {
		FinalProject fp = new FinalProject();
		fp.Partition();
		fp.Intra_similarity();
		fp.Inter_similarity();
	}
	
	/**
	 * Partition phase.
	 */
	public void Partition() throws IOException  {
		String csvFile = "ratings_small2.csv";
        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String line = br.readLine();

        while ((line = br.readLine()) != null) {
            storeRating(line);
        }
        computeAverage();
        buildSimilarityArray(300);
        
        /*LSH lsh = new LSH(itemToUserMap, userToItemMap);
        lsh.createGroups();
        lsh.print();*/
        
        Iterator it = itemToUserMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Rating>> pair = (Map.Entry)it.next();
            Part_reduce(pair.getKey(), pair.getValue());
        }
        
        System.out.println(MinHash.size());
        //System.out.println(Pearson.size());
        //System.out.println(H.size());
        /*it = H.entrySet().iterator();
        it.next();
        Map.Entry<Double, List<KV_pairs>> entry = (Map.Entry)it.next();
        ListIterator<KV_pairs> its = entry.getValue().listIterator();
        System.out.println("h: "+entry.getKey());
        while (its.hasNext()) {
        	its.next().Print();
        }*/
	}
	
	public void storeRating(String stringRating) {
        String[] ratings = stringRating.split(",");
        int movieId = Integer.parseInt(ratings[1]);
        int userId = Integer.parseInt(ratings[0]);
        Rating rating = new Rating(movieId, userId,
                Double.parseDouble(ratings[2]));
        
        if(!averageItemRating.containsKey(movieId)) {
        	averageItemRating.put(movieId, rating.getRating());
        } else {
        	averageItemRating.put(movieId, rating.getRating() 
        			+ averageItemRating.get(movieId));
        }
        
        Shuffle(movieId, rating, itemToUserMap);
        Shuffle(userId,rating, userToItemMap);
    }
	
	public void computeAverage() {
		Iterator it = itemToUserMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Rating>> pair = (Map.Entry)it.next();
            double sum = averageItemRating.get(pair.getKey());
            double avg = sum / pair.getValue().size();
            averageItemRating.put(pair.getKey(), avg);
        }
	}
	
	public void buildSimilarityArray(int maxValue) {
		int N = itemToUserMap.size();
		final Integer[] keyArray = new Integer[N];
        Sindex = new Integer[maxValue];	// Max value of itemId + 1.
        itemToUserMap.keySet().toArray(keyArray);
        
        for (int i = 0; i < N; i++) {
        	Sindex[keyArray[i]] = i;
        }
        S2 = new double[N][N];
	}
	
	/**
	 * Add the key-value into the target map.
	 * @param key
	 * @param value
	 * @param list
	 */
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
        averageItemRating.put(movieID, rAvg);
        MinHash.put(movieID, hMin);

        Collections.sort(list, new RatingComparator());
        List<KV_pairs> tmp = new ArrayList<KV_pairs>();
        if(H.containsKey(hMin)) {
            tmp = H.get(hMin);
        }
        KV_pairs now = new KV_pairs(movieID, list);
        tmp.add(now);
        H.put(hMin, tmp);
    }
	
	public void Intra_similarity() {
		Iterator it = H.entrySet().iterator();
        Map.Entry<Double, List<KV_pairs>> entry;
        while (it.hasNext()) {
        	entry = (Map.Entry)it.next();
        	Intra_sim_map (entry.getValue());
        }
        //H = new HashMap<Double, List<KV_pairs>>();
        //itemToUserMap = new HashMap<Integer, List<Rating>>();
        /*System.out.println("Size of S: " + S.size());
        it = S.entrySet().iterator();
        Map.Entry<Sim_key, Double> entry2;
        System.out.println("Key     sij");
        while (it.hasNext()) {
        	entry2 = (Map.Entry)it.next();
        	entry2.getKey().Print();
        	System.out.println(" " + entry2.getValue());
        }*/
	}
	
	public void Intra_sim_map (List<KV_pairs> L) {
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
			riBar = averageItemRating.get(L.get(i).GetMovieID());
			for (int j = i + 1; j < L.size(); j++) {
				sum = 0; pi = 0; pj = 0;
				rjBar = averageItemRating.get(L.get(j).GetMovieID());
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
				S.put(simKey, sij);
				if(L.get(i).GetMovieID() < L.get(j).GetMovieID())
					S2[Sindex[simKey.getKey1()]][Sindex[simKey.getKey2()]] = sij;
				else
					S2[Sindex[simKey.getKey2()]][Sindex[simKey.getKey1()]] = sij;
			}
		}
	}
	
	public void Inter_similarity() {
		Iterator it = userToItemMap.entrySet().iterator();
        Map.Entry<Integer, List<Rating>> entry;
        while (it.hasNext()) {
        	entry = (Map.Entry)it.next();
        	Inter_sim_map (entry.getKey(), entry.getValue());
        }

        Iterator it2 = itemSimMap.entrySet().iterator();
        Map.Entry<Sim_key, List<Double[]>> entry2;
        while (it2.hasNext()) {
        	entry2 = (Map.Entry)it2.next();
        	Inter_sim_reduce(entry2.getKey(), entry2.getValue());
        }
        System.out.println("Size of S: " + S.size());
	}
	
	public void Inter_sim_map(int userId, List<Rating> list) {
		Rating rate1, rate2;
		double riBar, rjBar;
		Double[] itemSim = new Double[2];
		Sim_key simKey;
		//System.out.println(userId + " " + list.size());
	
		// For every pair in list, if the movies don't belong in the same group.
		for (int i = 0; i < list.size() - 1; i++) {
			rate1 = list.get(i);
			for (int j = i + 1; j < list.size(); j++) {
				rate2 = list.get(j);
			
				if(!MinHash.get(rate1.getMovieId()).equals(
						MinHash.get(rate2.getMovieId()))) {
					riBar = averageItemRating.get(rate1.getMovieId());
					rjBar = averageItemRating.get(rate2.getMovieId());
					itemSim[0] = rate1.getRating() - riBar;
					itemSim[1] = rate2.getRating() - rjBar;
					simKey = new Sim_key(rate1.getMovieId(), rate2.getMovieId());
					Shuffle(simKey, itemSim, itemSimMap);
				}
			}
		}	
	}
	
	public void Inter_sim_reduce(Sim_key simKey, List<Double[]> L) {
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
		S.put(simKey, sij);
		S2[Sindex[simKey.getKey1()]][Sindex[simKey.getKey2()]] = sij;
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
