/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fdbk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import retrieval.Constants;
import retrieval.MsMarcoQuery;

/**
 *
 * @author Debasis
 */

class KLDivScoreComparator implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc a, ScoreDoc b) {
        return a.score < b.score? -1 : a.score == b.score? 0 : 1;
    }    
}

public class RelevanceModelIId {
    IndexSearcher searcher;
    TopDocs topDocs;
    int numTopDocs;
    RetrievedDocsTermStats retrievedDocsTermStats;
    final float fbweight = Constants.RLM_FDBK_TERM_WT;
    IndexReader reader;
    PostFdbkReranker rerankerMethod;
    MsMarcoQuery query;

    public RelevanceModelIId(IndexSearcher searcher, MsMarcoQuery query, TopDocs topDocs, int numTopDocs) throws Exception {
        this(searcher, query, topDocs, numTopDocs, new KLDivReranker());
    }

    public RelevanceModelIId(IndexSearcher searcher, MsMarcoQuery query, TopDocs topDocs, int numTopDocs, PostFdbkReranker rerankMethod) throws Exception {
        this.searcher = searcher;
        this.query = query;
        this.topDocs = topDocs;
        this.numTopDocs = numTopDocs;
        this.rerankerMethod = rerankMethod;
    }

    public RetrievedDocsTermStats getRetrievedDocsTermStats() {
        return this.retrievedDocsTermStats;
    }
    
    public void buildTermStats() throws Exception {
        retrievedDocsTermStats = new
                RetrievedDocsTermStats(searcher.getIndexReader(), topDocs, numTopDocs);
        retrievedDocsTermStats.buildAllStats();
        reader = retrievedDocsTermStats.getReader();
    }
    
    float mixTfIdf(RetrievedDocTermInfo w) {
        return Constants.MIXING_LAMDA*w.tf/(float)retrievedDocsTermStats.sumTf +
                (1-Constants.MIXING_LAMDA)*w.df/retrievedDocsTermStats.sumDf;
    }    
    
    float mixTfIdf(RetrievedDocTermInfo w, PerDocTermVector docvec) {
        RetrievedDocTermInfo wGlobalInfo = retrievedDocsTermStats.termStats.get(w.getTerm());
        return Constants.MIXING_LAMDA*w.tf/(float)docvec.sum_tf +
                (1-Constants.MIXING_LAMDA)*wGlobalInfo.df/retrievedDocsTermStats.sumDf;
    }
            
    public void computeFdbkWeights() throws Exception {
        float p_q;
        float p_w;
        
        buildTermStats();
        
        /* For each w \in V (vocab of top docs),
         * compute f(w) = \sum_{q \in qwvecs} K(w,q) */
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            float total_p_q = 0;
            RetrievedDocTermInfo w = e.getValue();
            p_w = mixTfIdf(w);
            
            Set<Term> qTerms = query.getQueryTerms();
            for (Term qTerm : qTerms) {
                // Get query term frequency
                RetrievedDocTermInfo qtermInfo = retrievedDocsTermStats.getTermStats(qTerm.toString());
                if (qtermInfo == null) {
                    System.err.println("No KDE for query term: " + qTerm.toString());
                    continue;
                }
                p_q = qtermInfo.tf/(float)retrievedDocsTermStats.sumTf; //mixTfIdf(qtermInfo); //
                
                total_p_q += Math.log(1+p_q);
            }
            w.wt = p_w * (float)Math.exp(total_p_q-1);
        }
    }
    
    public TopDocs rerankDocs() {
        rerankerMethod.setStats(topDocs, retrievedDocsTermStats);
        return rerankerMethod.rerankDocs();
    }

    // rerank a different topdocs
    public TopDocs rerankDocs(TopDocs topDocs) {
        rerankerMethod.setStats(topDocs, retrievedDocsTermStats);
        return rerankerMethod.rerankDocs();
    }

    // Implement post-RLM query expansion. Set the term weights
    // according to the values of f(w).
    public MsMarcoQuery expandQuery(int numTerms) throws Exception {
        // The calling sequence has to make sure that the top docs are already
        // reranked by KL-div
        // Now reestimate relevance model on the reranked docs this time
        // for QE.
        computeFdbkWeights();
        
        Set<Term> origTerms = this.query.getQueryTerms();
        Set<String> origQueryWordStrings = this.query.getQueryTermsAsString();

        float normalizationFactor = 0;
        List<RetrievedDocTermInfo> termStats = new ArrayList<>();
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            w.wt = w.wt *
                    (float)Math.log(
                        reader.numDocs()/(float)
                        reader.docFreq(new Term(Constants.CONTENT_FIELD, w.getTerm())));
            termStats.add(w);
            normalizationFactor += w.wt;
        }
        
        // Normalize the weights
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            w.wt = w.wt/normalizationFactor;
        }
        
        Collections.sort(termStats);

        BooleanQuery.Builder expandedQueryBuilder = new BooleanQuery.Builder();
        for (Term t : origTerms) {
            BoostQuery tq = new BoostQuery(
                    new TermQuery(t),
                    1-fbweight);
            expandedQueryBuilder.add(tq, BooleanClause.Occur.SHOULD);
        }
        
        int nTermsAdded = 0;
        for (RetrievedDocTermInfo selTerm : termStats) {            
            String thisTerm = selTerm.getTerm();
            if (origQueryWordStrings.contains(thisTerm))
                continue;

            BoostQuery tq = new BoostQuery(
                new TermQuery(new Term(Constants.CONTENT_FIELD, thisTerm)),
                fbweight*selTerm.wt
            );
            expandedQueryBuilder.add(tq, BooleanClause.Occur.SHOULD);
            
            nTermsAdded++;
            if (nTermsAdded >= numTerms)
                break;
        }
        
        return new MsMarcoQuery(query, expandedQueryBuilder.build());
    }
}
