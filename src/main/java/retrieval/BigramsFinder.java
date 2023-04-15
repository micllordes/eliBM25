package retrieval;
import indexing.MsMarcoIndexer;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BigramsFinder {
    List<String[]> queries;
    Map<String, Double> cooccurProbs;
    Map<String, Integer> bigramFreqs;

    static String bigramKey(String u, String v) { return u + "^" + v;}

    BigramsFinder(List<String[]> queries) {
        cooccurProbs = new HashMap<>();
        bigramFreqs = new HashMap<>();

        Integer count;
        this.queries = queries;

        for (String[] query: queries) {
            int l = query.length;
            for (int i=0; i < l-1; i++) {
                String u = query[i];
                for (int j=i+1; j < l; j++) {
                    String v = query[j];
                    String key = bigramKey(u, v);
                    count = bigramFreqs.get(key);
                    if (count == null) count = 0;
                    bigramFreqs.put(key, count+1);
                }
            }
        }

        bigramFreqs = bigramFreqs.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
    }

    Map<String, Integer> frequentBigrams() {
        return bigramFreqs
            .entrySet().stream()
            .limit(100)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
    }

    public static void main(String[] args) {
        try {
            List<String[]> queries = FileUtils.readLines(new File(Constants.QUERY_FILE_TRAIN), StandardCharsets.UTF_8)
                    .stream()
                    .map(x -> x.split("\t"))
                    .collect(Collectors.toMap(
                        x -> x[0],
                        x -> MsMarcoIndexer.analyze(MsMarcoIndexer.constructAnalyzer(), x[1]).split("\\s+")))
                    .values()
                    .stream().collect(Collectors.toList())
            ;
            BigramsFinder bigramsFinder = new BigramsFinder(queries);
            bigramsFinder.frequentBigrams().entrySet().stream().forEach(System.out::println);
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

}
