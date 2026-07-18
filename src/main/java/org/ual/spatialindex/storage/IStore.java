package org.ual.spatialindex.storage;

import org.ual.spatialindex.storage.Weight;

import java.util.Iterator;

public interface IStore {
    Weight read(int wordId);
    void write(Weight weight);

    Iterator<Weight> iterator();
    int getSize();
}
