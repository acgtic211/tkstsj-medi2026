package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.querytype.SKNNQuery;

import java.util.List;

public interface IRankingQueryProcessor extends ISpatioTextualQueryProcessor {
    default List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        return topkKnnQuery(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk, RankingSumMode scoringMode);

    default List<SKNNQuery.Result> lkt(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        return lkt(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    List<SKNNQuery.Result> lkt(IDocumentIndex invertedList, SKNNQuery query, int topk, RankingSumMode scoringMode);
}
