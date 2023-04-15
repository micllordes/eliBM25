package fdbk;

import org.apache.lucene.search.TopDocs;

interface PostFdbkReranker {
    public void setStats(TopDocs topDocs, RetrievedDocsTermStats stats);
    public TopDocs rerankDocs();
}
