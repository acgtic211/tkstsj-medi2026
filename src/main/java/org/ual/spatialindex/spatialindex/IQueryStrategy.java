package org.ual.spatialindex.spatialindex;

public interface IQueryStrategy {
    void getNextEntry(IEntry e, int[] nextEntry, boolean[] hasNext);
}
