package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.SimilarityType;

import java.util.Map;

public class CrossDatasetNodeConstraintSimilarityOps {
    private static final Logger logger = LogManager.getLogger(CrossDatasetNodeConstraintSimilarityOps.class);

    protected CrossDatasetNodeConstraintSimilarityOps() {}

    // TODO PLACEHOLDER
    protected double textualSimilarity(InvertedListEntry internalList, InvertedListEntry externalList,
                                          Map<Integer, Double> internalKeywordWeights,
                                          Map<Integer, Double> externalKeywordWeights, SimilarityType similarityType) {

        // Similarity its calculated for left side and right side individually with their respective keyword/weights
        // If Sim > 0, the pair is accepted.
        return 0.0;
    }
}
