### BACH: Bitcoin Address Clustering based on multiple Heuristics
The batch procedure creates the database used in the BACH tool.<br>
It retrieves block information from [Blockchain APIs](https://blockchain.info) and builds a new database that clusters addresses together based on three heuristics:
  * Multi-Input clustering
  * Change address clustering
  * Coinbase clustering

The new database also includes information about the type of heuristic used to link two or more addresses, allowing for the construction of a graph from this data.
The code is well-documented, and the script includes the database schema.
