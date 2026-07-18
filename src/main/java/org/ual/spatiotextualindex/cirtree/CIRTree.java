package org.ual.spatiotextualindex.cirtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IAggregateDocumentIndex;
import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.storage.IStore;
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
 * A Cluster-based Inverted R-tree (CIR-tree) implementation that extends the IR-tree base structure.
 * The CIR-tree organizes spatial data into clusters and maintains inverted files at each node
 * to support efficient spatial keyword queries. It processes documents based on their spatial
 * locations and textual content, creating pseudo-documents for internal nodes using cluster information.
 * <p>
 * This implementation improves query performance by grouping similar documents into clusters,
 * reducing the search space during query execution.
 */
public class CIRTree extends AbstractIRTree {
    private static final Logger logger = LogManager.getLogger(CIRTree.class);

    /**
     * Constructs a CIRTree instance with the specified property set, storage manager,
     * and dataset parameters.
     *
     * @param propertySet         The set of properties for configuring the CIRTree.
     * @param storageManager      The storage manager for handling document storage.
     * @param datasetParameters   Parameters related to the dataset being processed.
     */
    public CIRTree(PropertySet propertySet, IStorageManager storageManager, DatasetParameters datasetParameters) {
        super(propertySet, storageManager, datasetParameters, false);
    }


    /**
     * Creates a CIRTree structure based on the provided cluster tree, dataset, and inverted file.
     * This method initializes and populates the CIRTree by traversing the spatial index structure
     * and creating pseudo-documents for each node based on the cluster information.
     *
     * @param clusterTree    The mapping of document IDs to their respective cluster IDs
     * @param documentStorage             The data store containing document weight information
     * @param invertedIndex   The inverted file index for managing document retrieval
     * @return              A list of cluster-based pseudo-documents, where each inner list
     *                      contains WeightEntry objects for a specific cluster
     * @throws IllegalArgumentException if ds or invertedFile is null
     * @throws IllegalStateException if the root node cannot be retrieved
     */
    public List<List<WeightEntry>> createCIRTree(Map<Integer, Integer> clusterTree, IStore documentStorage, IDocumentIndex invertedIndex) {
        if (clusterTree == null) {
            logger.error("Cluster tree is null");
            throw new IllegalArgumentException("Cluster tree must not be null");
        }
        if (documentStorage == null) {
            logger.error("Document storage is null");
            throw new IllegalArgumentException("Document storage must not be null");
        }
        if (invertedIndex == null) {
            logger.error("Inverted index is null");
            throw new IllegalArgumentException("Inverted index must not be null");
        }

        logger.debug("Creating CIRTree structure with root ID: {}", rootID);
        // Read the root node to start the traversal process
        Node rootNode = readNode(rootID);
        if (rootNode == null) {
            logger.error("Failed to read root node with ID: {}", rootID);
            throw new IllegalStateException("Root node could not be retrieved");
        }

        logger.debug("Starting CIRTree traversal from root node: {}", rootNode.getIdentifier());
        return cirTraversal(clusterTree, documentStorage, invertedIndex, rootNode);
    }


    /**
     * Performs a recursive traversal of the tree structure to build the CIRTree.
     * This method processes each node, creating appropriate document entries based on
     * whether the node is a leaf (level 0) or an internal node.
     *
     * @param clusterTree    The mapping of document IDs to their respective cluster IDs
     * @param ds             The data store containing document weight information
     * @param invertedIndex   The inverted file index for managing document retrieval
     * @param node           The current node being processed in the traversal
     * @return               A list of cluster-based pseudo-documents
     * @throws IllegalStateException if required documents or clusters cannot be found
     */
    private List<List<WeightEntry>> cirTraversal(Map<Integer, Integer> clusterTree, IStore ds,
                                                           IDocumentIndex invertedIndex, Node node) {
        // Create an entry in the inverted file for this node
        invertedIndex.create(node.getIdentifier());
        final IAggregateDocumentIndex aggregateIndex =
                (invertedIndex instanceof IAggregateDocumentIndex) ? (IAggregateDocumentIndex) invertedIndex : null;

        if (node.getLevel() == 0) {
            // Leaf node processing - iterate through nodeEntries
            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int docID = entry.getKey();

                ArrayList<WeightEntry> document = ds.read(docID).weights;
                if (document == null) {
                    logger.error("Couldn't find document {}", docID);
                    throw new IllegalStateException("Document " + docID + " could not be found in the data store");
                }

                Integer clusterID = clusterTree.get(docID);
                if (clusterID == null) {
                    logger.error("Couldn't find cluster for document {}", docID);
                    throw new IllegalStateException("No cluster found for document " + docID);
                }

                logger.debug("Adding DOC => nodeID: {} docID: {} DOC: {} Cluster: {}",
                        node.getIdentifier(), docID, document, clusterID);
                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Leaf entry " + docID + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(node.getIdentifier(), docID, document, mbr, clusterID);
                } else {
                    invertedIndex.addDocument(node.getIdentifier(), docID, document, clusterID);
                }
            }
        } else {
            // Internal node processing
            logger.debug("Processing index node: {}", node.getIdentifier());

            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int childNodeID = entry.getKey();
                Node childNode = readNode(childNodeID);
                if (childNode == null) {
                    throw new IllegalStateException("Child node could not be retrieved for ID " + childNodeID);
                }

                // Recursively process child node
                List<List<WeightEntry>> pseudoDocs = cirTraversal(clusterTree, ds, invertedIndex, childNode);

                if (pseudoDocs == null) {
                    logger.error("Couldn't find pseudo-documents for node {}", childNodeID);
                    throw new IllegalStateException("Pseudo-documents not found for node " + childNodeID);
                }

                // Add each cluster's document to the inverted file
                for (int clusterIndex = 0; clusterIndex < pseudoDocs.size(); clusterIndex++) {
                    if (pseudoDocs.get(clusterIndex).isEmpty()) {
                        continue;
                    }
                    if (aggregateIndex != null) {
                        Region mbr = entry.getValue().getMBR();
                        if (mbr == null) {
                            throw new IllegalStateException("Internal entry " + childNodeID + " is missing MBR for aggregate textual index");
                        }
                        aggregateIndex.addDocument(node.getIdentifier(), childNodeID,
                                pseudoDocs.get(clusterIndex), mbr, clusterIndex);
                    } else {
                        invertedIndex.addDocument(node.getIdentifier(), childNodeID,
                                pseudoDocs.get(clusterIndex), clusterIndex);
                    }
                    logger.debug("Adding inner DOC: {} - NodeID: {}", childNodeID, node.getIdentifier());
                }
            }
        }

        // Generate and return pseudo-documents for all clusters at this node
        return invertedIndex.storeClusterEnhance(node.getIdentifier());
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

    @Override
    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedFile, SKNNQuery query, int topk) {
        RankingQueryProcessor processor = new RankingQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.lkt(invertedFile, query, topk);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }
}
