package indexing;

import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;
import retrieval.Constants;

import java.io.*;
import java.util.*;

public class IndexTester {
    static void showDocVec(IndexReader reader, int docId) throws Exception {
        BytesRef term;
        String termText;
        int tf;

        Document d = reader.document(docId);

        System.out.println("Fields stored in the index...");
        for (IndexableField f: d.getFields()) {
            System.out.println(f.name());
        }
        Terms tfs = reader.getTermVector(0, Constants.CONTENT_FIELD);
        if (tfs == null)
            System.out.println("Term vectors not indexed");
        else {
            System.out.println("Vector size: " + tfs.size());
            Terms tfvector = reader.getTermVector(docId, Constants.CONTENT_FIELD);
            TermsEnum termsEnum = tfvector.iterator(); // access the terms for this field

            while ((term = termsEnum.next()) != null) { // explore the terms for this field
                termText = term.utf8ToString();
                tf = (int)termsEnum.totalTermFreq();
                System.out.println(String.format("%s:%d", termText, tf));
            }
        }
    }

    static void showTokensForField(IndexReader reader, String fieldName) throws IOException {
        List<LeafReaderContext> list = reader.leaves();
        int count = 0;
        for (LeafReaderContext lrc : list) {
            Terms terms = lrc.reader().terms(fieldName);
            if (terms != null) {
                TermsEnum termsEnum = terms.iterator();

                BytesRef term;
                while ((term = termsEnum.next()) != null) {
                    //System.out.println(term.utf8ToString());
                    count++;
                }
            }
        }
        System.out.println(count);
    }

    static void showTokensForField(IndexReader reader, String fieldName, String fileName) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
        List<LeafReaderContext> list = reader.leaves();
        int count = 0;
        HashSet<String> vocab = new HashSet<>();

        for (LeafReaderContext lrc : list) {
            Terms terms = lrc.reader().terms(fieldName);
            if (terms != null) {
                TermsEnum termsEnum = terms.iterator();

                BytesRef term;
                while ((term = termsEnum.next()) != null) {
                    String word = term.utf8ToString();
                    if (!vocab.contains(word)) {
                        int freq = reader.docFreq(new Term(fieldName, word));
                        bw.write(String.format("%s\t%d\n", word, freq));
                        count++;
                        vocab.add(word);
                    }
                }
            }
        }
        bw.close();
        System.out.println(count + " terms found in the index.");
    }

    public static void main(String[] args) throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        System.out.println(reader.numDocs());

        /*
        showDocVec(reader, 0);
        System.out.println(reader.docFreq(new Term(Constants.CONTENT_FIELD, "theraderm")));
        //showTokensForField(reader, Constants.CONTENT_FIELD);
        reader.close();

        reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_QUERY_INDEX).toPath()));
        System.out.println(reader.numDocs());
        showDocVec(reader, 0);
        System.out.println(reader.docFreq(new Term(Constants.CONTENT_FIELD, "trapezoid")));
        showTokensForField(reader, Constants.CONTENT_FIELD);
         */
        showTokensForField(reader, Constants.CONTENT_FIELD, "vocab.txt");
    }
}
