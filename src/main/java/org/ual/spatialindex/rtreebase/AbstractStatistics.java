package org.ual.spatialindex.rtreebase;

import org.ual.spatialindex.spatialindex.IStatistics;

import java.util.ArrayList;

/**
  * Abstract base for R-Tree statistical tracking.
  *
  * <p>Tracks reads, writes, splits, hits, misses, nodes, adjustments,
  * query results, data count, tree height, and per-level node counts.</p>
  *
  * <p><b>Conventions:</b></p>
  * <ul>
  *   <li>Level 0: leaf nodes; level n: index nodes; root at highest level.</li>
  *   <li>Tree height = number of levels (e.g., height 1 = single root leaf).</li>
  *   <li>{@code nodesInLevel.get(0)} always returns leaf node count.</li>
  * </ul>
  */
public abstract class AbstractStatistics implements IStatistics, Cloneable {
    // Statistical counters for tree operations
    protected long reads;
    protected long writes;
    protected long splits;
    protected long hits;
    protected long misses;
    protected long nodes;
    protected long adjustments;
    protected long queryResults;
    protected long data;

    // Tree structure information
    protected int treeHeight;
    protected ArrayList<Integer> nodesInLevel = new ArrayList<>();

    /**
     * Creates a new statistics object with all counters set to zero.
     */
    public AbstractStatistics() {
        reset();
    }

    /**
     * Copy constructor.
     *
     * @param s The statistics object to copy from
     */
    public AbstractStatistics(AbstractStatistics s) {
        this.reads = s.reads;
        this.writes = s.writes;
        this.splits = s.splits;
        this.hits = s.hits;
        this.misses = s.misses;
        this.nodes = s.nodes;
        this.adjustments = s.adjustments;
        this.queryResults = s.queryResults;
        this.data = s.data;
        this.treeHeight = s.treeHeight;
        this.nodesInLevel = new ArrayList<>(s.nodesInLevel);
    }

    /**
     * Resets all counters to zero.
     */
    public void reset() {
        reads = 0L;
        writes = 0L;
        splits = 0L;
        hits = 0L;
        misses = 0L;
        nodes = 0L;
        adjustments = 0L;
        queryResults = 0L;
        data = 0L;
        treeHeight = 0;
        nodesInLevel.clear();
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Reads: ").append(reads).append("\n")
                .append("Writes: ").append(writes).append("\n")
                .append("Hits: ").append(hits).append("\n")
                .append("Misses: ").append(misses).append("\n")
                .append("Tree height: ").append(treeHeight).append("\n")
                .append("Number of data: ").append(data).append("\n")
                .append("Number of nodes: ").append(nodes).append("\n");

        for (int level = 0; level < treeHeight; level++) {
            s.append("Level ").append(level).append(" pages: ").append(nodesInLevel.get(level)).append("\n");
        }

        s.append("Splits: ").append(splits).append("\n")
                .append("Adjustments: ").append(adjustments).append("\n")
                .append("Query results: ").append(queryResults);

        return s.toString();
    }

    @Override
    public abstract AbstractStatistics clone();


    //====================================================================================
    //=================================== Getter Methods =================================
    //====================================================================================


    @Override
    public long getReads() {
        return reads;
    }

    @Override
    public long getWrites() {
        return writes;
    }

    @Override
    public long getNumberOfNodes() {
        return nodes;
    }

    @Override
    public long getNumberOfData() {
        return data;
    }

    @Override
    public long getSplits() {
        return splits;
    }

    @Override
    public long getHits() {
        return hits;
    }

    @Override
    public long getMisses() {
        return misses;
    }

    @Override
    public long getAdjustments() {
        return adjustments;
    }

    @Override
    public long getQueryResults() {
        return queryResults;
    }

    /**
      * Returns the height of the R-tree (number of levels).
      *
      * @return tree height; always >= 1 for an initialized tree
      */
    @Override
    public int getTreeHeight() {
        return treeHeight;
    }

    public ArrayList<Integer> getNodesInLevel() {
        return nodesInLevel;
    }

    /**
      * Returns the number of leaf nodes in the tree.
      *
      * @return the count of leaf nodes at level 0, or 0 if tree is empty
      */
    @Override
    public int getLeafNodeCount() {
        // Leaves are always at level 0, regardless of tree height
        return nodesInLevel.isEmpty() ? 0 : nodesInLevel.get(0);
    }

    @Override
    public int getNumberOfNodesInLevel(int level) throws IndexOutOfBoundsException {
        return nodesInLevel.get(level);
    }


    //====================================================================================
    //=========================== Increment/Decrement Methods ============================
    //====================================================================================

    public void incrementReads() {
        reads++;
    }

    public void incrementWrites() {
        writes++;
    }

    public void incrementSplits() {
        splits++;
    }

    public void incrementHits() {
        hits++;
    }

    public void incrementMisses() {
        misses++;
    }

    public void incrementNodes() {
        nodes++;
    }

    public void incrementAdjustments() {
        adjustments++;
    }

    public void incrementQueryResults() {
        queryResults++;
    }

    public void incrementData() {
        data++;
    }

    public void decrementData() {
        data--;
    }

    public void incrementTreeHeight() {
        treeHeight++;
    }

    //====================================================================================
    //==================================== Setter Methods ================================
    //====================================================================================

    @Override
    public void setDataCount(int totalEntries) {
        data = totalEntries;
    }

    @Override
    public void setTreeHeight(int height) {
        treeHeight = height;
    }

    //====================================================================================
    //============================ Level Management Methods ==============================
    //====================================================================================

    @Override
    public void addNodeInLevel(int node) {
        nodesInLevel.add(node);
    }

    public void addNodesInLevel(int level, int count) {
        if (level >= nodesInLevel.size()) {
            for (int i = nodesInLevel.size(); i <= level; i++) {
                nodesInLevel.add(0);
            }
        }
        nodesInLevel.set(level, nodesInLevel.get(level) + count);
    }
}
