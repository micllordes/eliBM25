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

public class Greedy {


    private static Pair<String, Double> greedySearch(Map<String, List<RetrievedDocTermInfo>> termWeightsAll, Map.Entry<String, String> queryEntry, OneStepRetriever retriever, AllRetrievedResults neuralRes) throws Exception {
        String qid = queryEntry.getKey();
        MsMarcoQuery query = new MsMarcoQuery(qid, queryEntry.getValue());
        List<ResultTuple> nnTopDocs = neuralRes.getRetrievedResultsForQueryId(qid).getTuples();
        List<RetrievedDocTermInfo> allFdbkTerms = termWeightsAll.get(qid);
        List<RetrievedDocTermInfo> sublistFdbkTerms = allFdbkTerms.subList(0, Math.min(allFdbkTerms.size(), Constants.GREEDY_VOCAB_TERMS));
        System.out.println(queryEntry.getValue());
        ArrayList<Pair<String, Double>> topKterms = new ArrayList<>();
        for (String term : query.getQueryTermsAsString()) {
            ArrayList<String> bm25TopDocs = retriever.retrieveQuery(term, Constants.BFS_TOP_DOCS_NUM);
            double similarity = SimilarityMeasures.rbo(bm25TopDocs, nnTopDocs);

            for (RetrievedDocTermInfo termInfo : sublistFdbkTerms) {
                String addedTerm = termInfo.getTerm();
                String newQuery = term + " " + addedTerm;
                ArrayList<String> newTopDocs = retriever.retrieveQuery(newQuery, Constants.BFS_TOP_DOCS_NUM);
                double newSimilarity = SimilarityMeasures.rbo(newTopDocs, nnTopDocs);
                topKterms.add(Pair.of(addedTerm, newSimilarity - similarity));
            }
        }
        ;
        StringBuilder finalQuery = new StringBuilder();

        Comparator<Pair<String,Double>> cmp = Comparator.comparing((Pair::getRight));
        topKterms.sort(cmp.reversed());
        int i = 0;
        while (finalQuery.toString().split(" ").length < Constants.BFS_MAX_DEPTH) {
            String term = topKterms.get(i).getLeft();
            if (!finalQuery.toString().contains(term)){
                finalQuery.append(topKterms.get(i).getLeft()).append(" ");
            }
            i++;
        }
        ArrayList<String> finalTopDocs = retriever.retrieveQuery(finalQuery.toString().toString(), Constants.BFS_TOP_DOCS_NUM);
        double similarity = SimilarityMeasures.rbo(finalTopDocs, nnTopDocs);
        return Pair.of(finalQuery.toString(), similarity);
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
                FdbkTermStats fdbkTermStats = new FdbkTermStats(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN, neuralFileName);
                Map<String, List<RetrievedDocTermInfo>> termWeightsAll = fdbkTermStats.computeFdbkTermWeights(Constants.BFS_TOP_DOCS_NUM);
                ArrayList<String[]> queryList = new ArrayList<>(queries.size());
                ArrayList<Pair<String, Double>> expandedQueries = new ArrayList<>(queries.size());
                for (Map.Entry<String, String> query : queries.entrySet()) {
                    Pair<String, Double> best_pair = greedySearch(termWeightsAll, query, retriever, neuralRes);
                    System.out.println(best_pair);
                    expandedQueries.add(best_pair);
                    queryList.add(new String[]{query.getKey(), query.getValue()});
                }
                String[] resFileNames = neuralFileName.split("\\\\");
                String fname = resFileNames[resFileNames.length - 1].replaceAll(".res", ".tsv");
                new File(Constants.EXPANDED_QUERY_PATH_GREEDY).mkdirs();
                BufferedWriter bw = new BufferedWriter(new FileWriter(Constants.EXPANDED_QUERY_PATH_GREEDY + fname));
                for (int i = 0; i < expandedQueries.size(); i++) {
                    Pair<String, Double> p = expandedQueries.get(i);
                    String[] query = queryList.get(i);
                    bw.write(query[0] + "\t" + p.getLeft() + "\t" + p.getRight() + "\t" + query[1]);
                    bw.newLine();
                }
                bw.close();
                System.out.println(expandedQueries.stream().mapToDouble(Pair::getRight).average().getAsDouble());

            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }
}
