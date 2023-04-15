package search;

import fdbk.RetrievedDocTermInfo;
import indexing.MsMarcoIndexer;
import org.apache.commons.lang3.tuple.Pair;
import qrels.AllRetrievedResults;
import qrels.ResultTuple;
import retrieval.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;


public class BFS {


    private static ArrayList<String> sample(List<RetrievedDocTermInfo> fdbk, int numWanted){
        double total = fdbk.stream().mapToDouble(RetrievedDocTermInfo::getWeight).sum();
        Random random = new Random();
        ArrayList<String> terms = new ArrayList<>(numWanted);
        while (terms.size()<numWanted && terms.size()<fdbk.size()){
            double index = random.nextDouble()*total;
            for (RetrievedDocTermInfo term: fdbk){
                index = index - term.getWeight();
                if(index<=0){
                    if (!terms.contains(term.getTerm())){
                        terms.add(term.getTerm());
                        break;
                    }
                }
            }
        }
        return terms;
    }
    private static Pair<String, Double> bfs(Map<String, List<RetrievedDocTermInfo>> termWeightsAll, Map.Entry<String, String> queryEntry, OneStepRetriever retriever, AllRetrievedResults neuralRes) throws Exception {
        Comparator<Pair<String, Double>> comp = Comparator.comparingDouble(Pair::getRight); //java compiler can't understand this syntax without intermediate variable
        String qid = queryEntry.getKey();
        MsMarcoQuery query = new MsMarcoQuery(qid, queryEntry.getValue());
        List<ResultTuple> nnTopDocs = neuralRes.getRetrievedResultsForQueryId(qid).getTuples();
        List<RetrievedDocTermInfo> allFdbkTerms = termWeightsAll.get(qid);
        System.out.println(queryEntry.getValue());
        Pair<String, Double> bestState = null;
        for (String term : query.getQueryTermsAsString()) {
            PriorityQueue<Pair<String, Double>> queue = new PriorityQueue<>(Constants.BFS_TOP_DOCS_NUM, comp.reversed());
            ArrayList<String> bm25TopDocs = retriever.retrieveQuery(term, Constants.BFS_TOP_DOCS_NUM);
            double similarity = SimilarityMeasures.rbo(bm25TopDocs, nnTopDocs);
            Pair<String, Double> initialState = Pair.of(term, similarity);
            if (bestState == null) {
                bestState = initialState;
            }
            queue.add(initialState);
            int statesExplored = 0;
            while (!queue.isEmpty() && statesExplored< Constants.BFS_MAX_EXPLORATION) {
                Pair<String, Double> currentBest = queue.poll();
                statesExplored++;

                if (currentBest.getRight() > bestState.getRight()) {
                    bestState = currentBest;
                }
                for (String vocabTerm : sample(allFdbkTerms, Constants.BFS_VOCAB_TERMS)) {
                    String newQuery;
                    if (!currentBest.getLeft().contains(vocabTerm)) {
                        newQuery = currentBest.getLeft() + " " + vocabTerm;
                    } else {
                        newQuery = currentBest.getLeft();
                    }
                    if (queue.stream().noneMatch((p1) -> p1.getLeft().equals(newQuery)) && newQuery.split("\\s").length < Constants.BFS_MAX_DEPTH) {
                        ArrayList<String> newTopDocs = retriever.retrieveQuery(newQuery, Constants.BFS_TOP_DOCS_NUM);
                        double newSimilarity = SimilarityMeasures.rbo(newTopDocs, nnTopDocs);
                        if (newSimilarity >= currentBest.getRight()) {
                            queue.add(Pair.of(newQuery, newSimilarity));
                        }
                        if (newSimilarity > bestState.getRight()) {
                            bestState = Pair.of(newQuery, newSimilarity);
                        }
                    }
                }
            }
        }
            return bestState;
    }





    public static void main(String[] args) {
        try{
            OneStepRetriever retriever = new OneStepRetriever();
            Map<String, String> queries = retriever.loadQueries(Constants.QUERY_FILE_TEST);
            queries
                    .entrySet()
                    .stream()
                    .collect(
                            Collectors.toMap(
                                    e -> e.getKey(),
                                    e -> MsMarcoIndexer.normalizeNumbers(e.getValue())
                            )
                    );
            File res_dir = new File(Constants.NN_RES_DIR);
            File[] files = res_dir.listFiles();

            for (File neuralFile: files) {
                String neuralFileName = neuralFile.getAbsolutePath();
                AllRetrievedResults neuralRes = new AllRetrievedResults(neuralFileName);
                FdbkTermStats fdbkTermStats = new FdbkTermStats(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN, neuralFile.getAbsolutePath());
                Map<String, List<RetrievedDocTermInfo>> termWeightsAll = fdbkTermStats.computeFdbkTermWeights(Constants.BFS_TOP_DOCS_NUM);
                ArrayList<String[]> queryList = new ArrayList<>(queries.size());
                ArrayList<Pair<String, Double>> expandedQueries = new ArrayList<>(queries.size());
                for (Map.Entry<String, String> query : queries.entrySet()) {
                    Pair<String, Double> best_pair = bfs(termWeightsAll, query, retriever, neuralRes);
                    System.out.println(best_pair);
                    expandedQueries.add(best_pair);
                    queryList.add(new String[]{query.getKey(), query.getValue()});
                }
                String[] resFileNames = neuralFileName.split("\\\\");
                String fname = resFileNames[resFileNames.length - 1].replaceAll(".res", ".tsv");
                new File(Constants.EXPANDED_QUERY_PATH_BFS).mkdirs();
                BufferedWriter bw = new BufferedWriter(new FileWriter(Constants.EXPANDED_QUERY_PATH_BFS + fname));
                for (int i = 0; i < expandedQueries.size(); i++) {
                    Pair<String, Double> p = expandedQueries.get(i);
                    String[] query = queryList.get(i);
                    bw.write(query[0] + "\t" + p.getLeft() + "\t" + p.getRight() + "\t" + query[1]);
                    bw.newLine();
                }
                bw.close();
            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

}
