package org.ual.spatiotextualindex.queries.baseline.join;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.AbstractQueryProcessor;
import org.ual.spatiotextualindex.queries.QueryStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AbstractJoinQueryProcessor extends AbstractQueryProcessor {
    public AbstractJoinQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

    protected long buildDirectionalPairKey(int leftId, int rightId) {
        // Cross-dataset joins are directional: (A,B) and (B,A) are distinct pairs.
        return (((long) leftId) << 32) | (rightId & 0xffffffffL);
    }

    protected long buildOrderedPairKey(int id1, int id2) {
        int minId = Math.min(id1, id2);
        int maxId = Math.max(id1, id2);
        return (((long) minId) << 32) | (maxId & 0xffffffffL);
    }

    protected boolean usesExactTextualSimilarity(QueryStrategy queryStrategy) {
        return QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity();
    }

    protected double resolveTextualUpperBound(Map<Integer, Double> relevanceScores, int childIdX, int childIdY, QueryStrategy queryStrategy) {
        if (relevanceScores == null || relevanceScores.isEmpty()) {
            return 0.0;
        }

        if (usesExactTextualSimilarity(queryStrategy)) {
            double max = 0.0;
            for (double v : relevanceScores.values()) {
                max = Math.max(max, v);
            }
            return max;
        }

        return pairTextualUpperBound(relevanceScores, childIdX, childIdY);
    }

    protected double pairTextualUpperBound(Map<Integer, Double> relevanceScores, int entryIdA, int entryIdB) {
        if (relevanceScores == null || relevanceScores.isEmpty()) {
            return 0.0;
        }
        final double scoreA = relevanceScores.getOrDefault(entryIdA, 0.0);
        final double scoreB = relevanceScores.getOrDefault(entryIdB, 0.0);
        return Math.min(scoreA, scoreB);
    }

    protected double resolveLeafTextualSimilarity(IDocumentIndex invertedList,
                                                  int leafNodeIdX,
                                                  int objectIdX,
                                                  int leafNodeIdY,
                                                  int objectIdY,
                                                  SimilarityType similarityType,
                                                  QueryStrategy queryStrategy,
                                                  Map<Integer, Double> relevanceScores) {
        if (usesExactTextualSimilarity(queryStrategy)) {
            return invertedList.nodesDocumentSim(
                    leafNodeIdX, leafNodeIdY,
                    objectIdX, objectIdY,
                    similarityType);
        }

        return resolveTextualUpperBound(relevanceScores, objectIdX, objectIdY, queryStrategy);
    }

    protected Map<Integer, Double> calculateNodePairRelevanceScores(IDocumentIndex invertedList, int nodeIdX, int nodeIdY,
                                                                    List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType,
                                                                    QueryStrategy queryStrategy) {
        if (usesExactTextualSimilarity(queryStrategy)) {
            double score = invertedList.nodesTextualSim(nodeIdX, nodeIdY, similarityType);//, spatialThreshold);
            if (score <= 0.0) {
                return Collections.emptyMap();
            }
            return Collections.singletonMap(-1, score);
        }

        Map<Integer, Double> scores = invertedList.nodesConstraintTextualSim(
                nodeIdX, nodeIdY, keywords, keywordWeights, similarityType);

        return (scores == null) ? Collections.emptyMap() : scores;
    }

    protected static class NodePairRelevance {
        protected final Map<Integer, Double> leftScores;
        protected final Map<Integer, Double> rightScores;
        protected final double fullJoinUpperBound;

        protected NodePairRelevance(Map<Integer, Double> leftScores,
                                 Map<Integer, Double> rightScores,
                                 double fullJoinUpperBound) {
            this.leftScores = (leftScores == null) ? Collections.emptyMap() : leftScores;
            this.rightScores = (rightScores == null) ? Collections.emptyMap() : rightScores;
            this.fullJoinUpperBound = fullJoinUpperBound;
        }

        protected boolean isEmpty(QueryStrategy queryStrategy) {
            if (QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()) {
                return fullJoinUpperBound <= 0.0;
            }
            return leftScores.isEmpty() || rightScores.isEmpty();
        }
    }

    /**
     * Helper class for candidate pairs from plane-sweep.
     */
    protected static class CandidatePair {
        protected final int childIdX;
        protected final int childIdY;
        protected double spatialDistance;
        protected double textualUpperBound;

        protected CandidatePair(int childIdX, int childIdY, double spatialDistance, double textualUpperBound) {
            this.childIdX = childIdX;
            this.childIdY = childIdY;
            this.spatialDistance = spatialDistance;
            this.textualUpperBound = textualUpperBound;
        }
    }


}
