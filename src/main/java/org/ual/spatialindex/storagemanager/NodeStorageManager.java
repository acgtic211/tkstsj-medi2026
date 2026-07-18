package org.ual.spatialindex.storagemanager;

import org.ual.spatialindex.spatialindex.INode;

import java.util.*;

public class NodeStorageManager implements IStorageManager {
    private final LinkedHashMap<Integer, INode> treeStorage = new LinkedHashMap<>();
    private int nextId = 0;

//    private static final AtomicLong counter = new AtomicLong(0);
//
//    private static int generateUniqueId() {
//        return (int) counter.incrementAndGet();
//    }

    // Keep track of nodes in each level
    private final TreeMap<Integer, LinkedHashSet<Integer>> nodesInLevel = new TreeMap<>();

    @Override
    public Map<Integer, LinkedHashSet<Integer>> getNodesInLevel() {
        return nodesInLevel;
    }

    @Override
    public INode loadNode(int id) {
        return treeStorage.get(id);
    }

    @Override
    public int storeNode(int id, INode node) {
        int ret = id;

        if(id == NewPage) {
            ret = nextId++;//generateUniqueId();
            treeStorage.put(ret, node);
        } else {
            if (id < 0 || !treeStorage.containsKey(id)) throw new InvalidPageException(id);

            // Remove the old node from the level tracking if it exists
            INode oldNode = treeStorage.get(id);
            if (oldNode != null) {
                int oldLevel = oldNode.getLevel();
                nodesInLevel.get(oldLevel).remove(ret);
            }

            treeStorage.put(id, node);
        }

        int level = node.getLevel();
        nodesInLevel.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(ret);

        return ret;
    }

    @Override
    public void deleteNode(int id) {
        if (treeStorage.containsKey(id)) {
            INode node = treeStorage.get(id);
            if (node != null) {
                int level = node.getLevel();
                nodesInLevel.get(level).remove(id);
            }
            treeStorage.remove(id);
        } else {
            throw new InvalidPageException(id);
        }
    }
}
