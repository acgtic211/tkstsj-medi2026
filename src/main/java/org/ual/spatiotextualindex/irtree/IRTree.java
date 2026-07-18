package org.ual.spatiotextualindex.irtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IAggregateDocumentIndex;
import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.IStore;
import org.ual.spatialindex.storage.Weight;
import org.ual.spatialindex.storage.WeightEntry;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.*;
import org.ual.spatiotextualindex.queries.baseline.AggregateQueryProcessor;
import org.ual.spatiotextualindex.queries.baseline.BooleanQueryProcessor;
import org.ual.spatiotextualindex.queries.baseline.join.JoinQueryProcessor;
import org.ual.spatiotextualindex.queries.baseline.RankingQueryProcessor;

import java.util.*;

/**
 * The IRTree class extends the IRTreeBase and provides functionality for
 * creating and traversing an IRTree structure for spatio-textual indexing.
 * It integrates spatial and textual data, allowing efficient storage and
 * retrieval of documents based on both spatial and textual attributes.
 */
public class IRTree extends AbstractIRTree {
    private static final Logger logger = LogManager.getLogger(IRTree.class);

    /**
     * Constructs an IRTree instance with the specified property set, storage manager,
     * and dataset parameters.
     *
     * @param propertySet         The set of properties for configuring the IRTree.
     * @param storageManager      The storage manager for handling document storage.
     * @param datasetParameters   Parameters related to the dataset being processed.
     */
    public IRTree(PropertySet propertySet, IStorageManager storageManager, DatasetParameters datasetParameters) {
        super(propertySet, storageManager, datasetParameters, false);
    }


    /**
     * Creates an IRTree structure based on the provided dataset and inverted file.
     * This method initializes the IRTree and sets up the necessary parameters for
     * storing and retrieving documents.
     *
     * @param ds                The data store containing document weights.
     * @param invertedFile      The inverted file index for document retrieval.
     * @return                  An ArrayList of WeightEntry objects representing the
     *                          pseudo-document structure of the IRTree.
     */
    public List<WeightEntry> createIRTree(IStore ds, IDocumentIndex invertedFile) {
        if (ds == null) {
            logger.error("Data store is null");
            throw new IllegalArgumentException("Data store must not be null");
        }
        if (invertedFile == null) {
            logger.error("Inverted file is null");
            throw new IllegalArgumentException("Inverted file must not be null");
        }

        logger.debug("Creating IRTree structure with root ID: {}", rootID);

        // Read the root node to start the traversal process
        Node rootNode = readNode(rootID);
        if (rootNode == null) {
            logger.error("Failed to read root node with ID: {}", rootID);
            throw new IllegalStateException("Root node could not be retrieved");
        }

        // Traverse the tree starting from the root node to build the inverted file index
        return irTraversal(ds, invertedFile, rootNode);
    }


    /**
     * Traverses the IRTree recursively to build the inverted file index.
     * This method processes each node in the tree, adding documents and their
     * associated weights to the inverted file.
     *
     * @param ds                The data store containing document weights.
     * @param invertedFile      The inverted file index for document retrieval.
     * @param node                 The current node being processed in the IRTree.
     * @return                  An ArrayList of WeightEntry objects representing
     *                          the pseudo-document structure of the IRTree.
     */
    private List<WeightEntry> irTraversal(IStore ds, IDocumentIndex invertedFile, Node node) {
        invertedFile.create(node.getIdentifier());
        final IAggregateDocumentIndex aggregateIndex =
                (invertedFile instanceof IAggregateDocumentIndex) ? (IAggregateDocumentIndex) invertedFile : null;

        TreeMap<Integer, NodeEntry> nodeEntries = node.getNodeEntries();
        if (nodeEntries == null || nodeEntries.isEmpty()) {
            logger.error("Node {} has no entries", node.getIdentifier());
            throw new IllegalStateException("Node must contain entries");
        }

        boolean isLeaf = (node.getLevel() == 0);

        if (isLeaf) {
            logger.debug("Processing leaf node: {}", node.getIdentifier());
            for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                int docID = entry.getKey();
                Weight weight = ds.read(docID);

                if (weight == null) {
                    logger.error("Document with ID: {} has no textual data", docID);
                    throw new IllegalStateException("Document " + docID + " must have textual data");
                }

                List<WeightEntry> document = weight.weights;
                if (document == null || document.isEmpty()) {
                    logger.error("Document with ID: {} has empty weights", docID);
                    throw new IllegalStateException("Document " + docID + " must have weight entries");
                }

                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Leaf entry " + docID + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(node.getIdentifier(), docID, document, mbr);
                } else {
                    invertedFile.addDocument(node.getIdentifier(), docID, document);
                }
            }
        } else {
            logger.debug("Processing index node: {}", node.getIdentifier());
            for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                int childID = entry.getKey();
                Node childNode = readNode(childID);

                if (childNode == null) {
                    logger.error("Child node not found with ID: {}", childID);
                    throw new IllegalStateException("Unable to read child node " + childID);
                }

                List<WeightEntry> pseudoDoc = irTraversal(ds, invertedFile, childNode);
                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Internal entry " + childID + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(node.getIdentifier(), childID, pseudoDoc, mbr);
                } else {
                    invertedFile.addDocument(node.getIdentifier(), childID, pseudoDoc);
                }
            }
        }

        List<WeightEntry> result = invertedFile.store(node.getIdentifier());
        if (result == null || result.isEmpty()) {
            logger.error("Node {} produced no textual data after processing", node.getIdentifier());
            throw new IllegalStateException("Node must produce valid weight entries");
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * This is the IR-Tree implementation of the GNNK baseline algorithm.
     */
    @Override
    public List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery, int topk) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.gnnkBaseline(invertedList, gnnkQuery, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.sgnnkBaseline(invertedList, sgnnkQuery, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery, int topk) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.gnnk(invertedList, gnnkQuery, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.sgnnk(invertedList, sgnnkQuery, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        Map<Integer, List<AggregateSKNNQuery.Result>> results = processor.sgnnkExtended(invertedList, sgnnkQuery, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKNNQuery.Result> booleanRangeQuery(IDocumentIndex invertedList, SKNNQuery query, float radius) {
        BooleanQueryProcessor processor = new BooleanQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.booleanRangeQuery(invertedList, query, radius);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKNNQuery.Result> booleanKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        BooleanQueryProcessor processor = new BooleanQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.booleanKnnQuery(invertedList, query, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        RankingQueryProcessor processor = new RankingQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.topkKnnQuery(invertedList, query, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedFile, SKNNQuery query, int topk) {
        RankingQueryProcessor processor = new RankingQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.lkt(invertedFile, query, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedList, SKJoinQuery query,
                                                             float spatialThreshold, float textualThreshold,
                                                             JoinConfiguration joinConfiguration) {
        JoinQueryProcessor processor = new JoinQueryProcessor(this);
        List<SKJoinQuery.Result> results = processor.selfJoinSKQueryBestFirst(
                invertedList, query, spatialThreshold, textualThreshold, joinConfiguration);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedList, SKJoinQuery query,
                                                             float spatialThreshold, float textualThreshold,
                                                             JoinConfiguration joinConfiguration) {
        JoinQueryProcessor processor = new JoinQueryProcessor(this);
        List<SKJoinQuery.Result> results = processor.selfJoinSKQueryRecursive(
                invertedList, query, spatialThreshold, textualThreshold, joinConfiguration);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }
}
