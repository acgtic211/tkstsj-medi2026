package org.ual.utils.config;

import org.ual.spatialindex.rtree.BulkLoadMethod;

public class IndexConfig {
    private int fanout = 25;
    private float fillFactor = 0.7f;
    private int dimension = 2;
    private float betaArea = 0.5f;
    private int maxWord = 4;
    private int numClusters = 8;
    private int numMoves = 300;
    private RTreeVariant rTreeVariant = RTreeVariant.RSTAR;
    private int nearMinimumOverlapFactor = 8;//32;
    private float smoothingFactor = 0.2f;
    private SpatialIndexType spatialIndexType = SpatialIndexType.IR;
    private DataStructureType dataStructureType = DataStructureType.HASHMAP;
    private TextualIndexType textualIndexType = TextualIndexType.INVERTED_LIST;
    private BulkLoadMethod bulkLoadMethod = BulkLoadMethod.STR;

    // Getters and setters
    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
    }

    public float getFillFactor() {
        return fillFactor;
    }

    public void setFillFactor(float fillFactor) {
        this.fillFactor = fillFactor;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public float getBetaArea() {
        return betaArea;
    }

    public void setBetaArea(float betaArea) {
        this.betaArea = betaArea;
    }

    public int getMaxWord() {
        return maxWord;
    }

    public void setMaxWord(int maxWord) {
        this.maxWord = maxWord;
    }

    public int getNumClusters() {
        return numClusters;
    }

    public void setNumClusters(int numClusters) {
        this.numClusters = numClusters;
    }

    public int getNumMoves() {
        return numMoves;
    }

    public void setNumMoves(int numMoves) {
        this.numMoves = numMoves;
    }

    public RTreeVariant getRTreeVariant() {
        return rTreeVariant;
    }

    public void setRTreeVariant(RTreeVariant rTreeVariant) {
        this.rTreeVariant = rTreeVariant;
    }

    public int getNearMinimumOverlapFactor() {
        return nearMinimumOverlapFactor;
    }

    public void setNearMinimumOverlapFactor(int nearMinimumOverlapFactor) {
        this.nearMinimumOverlapFactor = nearMinimumOverlapFactor;
    }

    public float getSmoothingFactor() {
        return smoothingFactor;
    }

    public void setSmoothingFactor(float smoothingFactor) {
        this.smoothingFactor = smoothingFactor;
    }

    public SpatialIndexType getSpatialIndexType() {
        return spatialIndexType;
    }

    public void setSpatialIndexType(SpatialIndexType spatialIndexType) {
        this.spatialIndexType = spatialIndexType;
    }

    public DataStructureType getDataStructureType() {
        return dataStructureType;
    }

    public void setDataStructureType(DataStructureType dataStructureType) {
        this.dataStructureType = dataStructureType;
    }

    public TextualIndexType getTextualIndexType() {
        return textualIndexType;
    }

    public void setTextualIndexType(TextualIndexType textualIndexType) {
        this.textualIndexType = textualIndexType;
    }

    public BulkLoadMethod getBulkLoadMethod() {
        return bulkLoadMethod;
    }

    public void setBulkLoadMethod(BulkLoadMethod bulkLoadMethod) {
        this.bulkLoadMethod = bulkLoadMethod;
    }
}

