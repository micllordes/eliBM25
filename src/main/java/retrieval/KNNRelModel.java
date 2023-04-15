package retrieval;

import fdbk.RelevanceModelConditional;
import fdbk.RelevanceModelIId;
import fdbk.RetrievedDocTermInfo;
import indexing.MsMarcoIndexer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.store.FSDirectory;
import qrels.PerQueryRelDocs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class TermWt implements Comparable<TermWt> {
    String term;
    double wt;

    TermWt(String term, double wt) {
        this.term = term;
        this.wt = wt;
    }

    @Override
    public int compareTo(TermWt o) {
        return Double.compare(this.wt, o.wt);
    }
}

public class KNNRelModel extends SupervisedRLM {
    IndexReader qIndexReader;
    IndexSearcher qIndexSearcher;

    static Analyzer analyzer = MsMarcoIndexer.constructAnalyzer();

    KNNRelModel(String qrelFile, String queryFile) throws Exception {
        super(qrelFile, queryFile);
        System.out.println(Constants.MSMARCO_QUERY_INDEX);
        qIndexReader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_QUERY_INDEX).toPath()));
        qIndexSearcher = new IndexSearcher(qIndexReader);
        qIndexSearcher.setSimilarity(new LMDirichletSimilarity(Constants.MU));
    }

    List<MsMarcoQuery> knnQueries(String currentQuery, int k) {
        try {
            Query luceneQuery = makeQuery(currentQuery);
            List<MsMarcoQuery> knnQueries = new ArrayList<>();

            TopDocs knnQueriesTopDocs = qIndexSearcher.search(luceneQuery, k);
            for (ScoreDoc sd : knnQueriesTopDocs.scoreDocs) {
                Document q = qIndexReader.document(sd.doc);
                knnQueries.add(
                        new MsMarcoQuery(q.get(Constants.ID_FIELD),
                            MsMarcoIndexer.analyze(analyzer, q.get(Constants.CONTENT_FIELD)),
                            sd.score)
                );
            }
            System.out.print(currentQuery + "\t");
            System.out.println(knnQueries.stream().map(x -> x.qText).collect(Collectors.joining("|")));

            return knnQueries;
        }
        catch (Exception ex) { return null; }
    }

    Query rocchioQE(MsMarcoQuery query, TopDocs topDocs) throws Exception {
        // original query terms
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        Set<String> queryTerms = query.getQueryTermsAsString();

        Map<String, Double> rel, nonrel;
        Map<String, Double> origQ = queryTerms.stream().collect(
                Collectors.toMap(x -> x, x -> Constants.ROCCHIO_ALPHA)); // orig query terms
        Map<String, Double> relAcc = new HashMap<>(), nonRelAcc = new HashMap<>();

        try {
            List<MsMarcoQuery> knnQueries = knnQueries(query.qText, Constants.K);
            for (MsMarcoQuery knnQ: knnQueries) {
                PerQueryRelDocs relDocIds = rels.getRelInfo(knnQ.qid);
                if (relDocIds == null) continue;

                for (String docId: relDocIds.getRelDocs()) {
                    rel = makeLMTermWts(docId);
                    mergeInto(rel, relAcc); // sum <- sum + beta*rel (rel and sum are vecs, beta is scalar)
                }

                List<Integer> nonRelDocIds = new ArrayList<>();
                for (ScoreDoc sd: topDocs.scoreDocs) {
                    if (!relDocIds.getRelDocs().contains(reader.document(sd.doc).get(Constants.ID_FIELD))) {
                        nonRelDocIds.add(sd.doc);
                        if (nonRelDocIds.size() >= Constants.ROCCHIO_NUM_NEGATIVE)
                            break;
                    }
                }
                for (Integer docId: nonRelDocIds) {
                    nonrel = makeLMTermWts(docId);
                    mergeInto(nonrel, nonRelAcc); // sum <- sum + beta*rel (rel and sum are vecs, beta is scalar)
                }

                mergeInto(relAcc, Constants.ROCCHIO_BETA/relDocIds.getRelDocs().size(), origQ);
                mergeInto(nonRelAcc, -1*Constants.ROCCHIO_GAMMA/nonRelDocIds.size(), origQ);
            }
            origQ = origQ.entrySet()
                .stream()
                .filter(e-> e.getValue() > 0)
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(Constants.NUM_EXPANSION_TERMS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new)
            );
        }
        catch (Exception ex) { ex.printStackTrace(); }

        origQ
        .entrySet().stream()
        .forEach(e ->
            qb.add(
                new BooleanClause(
                new BoostQuery(
                    new TermQuery(new Term(Constants.CONTENT_FIELD, e.getKey())),
                    (float)e.getValue().doubleValue()
                ),
                BooleanClause.Occur.SHOULD)
            )
        );

        Query expandedQuery = qb.build();
        return expandedQuery;
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

        try {
            termDistributions.clear();
            List<MsMarcoQuery> knnQueries = knnQueries(queryText, Constants.K);
            for (MsMarcoQuery knnQ: knnQueries) {
                fit(knnQ.qid, knnQ.qText);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        queryTerms.stream()
            .map(x-> new BoostQuery(new TermQuery(new Term(Constants.CONTENT_FIELD, x)), 1.0f))
            .forEach(tq -> qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD)));
        ;

        termDistributions
            .values()
            .stream()
            .flatMap(x->
                x.cooccurProbs
                .entrySet()
                .stream()
                .map(e -> new TermWt(e.getKey(), e.getValue()))
            )
            .sorted(Comparator.reverseOrder())
            .limit(Constants.NUM_EXPANSION_TERMS)
            .forEach(termWt ->
                qb.add(new BooleanClause(
                new BoostQuery(
                    new TermQuery(new Term(Constants.CONTENT_FIELD, termWt.term)),
                    (float)termWt.wt
                ),
                BooleanClause.Occur.SHOULD))
        );

        return qb.build();
    }

    void findKNNOfQueries() throws Exception {
        loadQueries(Constants.QUERY_FILE_TEST)
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey(),
                e -> MsMarcoIndexer.normalizeNumbers(e.getValue()
                )
            )
        )
        .values().stream()
        .forEach(x -> knnQueries(x, Constants.K));
        ;
    }

    public void retrieve() throws Exception {
        Map<String, String> testQueries = loadQueries(Constants.QUERY_FILE_TEST);
        testQueries
                .entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        e -> e.getKey(),
                        e -> MsMarcoIndexer.normalizeNumbers(e.getValue())
                    )
                )
        ;

        TopDocs topDocs = null;
        Map<String, TopDocs> topDocsMap = new HashMap<>(queries.size());

        for (Map.Entry<String, String> e : testQueries.entrySet()) {
            MsMarcoQuery query = new MsMarcoQuery(e.getKey(), e.getValue());

            Query luceneQuery = query.makeQuery();
            topDocs = searcher.search(luceneQuery, Constants.NUM_WANTED); // descending BM25
            topDocsMap.put(query.qid, topDocs);

            //Query luceneQuery = Constants.QRYEXPANSION? makeQueryWithExpansionTerms(qid, queryText) : makeQuery(queryText);
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(Constants.RES_FILE))) {
            for (Map.Entry<String, String> e : testQueries.entrySet()) {
                MsMarcoQuery query = new MsMarcoQuery(e.getKey(), e.getValue());
                String qid = query.qid;
                String queryText = query.qText;

                topDocs = topDocsMap.get(qid);
                if (Constants.RLM)
                    topDocs = rlm(searcher, query, topDocs);

                else if (Constants.RERANK)
                    topDocs = rerank(queryText, topDocs);
                else if (Constants.QRYEXPANSION) {
                    Query expandedQuery = rocchioQE(query, topDocs);
                    System.out.println("Expanded query: " + expandedQuery.toString());
                    topDocs = searcher.search(expandedQuery, Constants.NUM_WANTED);
                    topDocs = rlm(searcher, new MsMarcoQuery(searcher, qid, expandedQuery), topDocs);
                }

                AtomicInteger rank = new AtomicInteger(1);
                int rel = 0;
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    String docName = reader.document(sd.doc).get(Constants.ID_FIELD);
                    PerQueryRelDocs perQueryRelDocs = eval_rels.getRelInfo(qid);
                    rel = perQueryRelDocs.isRel(docName)? 1 : 0;

                    bw.write(String.format(
                            "%s\tQ0\t%s\t%d\t%.6f\t%d\t%s",
                            qid, docName,
                            rank.getAndIncrement(), sd.score,
                            rel,
                            reader.document(sd.doc).get(Constants.CONTENT_FIELD)
                    ));
                    bw.newLine();
                }
            }
            bw.close();
        }


    }

    static void mergeInto(Map<String, Double> a, Map<String, Double> b) { // merge a into b
        mergeInto(a, 1, b);
    }

    static void mergeInto(Map<String, Double> a, double weight_a, Map<String, Double> b) { // merge a into b
        for (Map.Entry<String, Double> e: a.entrySet()) {
            String key = e.getKey();
            Double a_val = e.getValue() * weight_a;
            Double b_val = b.get(key);
            if (b_val == null) {
                b_val = 0.0;
            }
            b_val += a_val;
            b.put(key, b_val);
        }
    }

    Map<String, Double> makeAvgLMDocModel(List<MsMarcoQuery> queries) throws Exception {
        Map<String, Double> docModel, avgDocModel = new HashMap<>();

        for (MsMarcoQuery query: queries) {
            PerQueryRelDocs relDocIds = rels.getRelInfo(query.qid);
            if (relDocIds == null)
                continue;

            for (String docId : relDocIds.getRelDocs()) {
                docModel = makeLMTermWts(docId);
                mergeInto(docModel, query.simWithOrig, avgDocModel);
            }
        }

        double l2Norm = TermDistribution.l2Norm(avgDocModel);
        return avgDocModel.entrySet().stream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue()/l2Norm));
    }

    // Use the supervised RLM to rerank results
    TopDocs rerank(String queryText, TopDocs retrievedRes) throws Exception {
        ScoreDoc[] rerankedScoreDocs = new ScoreDoc[retrievedRes.scoreDocs.length];
        Map<String, Double> knnDocTermWts, thisDocTermWts;
        int i = 0;
        double p_R_d = 0;

        List<MsMarcoQuery> knnQueries = knnQueries(queryText, Constants.K);

        knnDocTermWts = makeAvgLMDocModel(knnQueries); // make a centroid of reldocs for this query
        if (knnDocTermWts == null)
            return retrievedRes;

        for (ScoreDoc sd: retrievedRes.scoreDocs) {
            rerankedScoreDocs[i] = new ScoreDoc(sd.doc, sd.score);
            // LM model for this doc
            thisDocTermWts = makeLMTermWts(sd.doc, true);

            p_R_d = TermDistribution.cosineSim(knnDocTermWts, thisDocTermWts);
            rerankedScoreDocs[i++].score = (float)p_R_d * sd.score;
        }

        rerankedScoreDocs = Arrays.stream(rerankedScoreDocs)
                .sorted((o1, o2) -> o1.score < o2.score? 1: o1.score==o2.score? 0 : -1) // sort descending by sims
                .collect(Collectors.toList())
                .toArray(rerankedScoreDocs)
        ;

        return new TopDocs(new TotalHits(rerankedScoreDocs.length, TotalHits.Relation.EQUAL_TO), rerankedScoreDocs);
    }

    TopDocs srlm(IndexSearcher searcher, MsMarcoQuery query, TopDocs topDocs) throws Exception {
        List<MsMarcoQuery> knnQueries = knnQueries(query.qText, Constants.K);
        List<ScoreDoc> relDocs = new ArrayList<>();
        for (MsMarcoQuery knnQuery: knnQueries) {
            PerQueryRelDocs relDocIds = rels.getRelInfo(knnQuery.qid);
            if (relDocIds == null)
                continue;

            // add the rels for similar queries
            for (String docId : relDocIds.getRelDocs()) {
                relDocs.add(new ScoreDoc(getDocOffset(docId), 1.0f));
            }
        }
        float sum_scores = relDocs.stream().map(x->x.score).reduce(0.0f, (a, b) -> a+b);
        relDocs.stream().forEach(x -> x.score/= sum_scores);

        float sum_scores_topdocs =
                Arrays.stream(topDocs.scoreDocs)
                .map(x->x.score).reduce(0.0f, (a, b) -> a+b);
        for (ScoreDoc sd: topDocs.scoreDocs) { // add the retrieved ones
            relDocs.add(new ScoreDoc(sd.doc, sd.score/sum_scores_topdocs));
        }

        TopDocs relTopDocs = new TopDocs(
            new TotalHits(relDocs.size(), TotalHits.Relation.EQUAL_TO),
            relDocs.stream().toArray(ScoreDoc[]::new)
        );

        RelevanceModelIId fdbkModel = new RelevanceModelConditional(
                searcher, query, relTopDocs, Constants.RLM_NUM_TOP_DOCS);
        fdbkModel.computeFdbkWeights();
        return fdbkModel.rerankDocs(topDocs);
    }

    TopDocs rlm(IndexSearcher searcher, MsMarcoQuery query, TopDocs topDocs) throws Exception {
        RelevanceModelIId fdbkModel = new RelevanceModelConditional(
                searcher, query, topDocs, Constants.RLM_NUM_TOP_DOCS);
        fdbkModel.computeFdbkWeights();
        TopDocs reranked = fdbkModel.rerankDocs();
        if (!Constants.RLM_POST_QE)
            return reranked;

        MsMarcoQuery expanded_query = fdbkModel.expandQuery(Constants.NUM_EXPANSION_TERMS);
        return searcher.search(expanded_query.query, Constants.NUM_WANTED);
    }

    public static void main(String[] args) {
        try {
            KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN);
            knnRelModel.retrieve();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
