import java.util.List;
import java.util.ListIterator;

/**
 * This class will be used as value for minimum hash key.
 */
public class KV_pairs {
	int movieId;
    List<Rating> user_rateList;
    public KV_pairs() {}

    public KV_pairs(int movieId, List<Rating> user_rateList) {
        this.movieId = movieId;
        this.user_rateList = user_rateList;
    }

    public int GetMovieID() {
        return this.movieId;
    }

    /**
     * The list of user-rating of this item.
     */
    public List<Rating> GetListofValues() {
        return this.user_rateList;
    }

    public void Print() {
        System.out.print(movieId+", (");
        ListIterator<Rating> it = user_rateList.listIterator();
        while (it.hasNext()) {
            it.next().Print();
        }
        System.out.println(")");
    }
}
