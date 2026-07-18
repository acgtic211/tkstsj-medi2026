package org.ual.spatialindex.spatialindex;

public interface INode extends IEntry {
    int getLevel();
    long getNodeSignature();
    Region getMBR();
    boolean isIndex();
    boolean isLeaf();
}
