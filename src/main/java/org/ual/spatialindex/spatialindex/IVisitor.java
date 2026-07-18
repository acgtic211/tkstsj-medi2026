package org.ual.spatialindex.spatialindex;

import java.util.ArrayList;

public interface IVisitor {
    void visitNode(final INode n);
    void visitData(final IData d);
    void visitData(ArrayList<IData> v);
}
