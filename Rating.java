public  class Rating{


    int movieId;
    int userId;
    double rating;
    public Rating() {}

    public Rating(int movieID, int userID, double rating) {
        this.movieId = movieID;
        this.userId = userID;
        this.rating = rating;
    }

    public int getMovieId() {
        return this.movieId;
    }
    public int getUser() {
        return this.userId;
    }

    public double getRating() {
        return this.rating;
    }

    public void Print() {
        System.out.print("(" + movieId + ","+ userId +","+rating+") ");
    }
}