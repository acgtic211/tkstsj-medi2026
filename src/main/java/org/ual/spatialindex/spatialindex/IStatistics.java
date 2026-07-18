package org.ual.spatialindex.spatialindex;

public interface IStatistics {
    long getReads();
    long getWrites();
    long getNumberOfNodes();
    long getNumberOfData();
    // NEW
    long getSplits();
    long getHits();
    long getMisses();
    long getAdjustments();
    long getQueryResults();
    int getTreeHeight();
    int getLeafNodeCount();
    void setDataCount(int totalEntries);
    void addNodeInLevel(int i);
    void setTreeHeight(int level);
    int getNumberOfNodesInLevel(int level);
}
