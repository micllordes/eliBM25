package search;

import qrels.ResultTuple;

import java.util.*;

public class SimilarityMeasures {


    public static double rbo(ArrayList<String> l1, List<ResultTuple> neuralRes){
        double p = 0.9;
        int depth = 10;
        ArrayList<String> s1 = new ArrayList<>(l1.subList(0,Math.min(l1.size(),depth)));
        ArrayList<String> s2 = new ArrayList<>(s1.size());

        for (ResultTuple res: neuralRes.subList(0, Math.min(depth,neuralRes.size()))){
            s2.add(res.getDocName());
        }
        int k = Math.min(Math.min(s1.size(), s2.size()),depth);
        double[] A = new double[k];
        double[] A0 = new double[k];
        double[] weights = new double[k];
        HashMap<String, Boolean> s1_running = new HashMap<>();
        HashMap<String, Boolean> s2_running = new HashMap<>();
        s1_running.put(s1.get(0),true);
        s2_running.put(s2.get(0),true);

        for (int i=0;i<A0.length;i++){
            weights[i] = 1.0 * (1 - p) * Math.pow(p,i);
        }
        if (s1.get(0).equals(s2.get(0))){
            A[0] = 1;
            A0[0] = weights[0];
        }
        else{
            A[0] = 0;
            A0[0] = 0;
        }
        for (int i=1; i<k;i++){
            int tmp = 0;
            if (s2_running.containsKey(s1.get(i))){
                tmp+=1;
            }
            if (s1_running.containsKey(s2.get(i))){
                tmp+=1;
            }
            if (s1.get(i).equals(s2.get(i))){
                tmp+=1;
            }
            A[i] = 1.0 * ((A[i - 1] * i) + tmp) / (i + 1);
            A0[i] = A0[i - 1] + weights[i] * A[i];
            s1_running.put(s1.get(i),true);
            s2_running.put(s2.get(i),true);

        }
        double final_val = A0[A0.length-1];
        if (final_val<0.0){
            return 0.0;
        }
        if (final_val>1.0){
            return 1.0;
        }
        return final_val;

    }

    public static double jaccardSimilarity(ArrayList<String> set1, List<ResultTuple> neuralRes){ //untested with
        HashSet<String> s2 = new HashSet<>();
        HashSet<String> s1 = new HashSet<>(set1.size());
        s1.addAll(set1);
        for (ResultTuple res: neuralRes.subList(0, Math.min(s1.size(),neuralRes.size()))){
            s2.add(res.getDocName());
        }
        int s1Size = s1.size(); //original s1 size
        s1.retainAll(s2); //s1 is now intersection
        double union = s1Size + s2.size() - s1.size();
        if(union == 0){
            return 0.0;
        }
        return s1.size()/union;
    }


    public static void main(String[] args) {
        ResultTuple res1 = new ResultTuple("a",1,10);
        ResultTuple res2 = new ResultTuple("b",2,9);
        ResultTuple res3 = new ResultTuple("c",3,8);
        List<ResultTuple> neural = Arrays.asList(res1,res2,res3);
        ArrayList<String> s1 = new ArrayList<>(Arrays.asList(res1.getDocName(),res2.getDocName(),res3.getDocName()));
        System.out.println(rbo(s1,neural));

    }
}
