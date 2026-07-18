package org.ual.spatialindex.storage;

import java.util.HashSet;

public abstract class AbstractDocumentStore implements IStore {
    public static int maxWord;
    public abstract Weight read(int wordId);
    public abstract void write(Weight weight);
    public abstract HashSet<Integer> readSet(int id);
    public abstract int getSize();
}
