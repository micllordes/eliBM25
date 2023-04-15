package retrieval;

import indexing.MsMarcoIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MsMarcoQuery {
    String qid;
    String qText;
    Query query;
    float simWithOrig;

    public MsMarcoQuery(String qid, String qText) {
        this(qid, qText, 1);
    }

    public MsMarcoQuery(String qid, String qText, float simWithOrig) {
        this.qid = qid;
        this.qText = qText;
        this.simWithOrig = simWithOrig;
    }

    public MsMarcoQuery(MsMarcoQuery that, Query query) {
        this.qid = that.qid;
        this.qText = that.qText;
        this.simWithOrig = that.simWithOrig;
        this.query = query;
    }

    public MsMarcoQuery(IndexSearcher searcher, String qid, Query query) {
        this.qid = qid;
        this.query = query;
        try {
            Set<Term> origTerms = new HashSet<>();
            query.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(origTerms);
            qText = origTerms.stream().map(x->x.text()).collect(Collectors.joining(" "));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public MsMarcoQuery(String qid, String qText, Query query) {
        this(qid, qText, 0.0f);
        this.query = query;
    }

    public Set<String> getQueryTermsAsString() {
        return Arrays.stream(MsMarcoIndexer
            .analyze(MsMarcoIndexer.constructAnalyzer(), qText)
            .split("\\s+"))
            .collect(Collectors.toSet());
    }

    public Set<Term> getQueryTerms() {
        return Arrays.stream(MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), qText)
                .split("\\s+"))
                .map(x-> new Term(Constants.CONTENT_FIELD, x))
                .collect(Collectors.toSet());
    }

    Query makeQuery() {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), this.qText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return (Query)qb.build();
    }

}

