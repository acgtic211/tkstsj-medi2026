package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.querytype.AggregateSKNNQuery;

import java.util.List;
import java.util.Map;

public interface IAggregateQueryProcessor extends ISpatioTextualQueryProcessor {
    default List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk) {
        return gnnkBaseline(invertedFile, gnnkQuery, topk, RankingSumMode.defaultMode());
    }

    List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk) {
        return gnnk(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnkBaseline(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }

    List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk) {
        return sgnnk(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk, RankingSumMode scoringMode);

    default Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk) {
        return sgnnkExtended(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList, AggregateSKNNQuery query, int topk, RankingSumMode scoringMode);
}
