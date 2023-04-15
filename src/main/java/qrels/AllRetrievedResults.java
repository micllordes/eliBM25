package qrels;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import retrieval.Constants;

import java.io.*;
import java.util.*;

public class AllRetrievedResults {
    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;

    public AllRetrievedResults(String resFile) {
        String line;
        this.resFile = resFile;
        int i =0;

        allRetMap = new TreeMap<>();
        try (FileReader fr = new FileReader(resFile);
             BufferedReader br = new BufferedReader(fr); ) {
            while ((line = br.readLine()) != null) {
                i++;
                if(!line.equals("\n") && !line.equals("")){
                    storeRetRcd(line);
                }
            }
        }
        catch (Exception ex) {
            System.out.println(i);ex.printStackTrace(); }
    }

    public Set<String> queryIds() { return this.allRetMap.keySet(); }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return allRetMap.get(qid);
    }

    void storeRetRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid);
            allRetMap.put(qid, res);
        }
        if (tokens.length<2){
            System.out.println(line);
        }
        res.addTuple(tokens[2], Integer.parseInt(tokens[3]), Double.parseDouble(tokens[4]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            buff.append(res.toString()).append("\n");
        }
        return buff.toString();
    }

    public static int getDocOffsetFromId(IndexSearcher searcher, String docId) {
        try {
            Query query = new TermQuery(new Term(Constants.ID_FIELD, docId));
            TopDocs topDocs = searcher.search(query, 1);
            return topDocs.scoreDocs[0].doc;
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return -1;
    }

    public Map<String, TopDocs> castToTopDocs(IndexSearcher searcher) {
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        for (RetrievedResults rr: allRetMap.values()) {
            int numret = rr.rtuples.size();
            List<ScoreDoc> scoreDocs = new ArrayList<>();
            for (ResultTuple tuple: rr.rtuples) {
                int docOffset = getDocOffsetFromId(searcher, tuple.docName);
                if (docOffset>0)
                    scoreDocs.add(new ScoreDoc(docOffset, (float)tuple.score));
            }
            ScoreDoc[] scoreDocArray = new ScoreDoc[scoreDocs.size()];
            scoreDocArray = scoreDocs.toArray(scoreDocArray);
            TopDocs topDocs = new TopDocs(new TotalHits(numret, TotalHits.Relation.EQUAL_TO), scoreDocArray);
            topDocsMap.put(rr.qid, topDocs);
        }
        return topDocsMap;
    }
}


