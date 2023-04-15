package retrieval;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import indexing.MsMarcoIndexer;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.util.BytesRef;

import org.apache.lucene.store.FSDirectory;
import qrels.AllRelRcds;
import qrels.PerQueryRelDocs;

public class SupervisedRLM extends OneStepRetriever {

    AllRelRcds rels, eval_rels;
    Map<String, TermDistribution> termDistributions;

    static final String DELIM = "^";

    public SupervisedRLM(String qrelFile, String queryFile) throws Exception {
        super(queryFile);
        rels = new AllRelRcds(qrelFile);
        eval_rels = new AllRelRcds(Constants.QRELS_TEST);
        termDistributions = new HashMap<>();
    }

    int getDocOffset(String docId) throws Exception {
        Query tq = new TermQuery(new Term(Constants.ID_FIELD, docId));
        TopDocs topDocs = searcher.search(tq, 1);
        return topDocs.scoreDocs[0].doc;
    }

    Map<String, Double> makeLMTermWts(String docId) throws Exception {
        return makeLMTermWts(getDocOffset(docId), true);
    }

    Map<String, Double> makeLMTermWts(int docId) throws Exception {
        return makeLMTermWts(docId, true);
    }

    Map<String, Double> makeLMTermWts(String docId, boolean idfWeighting) throws Exception {
        return makeLMTermWts(getDocOffset(docId), idfWeighting);
    }

    Map<String, Double> makeLMTermWts(int docId, boolean idfWeighting) throws Exception {
        BytesRef term;
        String termText;
        int tf;
        Map<String, Integer> vec = new HashMap<>();

        // In MSMARCO, Lucene doc offsets and ids are identical
        Terms tfvector = reader.getTermVector(docId, Constants.CONTENT_FIELD);
        TermsEnum termsEnum = tfvector.iterator(); // access the terms for this field

        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            termText = term.utf8ToString();
            tf = (int)termsEnum.totalTermFreq();

            Integer wt = vec.get(termText);
            if (wt == null) {
                wt = 0;
            }
            wt++;
            vec.put(termText, tf);
        }

        Map<String, Double> idfWeighted =
            vec.entrySet().stream()
            .collect(Collectors.toMap(e->e.getKey(), e-> (double)e.getValue()));
        double sumTf = idfWeighted.values().stream().mapToDouble(x->x).sum();

        if (idfWeighting) {
            idfWeighted = vec.entrySet()
            .stream()
            .collect(Collectors
            .toMap(
                e -> e.getKey(),
                e -> {
                    try {
                        return Math.log(1 +
                        Constants.LAMBDA_ODDS *
                            e.getValue() / (double) sumTf * // tf
                            reader.numDocs() / reader.docFreq(new Term(Constants.CONTENT_FIELD, e.getKey())) // idf
                        );
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        return null;
                    }
                }
            )
            );
        }
        // L2 Normalise
        double l2Norm = TermDistribution.l2Norm(idfWeighted);
        return idfWeighted.entrySet().stream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue()/l2Norm));
    }

    List<MsMarcoQuery> loadQueriesAsList(String queryFile) throws Exception {
        return
            FileUtils.readLines(new File(queryFile), StandardCharsets.UTF_8)
            .stream()
            .map(x -> x.split("\t"))
            .map(x -> new MsMarcoQuery(x[0], x[1]))
            .collect(Collectors.toList())
        ;
    }


    void prune() {
        for (TermDistribution td: termDistributions.values()) {
            td.cooccurProbs = td.cooccurProbs.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .limit(Constants.NUM_TOP_TERMS) // keep only top terms
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new))
            ;
        }
    }

    int numTermMatches(String[] queryTerms, Map<String, Double> docTermWts) {
        int count = 0;
        for (String qTerm: queryTerms) {
            if (docTermWts.keySet().contains(qTerm))
                count++;
        }
        return count;
    }

    void fit(String qid, String qText) throws Exception {
        PerQueryRelDocs relDocIds = rels.getRelInfo(qid);
        if (relDocIds == null)
            return;

        String[] qTerms = qText.split("\\s+");

        for (String docId: relDocIds.getRelDocs()) {
            Map<String, Double> termWts = makeLMTermWts(docId);
            //if (numTermMatches(qTerms, termWts) < Constants.NUM_QUERY_TERM_MATCHES)
            //    continue;

            for (String q: qTerms) {
                // each query term contains a vector of P(w|q)
                TermDistribution termDistribution = termDistributions.get(q);
                if (termDistribution == null) {
                    termDistribution = new TermDistribution(q);
                }
                termDistribution.update(q, termWts);
                termDistributions.put(q, termDistribution);
            }
        }
    }

    void fit() throws Exception {
        int count = 0;
        System.out.println("#Training queries: " + queries.entrySet().size());

        for (Map.Entry<String, String> e: queries.entrySet()) {
            fit(e.getKey(), e.getValue());
            if (count++ % 100 == 0)
                System.out.print(String.format("Trained for %d queries\r", count-1));
        }
        System.out.println();

        normalizeCooccurStats();
    }

    void normalizeCooccurStats() {
        // normalize
        termDistributions.values().stream().forEach(
            td -> td.cooccurProbs.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) // sort descending term weights
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e2/TermDistribution.l2Norm(td.cooccurProbs), LinkedHashMap::new)) // normalize
        )
        ;
    }

    void saveToDisk() { // already sorted by term weights
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(Constants.SAVED_MODEL))) {
            StringBuilder b = new StringBuilder();
            for (TermDistribution termDistribution: termDistributions.values()) {
                b.setLength(0);
                b.append(termDistribution.queryTerm).append("\t");
                for (Map.Entry<String, Double> e: termDistribution.cooccurProbs.entrySet()) {
                    b.append(e.getKey()).append(DELIM).append(e.getValue()).append(" ");
                }
                bw.write(b.toString());
                bw.newLine();
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    public void loadFromDisk() {
        System.out.println("Loading trained Supervised RLM from disk...");
        try (BufferedReader br = new BufferedReader(new FileReader(Constants.SAVED_MODEL))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 2)
                    continue;

                String queryTerm = parts[0];
                TermDistribution termDistribution = new TermDistribution(queryTerm);
                String[] tokens = parts[1].split("\\s+");
                for (String token: tokens) {
                    termDistribution.add(token);
                }
                termDistributions.put(queryTerm, termDistribution);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
        prune();
    }

    // Use the supervised RLM to rerank results
    TopDocs rerank(String qid, String queryText, TopDocs retrievedRes) throws Exception {
        String[] queryTerms = queryText.split("\\s+");
        ScoreDoc[] rerankedScoreDocs = new ScoreDoc[retrievedRes.scoreDocs.length];

        int i = 0;
        double p_R_d = 0;

        for (ScoreDoc sd: retrievedRes.scoreDocs) {
            rerankedScoreDocs[i] = new ScoreDoc(sd.doc, sd.score);
            p_R_d = 0;

            for (String qTerm: queryTerms) {
                TermDistribution termDistribution_q = this.termDistributions.get(qTerm);
                if (termDistribution_q != null) {
                    Map<String, Double> doc_term_wts = makeLMTermWts(sd.doc);
                    p_R_d += termDistribution_q.cosineSim(doc_term_wts);
                }
            }
            rerankedScoreDocs[i].score = (float)p_R_d * sd.score;
            i++;
        }

        rerankedScoreDocs = Arrays.stream(rerankedScoreDocs)
            .sorted((o1, o2) -> o1.score < o2.score? 1: o1.score==o2.score? 0 : -1) // sort descending by sims
            .collect(Collectors.toList())
            .toArray(rerankedScoreDocs)
        ;

        return new TopDocs(new TotalHits(rerankedScoreDocs.length, TotalHits.Relation.EQUAL_TO), rerankedScoreDocs);
    }

    public void retrieve() throws Exception {
        Map<String, String> testQueries = loadQueries(Constants.QUERY_FILE_TEST);
        testQueries
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey(),
                    e -> MsMarcoIndexer.normalizeNumbers(e.getValue()
                )
            )
        )
        ;

        TopDocs topDocs = null;
        Map<String, TopDocs> topDocsMap = new HashMap<>(queries.size());

        for (Map.Entry<String, String> e : testQueries.entrySet()) {
            String qid = e.getKey();
            String queryText = e.getValue();

            Query luceneQuery = Constants.QRYEXPANSION? makeQueryWithExpansionTerms(qid, queryText) : makeQuery(queryText);

            System.out.println(String.format("Retrieving for query %s: %s", qid, luceneQuery));
            topDocs = searcher.search(luceneQuery, Constants.NUM_WANTED); // descending BM25
            topDocsMap.put(qid, topDocs);
        }

        processQueries(testQueries, topDocsMap, Constants.RES_FILE, false);
        if (Constants.RERANK)
            processQueries(testQueries, topDocsMap, Constants.RES_FILE_RERANKED, true);
    }

    void processQueries(Map<String, String> testQueries, Map<String, TopDocs> topDocsMap, String resFile, boolean rerank) throws Exception {
        TopDocs topDocs = null;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(resFile))) {
            for (Map.Entry<String, String> e : testQueries.entrySet()) {
                String qid = e.getKey();
                String queryText = e.getValue();

                topDocs = rerank? rerank(qid, queryText, topDocsMap.get(qid)) : topDocsMap.get(qid); // sort by ascending KLdiv

                AtomicInteger rank = new AtomicInteger(1);
                int rel = 0;
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    String docName = reader.document(sd.doc).get(Constants.ID_FIELD);
                    PerQueryRelDocs perQueryRelDocs = eval_rels.getRelInfo(qid);
                    if(perQueryRelDocs != null) {
                        rel = perQueryRelDocs.isRel(docName) ? 1 : 0;

                        bw.write(String.format(
                                "%s\tQ0\t%s\t%d\t%.6f\t%s\t%d",
                                qid, docName,
                                rank.getAndIncrement(), sd.score,
                                reader.document(sd.doc).get(Constants.CONTENT_FIELD), rel
                        ));
                        bw.newLine();
                    }
                }
            }
        }
    }

    BooleanQuery makeQueryWithExpansionTerms(String qid, String queryText) {
        // original query terms
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        Set<String> queryTerms =
                Arrays.stream(MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), queryText)
                .split("\\s+"))
                .collect(Collectors.toSet())
        ;

        BoostQuery tq;
        for (String token: queryTerms) {
            tq = new BoostQuery(new TermQuery(new Term(Constants.CONTENT_FIELD, token)), 1.0f);
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));

            // expansion terms
            TermDistribution td = termDistributions.get(token);
            if (td == null || td.cooccurProbs.isEmpty())
                continue;

            for (Map.Entry<String, Double> e: td.cooccurProbs.entrySet()) {
                if (queryTerms.contains(e.getKey()))
                    continue;

                tq = new BoostQuery(
                        new TermQuery(new Term(Constants.CONTENT_FIELD, e.getKey())),
                        (float)e.getValue().doubleValue()); // these weights are less than 1

                qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
            }
        }
        return qb.build();
    }

    public static void main(String[] args) {
        if (args.length==0) {
            args = new String[1];
            args[0] = "rlm_terms/rlm_test.txt";
        }

        try {
            SupervisedRLM supervisedRLM = new SupervisedRLM(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN);

            if (new File(Constants.SAVED_MODEL).exists() == false) {
                // Train on qrels
                System.out.println("Training SRLM on the MSMARCO training set");
                supervisedRLM.fit();
                supervisedRLM.saveToDisk();
                supervisedRLM.prune();
            }
            else {
                supervisedRLM.loadFromDisk();
            }

            // Retrieve on test
            System.out.println("Writing out results file in " + args[0]);
            supervisedRLM.retrieve();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
