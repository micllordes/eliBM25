package retrieval;

import fdbk.PerDocTermVector;
import fdbk.RetrievedDocTermInfo;
import indexing.MsMarcoIndexer;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import qrels.RetrievedResults;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class OneStepRetriever {
    IndexReader reader;
    IndexSearcher searcher;
    Similarity sim;
    Map<String, String> queries;

    public OneStepRetriever(String queryFile) throws Exception {
        reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        searcher = new IndexSearcher(reader);
        sim = new BM25Similarity();
//        sim = new LMDirichletSimilarity(Constants.MU);
        searcher.setSimilarity(sim);
        queries = loadQueries(queryFile);
    }
    public OneStepRetriever() throws Exception{
        reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        searcher = new IndexSearcher(reader);
        sim = new BM25Similarity();
        searcher.setSimilarity(sim);
    }

    public Map<String, String> loadQueries(String queryFile) throws Exception {
        return
                FileUtils.readLines(new File(queryFile), StandardCharsets.UTF_8)
                        .stream()
                        .map(x -> x.split("\t"))
                        .collect(Collectors.toMap(x -> x[0], x -> x[1])
                        )
                ;
    }

    public Query makeQuery(String queryText) throws Exception {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = MsMarcoIndexer.analyze(MsMarcoIndexer.constructAnalyzer(), queryText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return (Query)qb.build();
    }


    public ArrayList<String> retrieveQuery(String qText, int numWanted) throws Exception{
        Query luceneQuery = makeQuery(qText);
        TopDocs topDocs = searcher.search(luceneQuery, numWanted);
        ArrayList<String> topDocIDs = new ArrayList<String>(numWanted);
        for (ScoreDoc doc: topDocs.scoreDocs){
            String docID = searcher.doc(doc.doc).get("id");
            topDocIDs.add(docID);
        }
        return topDocIDs;
    }

    public String retrieveTopDocs(Map.Entry<String, String> query, int numWanted) throws Exception {
        String qText = query.getValue();
        String qID = query.getKey();
        RetrievedResults res = new RetrievedResults(qID);
        Query luceneQuery = makeQuery(qText);
        TopDocs topDocs = searcher.search(luceneQuery, numWanted);
        int rank = 0;
        for (ScoreDoc doc: topDocs.scoreDocs){
            String docID = searcher.doc(doc.doc).get("id");
            res.addTuple(docID,rank,doc.score);
            rank++;
        }
        return res.toTrecString();
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

        TopDocs topDocs;
        for (Map.Entry<String, String> e : testQueries.entrySet()) {
            String qid = e.getKey();
            String queryText = e.getValue();

            Query luceneQuery = makeQuery(queryText);

            System.out.println(String.format("Retrieving for query %s: %s", qid, luceneQuery));
            topDocs = searcher.search(luceneQuery, Constants.NUM_WANTED);

            saveTopDocs(qid, topDocs);
        }
    }

    public PerDocTermVector buildStatsForSingleDoc(IndexReader reader, int docId) throws IOException {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        RetrievedDocTermInfo trmInfo;
        PerDocTermVector docTermVector = new PerDocTermVector(docId);

        tfvector = reader.getTermVector(docId, Constants.CONTENT_FIELD);
        if (tfvector == null || tfvector.size() == 0)
            return null;

        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field

        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            termText = term.utf8ToString();
            tf = (int)termsEnum.totalTermFreq();
            docTermVector.addTerm(termText, tf);
        }
        return docTermVector;
    }

    void saveTopDocs(String qid, TopDocs topDocs) throws Exception {
        String parentDir = Constants.TOPDOCS_FOLDER;
        Map<String, Integer> word2id = new HashMap<>();
        int termIdInLocalVocab = 0;

        String qidPath = parentDir + "/" + qid;
        // create a folder
        System.out.println("Creating directory " + qidPath);
        File dir = new File(qidPath);
        if (!dir.exists())
            dir.mkdir();

        for (ScoreDoc sd : topDocs.scoreDocs) {
            String fileName = String.format("%s/sparse_%d.txt", qidPath, sd.doc);

            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));

            int docId = sd.doc;
            PerDocTermVector docvec = buildStatsForSingleDoc(reader, docId);

            for (RetrievedDocTermInfo tinfo: docvec.getPerDocStats().values()) {
                String word = tinfo.getTerm();
                bw.write(String.format("%s:%d", word, tinfo.getTf()));
                bw.newLine();

                Integer termId = word2id.get(word);
                if (termId==null) {
                    word2id.put(word, termIdInLocalVocab++);
                }
            }
            bw.close();
        }

        word2id = word2id
            .entrySet().stream().sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new))
        ;

        BufferedWriter bw = new BufferedWriter(new FileWriter(String.format("%s/vocab.txt", qidPath)));
        for (Map.Entry<String, Integer> e: word2id.entrySet()) {
            bw.write(String.format("%s\t%s\n", e.getKey(), e.getValue()));
        }
        bw.close();
    }

    public static void main(String[] args) throws Exception {
        OneStepRetriever oneStepRetriever = new OneStepRetriever(Constants.QUERY_FILE_TRAIN);
        oneStepRetriever.retrieve();
        oneStepRetriever.reader.close();
    }
}
