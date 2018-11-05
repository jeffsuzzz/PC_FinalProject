import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class FinalProject {

	public Map<Integer, Double> MinHash = new HashMap<Integer, Double>();
	public Map<Integer, Double> averageItemRating = new HashMap<Integer, Double>();
	public Map<Double, List<KV_pairs>> H = new HashMap<Double, List<KV_pairs>>();
	public Map<Sim_key, Double> S = new HashMap<Sim_key, Double>();
	public Map<Integer, List<Rating>> itemToUserMap = new HashMap<Integer, List<Rating>>();
	public Map<Integer, List<Rating>> userToItemMap = new HashMap<Integer, List<Rating>>();
	public Map<Sim_key, List<Double[]>> itemSimMap = new HashMap<Sim_key, List<Double[]>>();
	public Map<Integer, ArrayList<Integer> > bucketMap ;
	
	/**
	 * Main program.
	 * @param args   command line argument
	 */
	public static void main (String[] args) throws Exception {
		FinalProject fp = new FinalProject();
		fp.Partition();
		fp.runIntraSimilarity();
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

		LSH lsh = new LSH(itemToUserMap, userToItemMap);
        lsh.createGroups();
		bucketMap = lsh.getBuckets();
        /*
        System.out.println(MinHash.size());
        System.out.println(averageItemRating.size());
        System.out.println(H.size());
        it = H.entrySet().iterator();
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
			averageItemRating.put(movieId,rating.rating);
		}
		else {
			averageItemRating.put(movieId, averageItemRating.get(movieId) + rating.movieId );
		}
        Shuffle(movieId, rating, itemToUserMap);
        Shuffle(userId,rating, userToItemMap);
    }


    public void computeAverageRating() {

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
	

	

	public void runIntraSimilarity() {

		for(int bucketId: bucketMap.keySet()) {
			intra_sim_map(bucketMap.get(bucketId));
		}

	}

	public void intra_sim_map(ArrayList<Integer> items) {
		if (items.size() == 1) {
			return;
		}

		double similarity, sum, pi, pj, averageItem1Rating, averageItem2Rating;
		ListIterator<Rating> ratingIterator1, ratingIterator2;
		Rating tmpValue1, tmpValue2;
		int user1, user2;
		double rate1, rate2;
		Sim_key simKey;
		// For every pair in L, compute similarity.
		for (int item1Index = 0; item1Index < items.size() - 1; item1Index++) {

			averageItem1Rating = averageItemRating.get(items.get(item1Index));
			for (int item2Index = item1Index + 1; item2Index < items.size(); item2Index++) {

				sum = 0; pi = 0; pj = 0;
				averageItem2Rating = averageItemRating.get(items.get(item2Index));
				// Can it be improved using its sorted nature?
				ratingIterator1 = itemToUserMap.get(items.get(item1Index)).listIterator();
				while (ratingIterator1.hasNext()) {
					tmpValue1 = ratingIterator1.next();
					user1 = tmpValue1.getUser();
					rate1 = tmpValue1.getRating();

					ratingIterator2 = itemToUserMap.get(items.get(item2Index)).listIterator();
					while (ratingIterator2.hasNext()) {
						tmpValue2 = ratingIterator2.next();
						user2 = tmpValue2.getUser();
						rate2 = tmpValue2.getRating();

						// If the same user.
						if (user1 == user2) {
							sum += (rate1 - averageItem1Rating) * (rate2 - averageItem2Rating);
							pi += (rate1 - averageItem1Rating) * (rate1 - averageItem1Rating);
							pj += (rate2 - averageItem2Rating) * (rate2 - averageItem2Rating);
						}
					}
				}
				//if(sum!=0 || pi!=0 || pj!=0) {
				//System.out.println(sum+" "+pi+" "+pj);
				//}

				similarity = sum / Math.sqrt(pi * pj);
				if(Double.isNaN(similarity)) {
					similarity = 0;
				}
				simKey = new Sim_key(items.get(item1Index), items.get(item2Index));
				S.put(simKey, similarity);
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
        //System.out.println("Size of S: " + S.size());
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
