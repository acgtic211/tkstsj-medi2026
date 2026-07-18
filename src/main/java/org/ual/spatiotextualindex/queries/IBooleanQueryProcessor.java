package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.SKNNQuery;

import java.util.List;

public interface IBooleanQueryProcessor extends ISpatioTextualQueryProcessor {
    List<SKNNQuery.Result> booleanRangeQuery(IDocumentIndex invertedList, SKNNQuery query, float radius);
    List<SKNNQuery.Result> booleanKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk);
}
