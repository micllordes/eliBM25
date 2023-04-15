package qrels;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class AllRelRcds {
    String qrelsFile;
    HashMap<String, PerQueryRelDocs> perQueryRels;

    public AllRelRcds(String qrelsFile) throws Exception {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        load();
    }

    private void load() throws Exception {
        FileReader fr = new FileReader(qrelsFile);
        BufferedReader br = new BufferedReader(fr);
        String line;

        while ((line = br.readLine()) != null) {
            storeRelRcd(line);
        }
        br.close();
        fr.close();
    }

    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        relTuple.addTuple(tokens[2]);
    }

    public String toString() {
        return perQueryRels
                .values()
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }

    public PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}


