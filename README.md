# eliBM25
Repository holding the code used for experiments in the SIGIR'23 paper ["‘Explain like I am BM25’: Interpreting a Dense Model’s Ranked-List with a Sparse Approximation."](https://arxiv.org/abs/2304.12631).

This implementation relies largely on the lucene-msmarco repository that can be found [here](https://github.com/gdebasis/lucene-msmarco). The relevant code from this repo is included in this repository. 

We share our discrete-space optimization implementations in the `search` package under `src/main/java/`

We also include our approximation result files under the directory `runs/`. 

We include our original approximated NRM result files, as well as those used for our baselines under the `datasets/` directory. 



## Setup 

In order to reproduce the results, a msmarco passage collection index must exist under the path`index/msmarco_coll/`, as well as a query index under the path `index/msmarco_queries/`. These can be generated using the `index.sh`  script provided, where the msmarco passage collection is available under `datasets/msmarco/collection.tsv`, as well as the relevant query files `datasets/msmarco/queries/queries.train.tsv` and `datasets/msmarco/queries/qrels.train.tsv`. These files can be downloaded from the original msmarco website [here](https://microsoft.github.io/msmarco/). 



## Usage

In order to generate the expanded queries for our approaches, we provide main methods for both the Greedy baseline and the BFS approach under their respective classes, `BFS.java` and `Greedy.java`.

One can then use these expanded queries for simple BM25 retrieval, as shown in `ExpandedRetrieval.java`.
