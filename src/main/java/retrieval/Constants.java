package retrieval;

public interface Constants {
    String ID_FIELD = "id";
    String CONTENT_FIELD = "words";
    String EXPANDED_QUERY_PATH_BFS = "expanded_queries\\BFS_rbo\\";

    String EXPANDED_RESULTS_PATH_BFS = "doc_results\\BFS_rbo\\";
    String EXPANDED_QUERY_PATH_GREEDY = "expanded_queries\\Greedy_rbo\\";

    String EXPANDED_RESULTS_PATH_GREEDY = "doc_results\\greedy_rbo\\";

    String NN_RES_DIR = "datasets\\NRMs\\";

    String MSMARCO_COLL = "datasets\\msmarco\\collection.tsv";
    String MSMARCO_INDEX = "index\\msmarco_coll\\";
    String MSMARCO_QUERY_INDEX = "index\\msmarco_queries\\";
    String QRELS_TRAIN = "datasets\\msmarco\\queries\\qrels.train.tsv";
    String QUERY_FILE_TRAIN = "datasets\\msmarco\\queries\\queries.train.tsv";
    String STOP_FILE = "rlm_utils\\stop.txt";
    String QUERY_FILE_TEST = "datasets\\msmarco\\queries\\trec2019dl_testqueries.tsv";
    String QRELS_TEST = "datasets\\msmarco\\queries\\2019qrels-pass.txt";
//    String RES_FILE = "rlm_results.res";

    String RES_FILE = "rlm_utils\\knn_res.txt";


    String RES_FILE_RLM = "rlm_utils\\res.txt";
    String RES_FILE_RERANKED = "rlm_utils\\res_srlm.txt";
    String SAVED_MODEL = "rlm_utils\\rlm_model.tsv";
//    String SAVED_MODEL = "C:\\Users\\mic5r\\OneDrive - University of Glasgow\\Documents\\Computer_Science\\lvl4\\l4-project\\neural-xir\\results\\rlm-model.tsv";
    int GREEDY_VOCAB_TERMS = 100;
    int BFS_TOP_DOCS_NUM = 10;
    int BFS_VOCAB_TERMS = 30;
    int BFS_MAX_DEPTH = 10;
    int BFS_MAX_EXPLORATION = 10;
    int NUM_WANTED = 1000;
    float LAMBDA = 0.9f;
    float LAMBDA_ODDS = Constants.LAMBDA/(1-Constants.LAMBDA);
    int NUM_TOP_TERMS = 5;
    boolean QRYEXPANSION = false;
    boolean RERANK = false;
    int NUM_QUERY_TERM_MATCHES = 3;
    int K = 3;
    int MU = 500;
    int NUM_EXPANSION_TERMS = 20;
    float MIXING_LAMDA = 0.9f;
    float FDBK_LAMBDA = 0.2f;
    boolean RLM = true;
    int RLM_NUM_TOP_DOCS = 20;
    int RLM_NUM_TERMS = 20;
    float RLM_NEW_TERMS_WT = 0.2f;
    boolean RLM_POST_QE = false;
    float RLM_FDBK_TERM_WT = 0.2f;
    double ROCCHIO_ALPHA = 0.5;
    double ROCCHIO_BETA = 0.35;
    double ROCCHIO_GAMMA = 1-(ROCCHIO_ALPHA + ROCCHIO_BETA);
    int ROCCHIO_NUM_NEGATIVE = 3;
    String TOPDOCS_FOLDER = "topdocs";
}
