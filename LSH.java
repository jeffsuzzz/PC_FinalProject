import java.util.*;

public class LSH {



    ArrayList<BitSet> shingleMatrix;

    Map<Integer, List<Rating>> itemToUserMap;

    Map<Integer, List<Rating>> userToItemMap;

    ArrayList< ArrayList<Integer> > signatureMatrix;


    Map<Integer, Integer> itemToMatrixColIndex;

    Map<Integer, Integer> matrixColIndexToItem;

    Map<Integer, Integer> userToMatrixRowlIndex;

    Map<Integer, Integer> matrixRowIndexToUser;

    Map<Integer, Integer> itemToBucket;

    Map<Integer, ArrayList<Integer>> buckets;

    public static final int NUMBER_OF_PERMUTATIONS = 100;

    public static final int BAND_SIZE = 4;

    int bucketId;


    LSH(Map<Integer, List<Rating>> itemToUserMap, Map<Integer, List<Rating>> userToItemMap) {

        this.itemToUserMap = itemToUserMap;
        this.userToItemMap = userToItemMap;
        shingleMatrix = new ArrayList<>(userToItemMap.size());
        signatureMatrix = new ArrayList<>(NUMBER_OF_PERMUTATIONS);
        buckets = new HashMap<Integer, ArrayList<Integer> >() ;
        itemToMatrixColIndex  = new HashMap<Integer, Integer>() ;
        matrixColIndexToItem  = new HashMap<Integer, Integer>() ;
        userToMatrixRowlIndex = new HashMap<Integer, Integer>() ;
        matrixRowIndexToUser  = new HashMap<Integer, Integer>() ;

        initUserAndItemMaps();
        itemToBucket = new HashMap<>();
        bucketId = 1;

    }

    public void initUserAndItemMaps() {

        int userIndex = 0;
        for(int userID : userToItemMap.keySet()) {
            userToMatrixRowlIndex.put(userID, userIndex);
            matrixRowIndexToUser.put(userIndex,userID);
            userIndex++;
        }

        int itemIndex = 0;
        for(int itemID : itemToUserMap.keySet()) {
            itemToMatrixColIndex.put(itemID, itemIndex);
            matrixColIndexToItem.put(itemIndex,itemID);
            itemIndex++;
        }



    }


    public void createGroups() {
        buildShingleMatrix();
        System.out.println("Build Shingle Finished");
        initShingleMatrix();
        System.out.println("Init Shingle Finished");
        buildSignatureMatrix();
        System.out.println("Build Signature Finished");
        createBucket();
        System.out.println("Create Bucket Finished");

    }
    public void buildShingleMatrix() {

        int noOfUsers = userToMatrixRowlIndex.size();
        int noOfItems = itemToMatrixColIndex.size();

        System.out.println(noOfItems);
        System.out.println(noOfUsers);
        for( int index = 0; index < noOfUsers; index++) {
            BitSet itemBitSet = new BitSet(noOfItems);
            //set all the bits to 0 intially
            itemBitSet.clear();
            shingleMatrix.add(itemBitSet);
        }

    }

    public void initShingleMatrix() {


        for(int userID: userToItemMap.keySet()) {

            List<Rating>  ratings = userToItemMap.get(userID);
            for(Rating rating: ratings) {
                int userIndex = userToMatrixRowlIndex.get(userID);
                int itemIndex = itemToMatrixColIndex.get(rating.movieId);
                shingleMatrix.get(userIndex).set(itemIndex);
            }
        }
    }

    public void buildSignatureMatrix() {

        for( int index = 0; index < NUMBER_OF_PERMUTATIONS; index++) {
            Collections.shuffle(shingleMatrix);
            populateNextSignature();

        }
    }

     public void populateNextSignature() {

        int noOfItems = itemToMatrixColIndex.size();

        ArrayList<Integer> signature = new ArrayList<>(noOfItems);
        for(int index = 0; index < noOfItems; index++) {

            int row = 0;
            boolean addStatus = false;
            for(BitSet bitSet : shingleMatrix ) {
                if(bitSet.get(index)) {
                    addStatus = true;
                    signature.add(row);
                    break;
                }
                row++;
            }
            if(!addStatus) {
                signature.add(row);
            }

        }
        signatureMatrix.add(signature);

    }


    public void createBucket() {


        int end = signatureMatrix.size()/BAND_SIZE;
        for (int bandIndex = 0; bandIndex < end; bandIndex++) {


            computeBandSimilarity(bandIndex);


        }
    }

    public void allocateSameBucket(int item1Index, int item2Index) {

        int item1Id = matrixColIndexToItem.get(item1Index);
        int item2Id = matrixColIndexToItem.get(item2Index);
        if(!itemToBucket.containsKey(item1Id) &&
                !itemToBucket.containsKey(item2Id)) {

            itemToBucket.put(item1Id, bucketId);
            itemToBucket.put(item2Id, bucketId);
            buckets.put(bucketId, new ArrayList<Integer>());
            buckets.get(bucketId).add(item1Id);
            buckets.get(bucketId).add(item2Id);
            bucketId++;
            //System.out.println(bucketId);

        }
        else if( !itemToBucket.containsKey(item1Id)) {
            int bucket = itemToBucket.get(item2Id);
            itemToBucket.put(item1Id, bucket);
            buckets.get(bucket).add(item1Id);
        }
        else if(!itemToBucket.containsKey(item2Id)) {
            int bucket = itemToBucket.get(item1Id);
            itemToBucket.put(item2Id, bucket);
            buckets.get(bucket).add(item2Id);
        }
        else {

            int bucket1 = itemToBucket.get(item1Id);
            int bucket2 = itemToBucket.get(item2Id);
            if(bucket1 == bucket2) {
                return;
            }
            if(buckets.get(bucket1).size() >= buckets.get(bucket2).size()) {
                for(int item : buckets.get(bucket2))
                    itemToBucket.put(item,bucket1);
                buckets.get(bucket1).addAll(buckets.get(bucket2));
                buckets.remove(bucket2);
            }
            else {
                for(int item : buckets.get(bucket1))
                    itemToBucket.put(item,bucket2);
                buckets.get(bucket2).addAll(buckets.get(bucket1));
                buckets.remove(bucket1);

            }

        }


    }

    public void allocateDiffBucket(int item1Index, int item2Index) {

        int item1Id = matrixColIndexToItem.get(item1Index);
        int item2Id = matrixColIndexToItem.get(item2Index);
        if(!itemToBucket.containsKey(item1Id)) {
            itemToBucket.put(item1Id, bucketId);
            buckets.put(bucketId, new ArrayList<Integer>());
            buckets.get(bucketId).add(item1Id);
            //System.out.println(bucketId);
            bucketId++;

        }
        if(!itemToBucket.containsKey(item2Id)) {
            itemToBucket.put(item2Id, bucketId);
            buckets.put(bucketId, new ArrayList<Integer>());
            buckets.get(bucketId).add(item2Id);
            //System.out.println(bucketId);
            bucketId++;
        }


    }



    public void computeBandSimilarity(int bandIndex) {

        int noOfItems = itemToMatrixColIndex.size();
        for(int item1Index = 0; item1Index < noOfItems - 1; item1Index++ ) {

            for (int item2Index = item1Index + 1; item2Index < noOfItems; item2Index++) {

                if(isSimilar(item1Index, item2Index,bandIndex)) {

                    allocateSameBucket(item1Index, item2Index);
                }
                else {
                    allocateDiffBucket(item1Index, item2Index);
                }
            }
        }
    }


    public boolean isSimilar(int item1Index, int item2Index, int bandIndex) {

        int startIndex = bandIndex * BAND_SIZE;
        int endIndex = startIndex + BAND_SIZE;
        //System.out.println(startIndex);
        //System.out.println(endIndex);
        //System.out.println(item1Index);
        //System.out.println(item2Index);
        while(startIndex < endIndex && startIndex < signatureMatrix.size()) {
            if(signatureMatrix.get(startIndex).get(item1Index) != signatureMatrix.get(startIndex).get(item2Index)) {
                return false;
            }
            startIndex++;
        }

        return true;
    }



    public void print() {

        int count = 0;
        int bucketCount = 0;
        for(int bucket : buckets.keySet()){

            System.out.println("bucket" + bucket);
            ArrayList<Integer> list = buckets.get(bucket);
            System.out.print("Items:" );
            count +=list.size();
            for( int item : list) {
                System.out.print(item + " ");
            }
            System.out.println();
            System.out.println("count:" + list.size());
            bucketCount++;
        }
        System.out.println("number of buckets" + bucketCount);
    }




}
