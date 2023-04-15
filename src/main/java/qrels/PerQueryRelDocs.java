package qrels;
import java.util.*;
import java.util.stream.Collectors;

public class PerQueryRelDocs {
    String qid;
    Set<String> relSet; // keyed by docid, entry stores the rel value (>0)

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        relSet = new HashSet<>();
    }

    public void addTuple(String docId) {
        relSet.add(docId);
    }

    public boolean isRel(String docName) {
        return relSet.contains(docName);
    }

    public Set<String> getRelDocs() { return relSet; }

    public String toString() {
        return relSet
                .stream()
                .collect(Collectors.joining(", "));
    }
}
