import java.util.Comparator;

public class RatingComparator implements Comparator<Rating> {
    @Override
    public int compare(Rating v1, Rating v2) {
        int user1 = v1.getUser();
        int user2 = v2.getUser();
        if(user1 > user2)
            return 1;
        else if (user1 < user2)
            return -1;
        else
            return 0;
    }
}