package org.ual.spatiotextualindex.cdirtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IAggregateDocumentIndex;
import org.ual.documentindex.IDocumentIndex;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.IShape;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.storage.AbstractDocumentStore;
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
 * CDIRTree (Clustered Document Inverted R-Tree)
 * <p>
 * This class combines the functionality of DIRTree (Document Inverted R-Tree) and
 * CIRTree (Cluster-based Inverted R-tree) to provide a comprehensive spatial-textual
 * index structure. It extends {@link AbstractIRTree} with document clustering capabilities and
 * manages document-to-node mappings to optimize spatial-textual queries.
 * <p>
 * The tree maintains document organization in clusters while preserving spatial
 * relationships, supporting efficient document retrieval through its integration
 * with document store and inverted file components.
 */
public class CDIRTree extends AbstractIRTree {
    private static final Logger logger = LogManager.getLogger(CDIRTree.class);

    /**
     * Weighting factor used in R*-tree optimization for area-based node splitting decisions.
     * Controls the trade-off between minimizing area and overlap during node splits.
     * Value range: (0.0, 1.0). Lower values favor overlap minimization, higher values favor area minimization.
     */
    protected static float betaArea;

    /**
     * Maps document IDs to the set of node IDs they belong to in the tree structure.
     * Key: Document ID
     * Value: Set of node IDs containing the document
     */
    protected HashMap<Integer, HashSet<Integer>> documentNodeMapping;//doctree

    /**
     * Storage manager for document objects containing textual information and weights.
     * Handles persistence and retrieval of document data
     */
    protected AbstractDocumentStore documentStore;//objstore


    public CDIRTree(PropertySet propertySet, IStorageManager storageManager, AbstractDocumentStore documentStore, DatasetParameters datasetParameters) {
        super(propertySet, storageManager, datasetParameters, true);
        //setDocumentAware(true);
        this.documentStore = documentStore;
        this.documentNodeMapping = new HashMap<>();

        setBetaArea(getFloatProperty(propertySet, "BetaArea", i -> i > 0.0f && i < 1.0f,
                "Property BetaArea must be in (0.0, 1.0)"));
    }

    /**
     * Returns the beta area factor used in R*-tree optimization.
     *
     * @return The beta area factor.
     */
    @Override
    public float getBetaArea() {
        return betaArea;
    }

    /**
     * Sets the beta area factor used in R*-tree optimization.
     * The value must be between 0.0 and 1.0.
     *
     * @param beta The beta area factor to set.
     * @throws IllegalArgumentException if beta is not in the range (0.0, 1.0).
     */
    @Override
    public void setBetaArea(float beta) {
        if (beta <= 0.0f || beta >= 1.0f) {
            throw new IllegalArgumentException("Beta must be in range (0.0, 1.0)");
        }
        betaArea = beta;
        logger.debug("Set betaArea to: {}", beta);
    }

    /**
     * Returns the document store that manages the storage and retrieval of documents.
     * The document store handles operations related to textual information and weight entries
     * for each document in the DIR-tree.
     *
     * @return The AbstractDocumentStore instance used by this DIR-tree
     */
    @Override
    public AbstractDocumentStore getDocumentStore() {
        return documentStore;
    }


    /**
     * Returns the mapping between document IDs and their containing node IDs in the CDIR-tree.
     * Each document ID maps to a set of node IDs where the document appears, enabling efficient
     * document-to-node lookup operations. This mapping is crucial for document-aware operations
     * and maintaining the document distribution across the tree structure.
     *
     * @return A HashMap mapping document IDs (Integer) to sets of node IDs (HashSet<Integer>)
     *         where each document is stored
     */
    @Override
    public HashMap<Integer, HashSet<Integer>> getDocumentNodeMapping() {
        return documentNodeMapping;
    }

    /**
     * Inserts data into the CDIRTree at the specified shape and ID.
     * This method is not supported in this implementation. Use the version with document parameter instead.
     *
     * @param shape The shape associated with the data.
     * @param id The identifier for the data.
     * @throws UnsupportedOperationException if called directly.
     */
    @Override
    public void insertData(int id, final IShape shape) {
        throw new UnsupportedOperationException("This method is not supported. Use insertData with document parameter instead.");
    }

    /**
     * Inserts data into the CDIRTree at the specified region and ID.
     * This method is not supported in this implementation. Use the version with document parameter instead.
     *
     * @param region The region associated with the data.
     * @param id The identifier for the data.
     * @throws UnsupportedOperationException if called directly.
     */
    @Override
    protected void insertDataImpl(int id, Region region) {
        throw new UnsupportedOperationException("This method is not supported. Use the version with document parameter instead.");
    }

    /**
     * Inserts data into the CDIRTree at the specified shape and ID, along with a set of document IDs.
     * This method ensures that the shape's dimension matches the tree's dimension before proceeding with insertion.
     *
     * @param shape The shape associated with the data.
     * @param id The identifier for the data.
     * @param doc A set of document IDs associated with the data.
     * @throws IllegalArgumentException if the shape's dimension does not match the tree's dimension.
     */
    @Override
    public void insertData(int id, final IShape shape, HashSet<Integer> doc) {
        if (shape.getDimension() != dimension)
            throw new IllegalArgumentException("insertData: Shape has the wrong number of dimensions.");

        Region mbr = shape.getMBR();
        insertDataImpl(id, mbr, doc);
    }

    /**
     * Inserts data into the CDIRTree at the specified region and ID, along with a set of document IDs.
     * This method ensures that the region's dimension matches the tree's dimension before proceeding with insertion.
     *
     * @param mbr The region associated with the data.
     * @param id The identifier for the data.
     * @param doc A set of document IDs associated with the data.
     * @throws IllegalArgumentException if the region's dimension does not match the tree's dimension.
     */
    @Override
    protected void insertDataImpl(int id, Region mbr, HashSet<Integer> doc) {
        // Verify that the region dimension matches the tree dimension
        assert mbr.getDimension() == dimension;

        // Initialize path tracking and overflow handling structures
        Stack<Integer> pathBuffer = new Stack<>();

        // Read the root node
        Node root = readNode(rootID);

        // Initialize overflow tracking for each level in the tree
        HashMap<Integer, Boolean> overflowTable = new HashMap<>(root.getLevel());
        for (int i = 0; i < stats.getTreeHeight(); i++) {
            overflowTable.put(i, false); // Initialize all levels (0 = leaf, n = root) as not overflowing
        }


        // Find the appropriate leaf node for insertion based on the MBR and documents
        Node leaf = root.chooseSubtree(mbr, 0, pathBuffer, doc);

        // Insert the data into the chosen leaf node
        leaf.insertData(id, mbr, pathBuffer, overflowTable, doc);

        // Update statistics to reflect the inserted data
        stats.incrementData();
    }


    /**
     * Creates a CDIRTree structure based on the provided cluster tree, document store, and inverted file.
     * This method initializes and populates the CDIRTree by traversing the spatial index structure
     * and creating pseudo-documents for each node based on the cluster information.
     *
     * @param clusterTree    The mapping of document IDs to their respective cluster IDs (must not be null)
     * @param documentStore  The data store containing document weight information (must not be null)
     * @param invertedIndex   The inverted file index for managing document retrieval (must not be null)
     * @return              A list of cluster-based pseudo-documents, where each inner list
     *                      contains WeightEntry objects for a specific cluster
     * @throws IllegalArgumentException if any parameter is null
     */
    public List<List<WeightEntry>> createCDIRTree(Map<Integer, Integer> clusterTree,
                                                            AbstractDocumentStore documentStore, IDocumentIndex invertedIndex) {
        if (clusterTree == null) {
            logger.error("Cluster tree is null");
            throw new IllegalArgumentException("Cluster tree must be non-null");
        }
        if (documentStore == null) {
            logger.error("Document store is null");
            throw new IllegalArgumentException("Document store must be non-null");
        }
        if (invertedIndex == null) {
            logger.error("Inverted index is null");
            throw new IllegalArgumentException("Inverted index must be non-null");
        }

        logger.debug("Creating CDIRTree structure with root ID: {}", rootID);
        // Read the root node to start the traversal process
        Node rootNode = readNode(rootID);
        if (rootNode == null) {
            logger.error("Failed to read root node with ID: {}", rootID);
            throw new IllegalStateException("Root node could not be retrieved");
        }
        return cdirTraversal(clusterTree, documentStore, invertedIndex, rootNode);
    }


    /**
     * Traverses the CDIRTree recursively to build the structure and populate the inverted file with documents.
     * This method processes each node, creating appropriate document entries based on whether the node is a leaf (level 0)
     * or an internal node. It collects documents from the document store and adds them to the inverted file.
     *
     * @param clusterTree    The mapping of document IDs to their respective cluster IDs
     * @param documentStore  The data store containing document weight information
     * @param invertedFile   The inverted file index for managing document retrieval
     * @param node           The current node being processed in the traversal
     * @return               A list of cluster-based pseudo-documents
     */
    private List<List<WeightEntry>> cdirTraversal(Map<Integer, Integer> clusterTree, AbstractDocumentStore documentStore,
                                                            IDocumentIndex invertedFile, Node node) {
        // Create the inverted file entry for the current node
        invertedFile.create(node.getIdentifier());
        final IAggregateDocumentIndex aggregateIndex =
                (invertedFile instanceof IAggregateDocumentIndex) ? (IAggregateDocumentIndex) invertedFile : null;

        if (node.getLevel() == 0) {
            // Process leaf node
            logger.debug("Processing leaf node: {}", node.getIdentifier());

            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int docID = entry.getValue().getIdentifier();

                // Retrieve document weights
                List<WeightEntry> document = documentStore.read(docID).weights;
                if (document == null) {
                    logger.error("Couldn't find document with ID: {} (document is null)", docID);
                    throw new IllegalStateException("Document retrieval failed for ID: " + docID);
                }

                // Find the cluster for this document
                Integer cluster = clusterTree.get(docID);
                if (cluster == null) {
                    logger.error("Couldn't find cluster for document ID: {}", docID);
                    throw new IllegalStateException("Missing cluster information for document ID: " + docID);
                }

                logger.debug("Adding document: nodeID={}, docID={}, cluster={}", node.getIdentifier(), docID, cluster);
                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Leaf entry " + docID + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(node.getIdentifier(), docID, document, mbr, cluster);
                } else {
                    invertedFile.addDocument(node.getIdentifier(), docID, document, cluster);
                }
            }
        } else {
            // Process internal node
            logger.debug("Processing internal node: {}", node.getIdentifier());

            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int childID = entry.getValue().getIdentifier();
                Node childNode = readNode(childID);
                if (childNode == null) {
                    throw new IllegalStateException("Child node could not be retrieved for ID " + childID);
                }

                // Recursively process child node
                List<List<WeightEntry>> pseudoDoc = cdirTraversal(clusterTree, documentStore, invertedFile, childNode);

                if (pseudoDoc == null) {
                    logger.error("No pseudo-documents returned for child node: {}", childID);
                    throw new IllegalStateException("Traversal failed for child node: " + childID);
                }

                // Process each cluster's pseudo-document
                for (int clusterIndex = 0; clusterIndex < pseudoDoc.size(); clusterIndex++) {
                    if (pseudoDoc.get(clusterIndex).isEmpty()) {
                        continue;
                    }

                    if (aggregateIndex != null) {
                        Region mbr = entry.getValue().getMBR();
                        if (mbr == null) {
                            throw new IllegalStateException("Internal entry " + childID + " is missing MBR for aggregate textual index");
                        }
                        aggregateIndex.addDocument(node.getIdentifier(), childID, pseudoDoc.get(clusterIndex), mbr, clusterIndex);
                    } else {
                        invertedFile.addDocument(node.getIdentifier(), childID, pseudoDoc.get(clusterIndex), clusterIndex);
                    }
                    logger.debug("Adding inner document: nodeID={}, childID={}, cluster={}", node.getIdentifier(), childID, clusterIndex);
                }
            }
        }

        // Generate and return pseudo-documents for the current node
        return invertedFile.storeClusterEnhance(node.getIdentifier());
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
