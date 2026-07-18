package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.SKJoinQuery;

import java.util.List;

public interface IJoinQueryProcessor extends ISpatioTextualQueryProcessor {
    List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedList, SKJoinQuery query,
                                                      float spatialThreshold, float textualThreshold,
                                                      JoinConfiguration joinConfiguration);

    List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedList, SKJoinQuery query,
                                                    float spatialThreshold, float textualThreshold,
                                                    JoinConfiguration joinConfiguration);
}
