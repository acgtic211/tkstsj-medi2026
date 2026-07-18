package org.ual.documentindex;

//import org.ual.documentindex.signedblock.SpatioTextualQueryContext;
//import org.ual.documentindex.signedblocknew.SpatioTextualQueryContextNEW;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.storage.WeightEntry;
import org.ual.spatiotextualindex.queries.QueryStrategy;

import java.util.List;
import java.util.Map;

public interface IAggregateDocumentIndex extends IDocumentIndex {
    void addDocument(int nodeId, int docId, List<WeightEntry> document, Region spatialRegion);
    void addDocument(int nodeId, int docId, List<WeightEntry> document, Region spatialRegion, int clusterId);

//    default Map<Integer, Double> rankingSum(int nodeId, SpatioTextualQueryContext ctx) {
//        return rankingSum(nodeId, ctx, RankingSumMode.defaultMode());
//    }

//    Map<Integer, Double> rankingSum(int nodeId, SpatioTextualQueryContext ctx, RankingSumMode scoringMode);

//    Map<Integer, Double> calculateTextualRelevancy(int nodeIdA, int nodeIdB, SpatioTextualQueryContext ctx);

    /**
     * Context-aware ranking entry point for aggregate indexes.
     *
     * <p>Default behavior keeps backward compatibility by delegating to the existing
     * keyword/weight API. Aggregate implementations can override this method to apply
     * additional block-level pruning using spatial query context.
     */
//    default Map<Integer, Double> rankingSum(int nodeId,
//                                            SpatioTextualQueryContextNEW ctx,
//                                            RankingSumMode scoringMode) {
//        if (ctx == null) {
//            throw new IllegalArgumentException("SpatioTextualQueryContextNEW must not be null");
//        }
//        return rankingSum(nodeId, ctx.getKeywords(), ctx.getKeywordWeights(), scoringMode);
//    }

    /**
     * Returns the global spatial extent used to normalize distances within this index.
     * This value is the maximum dimension extent of the root MBR (used for Hilbert encoding
     * and distance-to-similarity conversion).
     *
     * <p>Query processors use this to build a ... and to enable
     * block-level spatial pruning ({@code canPrune}) inside the signed-block index.
     *
     * @return global extent (> 0); 0.0 when the index does not support spatial scoring.
     */
    default double getGlobalExtent() {
        return 0.0;
    }

//    Map<Integer, Double> calculateTextualRelevancyClusterEnhance(int nodeId1, int nodeId2,
//                                                                 List<Integer> keywords, List<Double> keywordWeights,
//                                                                 SimilarityType similarityType, QueryStrategy queryStrategy);
}
