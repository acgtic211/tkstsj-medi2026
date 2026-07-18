package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.spatialindex.ISpatioTextualIndex;

import java.util.List;

public interface IJoinMultiSetQueryProcessor extends ISpatioTextualQueryProcessor {
    List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                                      ISpatioTextualIndex secondaryTree, SKJoinQuery query,
                                                      float spatialThreshold, float textualThreshold,
                                                      JoinConfiguration joinConfiguration);

    List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                                      ISpatioTextualIndex secondaryTree, SKJoinQuery query,
                                                      float spatialThreshold, float textualThreshold,
                                                      JoinConfiguration joinConfiguration);
}
