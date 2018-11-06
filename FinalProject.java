import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class FinalProject {

	public Map<Integer, Double> averageItemRating = new HashMap<Integer, Double>();
	public Map<Sim_key, Double> S = new HashMap<Sim_key, Double>();
	public Integer[] Sindex;
	public double[][] S2;
	public Map<Integer, List<Rating>> itemToUserMap = new HashMap<Integer, List<Rating>>();
	public Map<Integer, List<Rating>> userToItemMap = new HashMap<Integer, List<Rating>>();
	public Map<Sim_key, List<Double[]>> itemSimMap = new HashMap<Sim_key, List<Double[]>>();
	public Map<Integer, ArrayList<Integer> > bucketMap ;
	public Integer[] keyArray;
	LSH lsh;
	
	/**
	 * Main program.
	 * @param args   command line argument
	 */
	public static void main (String[] args) throws Exception {
		FinalProject fp = new FinalProject();
		fp.Partition();
		fp.runIntraSimilarity();
		fp.Inter_similarity();
		fp.findRecommendationForUser(2);
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
        
		lsh = new LSH(itemToUserMap, userToItemMap);
        lsh.createGroups();
		bucketMap = lsh.getBuckets();
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
		keyArray = new Integer[N];
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

	public void runIntraSimilarity() {
		for(int bucketId: bucketMap.keySet()) {
			intra_sim_map(bucketMap.get(bucketId));
		}
		//System.out.println("Size of S: " + S.size());
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

				similarity = sum / Math.sqrt(pi * pj);
				if(Double.isNaN(similarity)) {
					similarity = 0;
				}
				simKey = new Sim_key(items.get(item1Index), items.get(item2Index));
				S.put(simKey, similarity);
				S2[Sindex[simKey.getKey1()]][Sindex[simKey.getKey2()]] = similarity;
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
	
		// For every pair in list, if the movies don't belong in the same group.
		for (int i = 0; i < list.size() - 1; i++) {
			rate1 = list.get(i);
			for (int j = i + 1; j < list.size(); j++) {
				rate2 = list.get(j);
				
				if(lsh.getItemBucket(rate1.getMovieId()) !=
						lsh.getItemBucket(rate2.getMovieId())) {
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
	
	public void findRecommendationForUser(int userID) {	
		List<Rating> userExistingRatings = userToItemMap.get(userID);
		ArrayList<Integer> mostSimilar = new ArrayList<>();
		for (Rating currentRating: userExistingRatings) {

			double currentMostSimilar = Double.MIN_VALUE;
			int currentMostSimilarItem = -1;
			int itemId = currentRating.getMovieId();
			int index = Sindex[itemId];
			for(int i = 0; i < S2[index].length; i++) {
				if(S2[index][i] > currentMostSimilar) {
					currentMostSimilar = S2[index][i];
					currentMostSimilarItem = keyArray[i];
				}
			}

			mostSimilar.add(currentMostSimilarItem);
			
		}

		//After this Find top 3 similar Items
		double similar1 = Double.MIN_VALUE;
		double similar2 = Double.MIN_VALUE;
		double similar3 = Double.MIN_VALUE;
		int item1 = -1, item2 = 1, item3 = -1;

		for(int index = 0; index < userExistingRatings.size(); index++) {
			// Avoid suggesting the same item
			if(!isWatched(userID, mostSimilar.get(index)) &&
					mostSimilar.get(index) != item1 &&
					mostSimilar.get(index) != item2 &&
					mostSimilar.get(index) != item3) {
				int firstItemIdIndex = Sindex[userExistingRatings.get(index).getMovieId()];
				int secondItemIdIndex = Sindex[mostSimilar.get(index)];
				 
				if(S2[firstItemIdIndex][secondItemIdIndex] > similar1){
					similar1 = S2[firstItemIdIndex][secondItemIdIndex];
					item1 = mostSimilar.get(index);
				}
				else if(S2[firstItemIdIndex][secondItemIdIndex] > similar2) {
					similar2 = S2[firstItemIdIndex][secondItemIdIndex];
					item2 = mostSimilar.get(index);
				}
				else if(S2[firstItemIdIndex][secondItemIdIndex] > similar3) {
					similar3 = S2[firstItemIdIndex][secondItemIdIndex];
					item3 = mostSimilar.get(index);
				}
			}
		}
		System.out.println(item1);
		System.out.println(item2);
		System.out.println(item3);
	}

	public boolean isWatched(int userID, int itemId) {
		List<Rating> userExistingRatings = userToItemMap.get(userID);
		for(Rating rating : userExistingRatings) {
			if (rating.movieId == itemId)
				return true;
		}
		return false;
	}

}
