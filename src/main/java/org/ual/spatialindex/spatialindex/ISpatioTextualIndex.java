package org.ual.spatialindex.spatialindex;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatiotextualindex.queries.JoinConfiguration;

import java.util.List;
import java.util.Map;

public interface ISpatioTextualIndex {
    default List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk) {
        return gnnkBaseline(invertedFile, gnnkQuery, topk, RankingSumMode.defaultMode());
    }
    List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnkBaseline(invertedFile, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }
    List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk) {
        return gnnk(invertedFile, gnnkQuery, topk, RankingSumMode.defaultMode());
    }
    List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk, RankingSumMode scoringMode);

    default List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnk(invertedFile, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }
    List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk, RankingSumMode scoringMode);

    default Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnkExtended(invertedFile, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }
    Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk, RankingSumMode scoringMode);

    List<SKNNQuery.Result> booleanRangeQuery(IDocumentIndex invertedFile, SKNNQuery query, float radius);
    List<SKNNQuery.Result> booleanKnnQuery(IDocumentIndex invertedFile, SKNNQuery query, int topk);
    default List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedFile, SKNNQuery query, int topk) {
        return topkKnnQuery(invertedFile, query, topk, RankingSumMode.defaultMode());
    }
    List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedFile, SKNNQuery query, int topk, RankingSumMode scoringMode);

    List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedFile, SKJoinQuery query,
                                                      float spatialThreshold, float textualThreshold,
                                                      JoinConfiguration joinConfiguration);

    List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedFile, SKJoinQuery query,
                                                      float spatialThreshold, float textualThreshold,
                                                      JoinConfiguration joinConfiguration);

    int getVisitedNodes();
    void setAlphaDistribution(float alphaDistribution);
    Integer getRootIdentifier();

    Node readNode(int nodeId);
}
