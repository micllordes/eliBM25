/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fdbk;

import java.util.Map;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

/**
 *
 * @author Debasis
 */
public class RelevanceModelConditional extends RelevanceModelIId {

    public RelevanceModelConditional(IndexSearcher searcher,
         MsMarcoQuery query, TopDocs topDocs, int numTopDocs) throws Exception {
        super(searcher, query, topDocs, numTopDocs);
    }
    
    @Override
    public void computeFdbkWeights() throws Exception {
        float p_w;
        float this_wt; // phi(q,w)
        
        buildTermStats();
        
        int docsSeen = 0;

        // For each doc in top ranked
        //for (PerDocTermVector docvec : this.retrievedDocsTermStats.docTermVecs.values()) {
        for (ScoreDoc sd: topDocs.scoreDocs) {
            PerDocTermVector docvec = retrievedDocsTermStats.docTermVecs.get(sd.doc);
            if (docvec==null) {
                System.out.println(String.format("Doc %d not seen", sd.doc));
                continue;
            }
            // For each word in this document
            for (Map.Entry<String, RetrievedDocTermInfo> e : docvec.perDocStats.entrySet()) {
                RetrievedDocTermInfo w = e.getValue();
                p_w = mixTfIdf(w, docvec);
                this_wt = p_w * docvec.sim/this.retrievedDocsTermStats.sumSim;
                
                // Take the average
                RetrievedDocTermInfo wGlobal = retrievedDocsTermStats.getTermStats(w.getTerm());
                wGlobal.wt += this_wt;      
            }
            docsSeen++;
            if (docsSeen >= numTopDocs)
                break;
        }  
    }
}
