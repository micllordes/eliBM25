package search;

import retrieval.Constants;
import retrieval.OneStepRetriever;

import java.io.*;
import java.util.Map;

public class ExpandedRetrieval {

    public static void main(String[] args) throws Exception {
        OneStepRetriever retriever = new OneStepRetriever();
        File res_dir = new File(Constants.NN_RES_DIR);
        File[] files = res_dir.listFiles();
        for (File neuralFile: files) {
            String[] resFileNames = neuralFile.getAbsolutePath().split("\\\\");
            String fname = resFileNames[resFileNames.length - 1].replaceAll(".res", ".tsv");
            Map<String, String> queries = retriever.loadQueries(Constants.EXPANDED_QUERY_PATH_BFS + fname);
            new File(Constants.EXPANDED_RESULTS_PATH_BFS).mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(Constants.EXPANDED_RESULTS_PATH_BFS + resFileNames[resFileNames.length - 1]));
            System.out.println(fname);
            for (Map.Entry<String, String> query : queries.entrySet()) {
                String results = retriever.retrieveTopDocs(query, Constants.NUM_WANTED);
                bw.write(results);
            }
            bw.close();
        }
    }
}
