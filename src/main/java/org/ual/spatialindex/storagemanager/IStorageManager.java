package org.ual.spatialindex.storagemanager;

import org.ual.spatialindex.spatialindex.INode;

import java.util.LinkedHashSet;
import java.util.Map;

public interface IStorageManager {
    static final int NewPage = -1;

    Map<Integer, LinkedHashSet<Integer>> getNodesInLevel();

    INode loadNode(final int id);
    int storeNode(final int id, final INode node);
    void deleteNode(final int id);
}
