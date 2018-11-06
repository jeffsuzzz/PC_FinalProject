import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import edu.rit.pj2.Loop;
import edu.rit.pj2.Task;

public class FinalProjectSmp extends Task{

	public Map<Integer, Double> averageItemRating = new HashMap<Integer, Double>();
	public Integer[] Sindex;
	public double[][] similarityArray;
	public Map<Integer, List<Rating>> itemToUserMap = new HashMap<Integer, List<Rating>>();
	public Map<Integer, List<Rating>> userToItemMap = new HashMap<Integer, List<Rating>>();
	public Map<Integer, ArrayList<Integer> > bucketMap ;
	public Integer[] keyArray;
	LSH lsh;
	int maxItemId;
	
	/**
	 * Main program.
	 * @param args   command line argument
	 */
	public void main (String[] args) throws Exception {
		Partition();
		runIntraSimilarity();
		Inter_similarity();
		findRecommendationForUser(2);
	}
	
	/**
	 * Partition phase.
	 */
	public void Partition() throws IOException  {
		String csvFile = "ratings_small.csv";
        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String line = br.readLine();

        while ((line = br.readLine()) != null) {
            storeRating(line);
        }
        
        buildSimilarityArray(maxItemId);
        computeAverage();  
        
		lsh = new LSH(itemToUserMap, userToItemMap);
        lsh.createGroups();
		bucketMap = lsh.getBuckets();
		//lsh.print();
	}

	/**
	 * Store userId, movieId, rate to two maps.
	 * @param stringRating
	 */
	public void storeRating(String stringRating) {
        String[] ratings = stringRating.split(",");
        int movieId = Integer.parseInt(ratings[1]);
        int userId = Integer.parseInt(ratings[0]);
        double userRating = Double.parseDouble(ratings[2]);
        if(userId > 3000) {
        	return;
		}
        Rating rating = new Rating(movieId, userId, userRating);
		if(!averageItemRating.containsKey(movieId)) {
			averageItemRating.put(movieId, userRating);
		}
		else {
			averageItemRating.put(movieId, averageItemRating.get(movieId) + userRating );
		}
        Shuffle(movieId, rating, itemToUserMap);
        Shuffle(userId, rating, userToItemMap);
        if(movieId > maxItemId)
            maxItemId = movieId;
    }

	/**
	 * Compute the average rating for every items.
	 */
	public void computeAverage() {
		Iterator it = itemToUserMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Rating>> pair = (Map.Entry)it.next();
            double sum = averageItemRating.get(pair.getKey());
            double avg = sum / pair.getValue().size();
            averageItemRating.put(pair.getKey(), avg);
        }
	}
	
	/**
	 * Build an NxN array to store similarities between two items.
	 * N is the number of different items.
	 * @param maxValue
	 */
	public void buildSimilarityArray(int maxValue) {
		int N = itemToUserMap.size();
		keyArray = new Integer[N];
        Sindex = new Integer[maxValue + 1];
        itemToUserMap.keySet().toArray(keyArray);
        
        for (int i = 0; i < N; i++) {
        	Sindex[keyArray[i]] = i;
        }
        similarityArray = new double[N][N];
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
	
	/**
	 * Parallelized Intra-Similarity phase.
	 */
	public void runIntraSimilarity() {
		parallelFor (0, bucketMap.size() - 1).exec (new Loop() {
        	public void start(){
        	}
        	
        	public void run (int n) {
        		intra_sim_map(bucketMap.get(n));
        	}
        });
	}

	/**
	 * Compute the similarity of items in the same bucket.
	 * @param items
	 * @param s
	 */
	public void intra_sim_map(ArrayList<Integer> items) {
		if (items.size() == 1) {
			return;
		}

		double similarity, sum, pi, pj, averageItem1Rating, averageItem2Rating;
		ListIterator<Rating> ratingIterator1, ratingIterator2;
		Rating tmpValue1, tmpValue2;
		int user1, user2;
		double rate1, rate2;
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
				similarityArray[Sindex[items.get(item1Index)]][Sindex[items.get(item2Index)]] = similarity;
			}
		}
	}
	
	/**
	 * Inter-similarity phase.
	 */
	public void Inter_similarity() {
		Iterator it = userToItemMap.entrySet().iterator();
        Map.Entry<Integer, List<Rating>> entry;
        while (it.hasNext()) {
        	entry = (Map.Entry)it.next();
        	Inter_sim_map (entry.getValue());
		}    
	}
	
	/**
	 * Store similarity between two items within one user.
	 * @param userId
	 * @param list
	 */
	public void Inter_sim_map(List<Rating> list) {
		final List<Rating> lists = list;
		
		// For every pair of items in this list.
		parallelFor (0, lists.size() - 2).exec (new Loop() {
			Rating rate1, rate2;
        	public void start(){
        	}
        	
        	public void run (int n) {
        		rate1 = lists.get(n);
        		for (int j = n + 1; j < lists.size(); j++) {
        			
        			// If the items don't belong in the same bucket.
        			rate2 = lists.get(j);
    				if(lsh.getItemBucket(rate1.getMovieId()) !=
    						lsh.getItemBucket(rate2.getMovieId())) {
    					computeSimilarity(rate1.getMovieId(), rate2.getMovieId());
    				}
        		}
        	}
        });
	}
	
	public void computeSimilarity(int movieID1, int movieId2) {
		if(similarityArray[Sindex[movieID1]][Sindex[movieId2]] != 0.0d) {
			return;
		}
		
		double similarity, sum, pi, pj, averageItem1Rating, averageItem2Rating;
		sum = 0; pi = 0; pj = 0;
		ListIterator<Rating> ratingIterator1, ratingIterator2;
		averageItem1Rating = averageItemRating.get(movieID1);
		averageItem2Rating = averageItemRating.get(movieId2);

		Rating tmpValue1, tmpValue2;
		int user1, user2;
		double rate1, rate2;

		ratingIterator1 = itemToUserMap.get(movieID1).listIterator();
		while (ratingIterator1.hasNext()) {
			tmpValue1 = ratingIterator1.next();
			user1 = tmpValue1.getUser();
			rate1 = tmpValue1.getRating();

			ratingIterator2 = itemToUserMap.get(movieId2).listIterator();
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
        similarityArray[Sindex[movieID1]][Sindex[movieId2]] = similarity;
	}
	
	
	
	/**
	 * Find the top three recommend items for the given user.
	 * @param userID
	 */
	public void findRecommendationForUser(int userID) {	
		List<Rating> userExistingRatings = userToItemMap.get(userID);
		ArrayList<Integer> mostSimilar = new ArrayList<>();
		
		// Find the most similar item to every item this user has rated.
		for (Rating currentRating: userExistingRatings) {
			double currentMostSimilar = Double.MIN_VALUE;
			int currentMostSimilarItem = -1;
			int itemId = currentRating.getMovieId();
			int index = Sindex[itemId];
			for(int i = 0; i < similarityArray[index].length; i++) {
				if(similarityArray[index][i] > currentMostSimilar) {
					currentMostSimilar = similarityArray[index][i];
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
				 
				if(similarityArray[firstItemIdIndex][secondItemIdIndex] > similar1){
					similar3 = similar2;
					item3 = item2;
					similar2 = similar1;
					item2 = item1;
					similar1 = similarityArray[firstItemIdIndex][secondItemIdIndex];
					item1 = mostSimilar.get(index);
				}
				else if(similarityArray[firstItemIdIndex][secondItemIdIndex] > similar2) {
					similar3 = similar2;
					item3 = item2;
					similar2 = similarityArray[firstItemIdIndex][secondItemIdIndex];
					item2 = mostSimilar.get(index);
				}
				else if(similarityArray[firstItemIdIndex][secondItemIdIndex] > similar3) {
					similar3 = similarityArray[firstItemIdIndex][secondItemIdIndex];
					item3 = mostSimilar.get(index);
				}
			}
		}
		System.out.println(item1);
		System.out.println(item2);
		System.out.println(item3);
	}

	/**
	 * Check if the user has already rated the given item.
	 * @param userID
	 * @param itemId
	 * @return
	 */
	public boolean isWatched(int userID, int itemId) {
		List<Rating> userExistingRatings = userToItemMap.get(userID);
		for(Rating rating : userExistingRatings) {
			if (rating.getMovieId() == itemId)
				return true;
		}
		return false;
	}
	
}
