package retrieval;
import java.util.HashMap;
import java.util.Map;

public class TermDistribution {
    String queryTerm;
    Map<String, Double> cooccurProbs;
    double l2Norm;

    TermDistribution(String queryTerm) {
        this.queryTerm = queryTerm;
        this.cooccurProbs = new HashMap<>();
    }

    void add(String term_and_wt_token) {
        String[] term_wt_token_parts = term_and_wt_token.split("\\" + SupervisedRLM.DELIM);
        String term = term_wt_token_parts[0];
        if (term_wt_token_parts.length<2)
            System.err.println("Problem for token|" + term_and_wt_token + "|");

        double wt = Double.parseDouble(term_wt_token_parts[1]);
        cooccurProbs.put(term, wt);
    }

    void update(String qterm, Map<String, Double> docTermWts) {
        Double cooccur_accumulated = null;
        double p_w_q;

        for (Map.Entry<String, Double> e: docTermWts.entrySet()) {
            String w = e.getKey();
            Double p_w_d = e.getValue(); // this can't be null coz this we get by iterating
            Double p_q_d = docTermWts.get(qterm);
            if (p_q_d==null) p_q_d = 0.0;

            cooccur_accumulated = cooccurProbs.get(w); // the weight indexed by the term 'w'
            if (cooccur_accumulated == null) {
                cooccur_accumulated = 0.0;
            }
            p_w_q = p_q_d*p_w_d;
            if (p_w_q > 0)
                cooccurProbs.put(w, cooccur_accumulated + p_w_q);  // this is the weight of 'w' (and not q)
        }
    }

    double klDiv(Map<String, Double> termWts) {
        double kldiv = 0;
        for (Map.Entry<String, Double> e: cooccurProbs.entrySet()) {
            String w = e.getKey();
            double p_w_R = e.getValue();
            Double p_w_d = termWts.get(w);
            if (p_w_d == null) continue; // avoid 0 division error
            if (p_w_R == 0) continue;

            kldiv += p_w_R * Math.log(p_w_R/p_w_d);
        }
        return kldiv;
    }

    static double l2Norm(Map<String, Double> termWts) {
        return Math.sqrt(termWts.values().stream().map(x->x*x).reduce(0.0, (x_c, x_n) -> x_c + x_n));
    }

    double cosineSim(Map<String, Double> termWts) {
        double sim = 0;
        if (this.cooccurProbs.isEmpty())
            return 0;
        for (Map.Entry<String, Double> e: termWts.entrySet()) {
            String w = e.getKey();
            Double p_w_R = this.cooccurProbs.get(w);
            if (p_w_R == null) continue;

            double p_w_d = e.getValue();
            sim += p_w_R * p_w_d;
        }
        return sim;
    }

    static double cosineSim(Map<String, Double> a, Map<String, Double> b) {
        double sim = 0;
        if (a.isEmpty())
            return 0;
        for (Map.Entry<String, Double> e: a.entrySet()) {
            String w = e.getKey();
            Double p_w_R = b.get(w);
            if (p_w_R == null) continue;

            double p_w_d = e.getValue();
            sim += p_w_R * p_w_d;
        }
        return sim;
    }


    public static void main(String[] args) {
        Map<String, Double> map = Map.of("t1",1.0, "t2", 1.0, "t3", 1.0);
        System.out.println(l2Norm(map));
    }
}
