package qrels;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;
    PerQueryRelDocs relInfo;

    public RetrievedResults(String qid) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(100);
        avgP = -1;
        numRelRet = -1;
    }

    public String getQid() { return qid; }

    public int getNumRet() { return rtuples.size(); }

    public List<ResultTuple> getTuples() { return this.rtuples; }

    public double[] getRSVs(int k) {
        return ArrayUtils
                .toPrimitive(rtuples
                        .stream()
                        .map(ResultTuple::getScore)
                        .collect(Collectors.toList())
                        .subList(0, Math.min(k, rtuples.size()))
                        .toArray(new Double[0]), 0.0);
    }

    public void addTuple(String docName, int rank, double score) {
        rtuples.add(new ResultTuple(docName, rank, score));
    }

    public String toTrecString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append("Q0").append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.score).append("\t").
                    append("BFS").append("\n");
        }
        return buff.toString();
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.rel).append("\n");
        }
        return buff.toString();
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}


