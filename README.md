# BACH-tool-database
### BACH: Bitcoin Address Clustering based on multiple Heuristics
Batch procedure to create the database used in the BACH tool.
Get block information from [Blockchain APIs](https://blockchain.info) and build a new database clustering the addresses together according to three heuristics:
  * Multi-Input clustering
  * Change address clustering
  * Coinbase clustering

The new database also contains information about the type of heuristic used to link two or more addresses, so that you can build a graph from them.
The code is well documented, also there is the script with the database'schema.
