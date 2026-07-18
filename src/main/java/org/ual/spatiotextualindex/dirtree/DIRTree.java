package org.ual.spatiotextualindex.dirtree;

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
 * DIRTree (Document Inverted R-Tree) extends IRTreeBase to optimize spatio-textual
 * indexing and querying of geo-tagged documents.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Document-to-node mappings for efficient document-based lookups</li>
 *   <li>R*-tree spatial indexing with configurable split behavior (controlled by betaArea)</li>
 *   <li>Document-aware node operations and traversals</li>
 *   <li>Hierarchical inverted file structure for text indexing</li>
 * </ul>
 *
 * <p>The structure maintains document-node associations throughout the tree hierarchy,
 * supports validation of document integrity, and provides methods for building document
 * collections at each node level. The tree optimizes insertion based on both spatial
 * properties and document distributions.</p>
 */
public class DIRTree extends AbstractIRTree {
    private static final Logger logger = LogManager.getLogger(DIRTree.class);

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


    /**
     * Constructs a DIRTree instance with the specified property set, storage manager,
     * and dataset parameters.
     *
     * @param propertySet         The set of properties for configuring the DIRTree.
     * @param storageManager      The storage manager for handling document storage.
     * @param documentStore       The document store for managing document data.
     * @param datasetParameters   Parameters related to the dataset being processed.
     */
    public DIRTree(PropertySet propertySet, IStorageManager storageManager, AbstractDocumentStore documentStore, DatasetParameters datasetParameters) {
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
     * Returns the mapping between document IDs and their containing node IDs in the DIR-tree.
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
     * Inserts data into the DIRTree at the specified shape and ID.
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
     * Inserts data into the DIRTree at the specified region and ID.
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
     * Inserts data into the DIRTree at the specified shape, ID, and document set.
     * This method ensures that the shape has the correct number of dimensions before proceeding.
     *
     * @param shape The shape associated with the data.
     * @param id The identifier for the data.
     * @param doc The set of document IDs associated with the data.
     */
    @Override
    public void insertData(int id, final IShape shape, HashSet<Integer> doc) {
        if (shape.getDimension() != dimension)
            throw new IllegalArgumentException("insertData: Shape has the wrong number of dimensions.");

        Region mbr = shape.getMBR();
        insertDataImpl(id, mbr, doc);
    }

    /**
     * Implements the data insertion algorithm for the DIRTree structure.
     * This method:
     * <ol>
     *   <li>Finds the appropriate leaf node for insertion using R-tree traversal</li>
     *   <li>Inserts the data into the chosen leaf</li>
     *   <li>Handles any necessary node splits and propagation up the tree</li>
     * </ol>
     *
     * @param mbr The minimum bounding region of the data
     * @param id The identifier for the data entry
     * @param documents The set of document IDs associated with this entry
     */
    @Override
    protected void insertDataImpl(int id, Region mbr, HashSet<Integer> documents) {
        assert mbr.getDimension() == dimension;

        // Read the root node to start traversal
        Node root = readNode(rootID);

        // Initialize overflow tracking for each tree level
        HashMap<Integer, Boolean> overflowTable = new HashMap<>(root.getLevel());
        for (int i = 0; i < stats.getTreeHeight(); i++) {
            overflowTable.put(i, false); // Initialize all levels (0 = leaf, n = root) as not overflowing
        }

        // Track the path from root to leaf for split propagation
        Stack<Integer> pathBuffer = new Stack<>();

        // Find the best leaf node for insertion based on the MBR
        Node leafNode = root.chooseSubtree(mbr, 0, pathBuffer, documents);

        // Insert data into the leaf, handling any necessary splits
        leafNode.insertData(id, mbr, pathBuffer, overflowTable, documents);

        // Update statistics
        stats.incrementData();
    }


    /**
     * Creates the DIRTree structure by traversing the tree and building document collections.
     * This method processes the spatial index tree structure to build the document-based inverted file
     * representations at each node level.
     *
     * @param ds The data store containing document weights and information
     * @param invertedFile The document index to build during tree traversal
     * @return A list of weight entries representing the aggregated document information at the root
     */
    public List<WeightEntry> createDIRTree(IStore ds, IDocumentIndex invertedFile) {
        if (ds == null || invertedFile == null) {
            logger.error("Data store or inverted file is null");
            throw new IllegalArgumentException("Data store and inverted file must not be null");
        }

        logger.debug("Creating DIRTree structure with root ID: {}", rootID);
        // Read the root node to start the traversal process
        Node rootNode = readNode(rootID);
        if (rootNode == null) {
            logger.error("Failed to read root node with ID: {}", rootID);
            throw new IllegalStateException("Root node could not be retrieved");
        }

        return dirTraversal(ds, invertedFile, rootNode);
    }


    /**
     * Recursively traverses the DIR-Tree structure to build document collections at each node.
     * This method processes both leaf nodes (level 0) and index nodes, creating inverted file
     * entries for each node visited during the traversal.
     *
     * @param ds The data store containing document weights and information
     * @param invertedFile The document index being built during traversal
     * @param node The current node being processed in the traversal
     * @return A list of weight entries representing the aggregated document information for the current node
     * @throws IllegalStateException if a required document or pseudo-document cannot be found
     */
    private List<WeightEntry> dirTraversal(IStore ds, IDocumentIndex invertedFile, Node node) {
        final int nodeId = node.getIdentifier();

        // Initialize the inverted file entry for this node
        invertedFile.create(nodeId);
        final IAggregateDocumentIndex aggregateIndex =
                (invertedFile instanceof IAggregateDocumentIndex) ? (IAggregateDocumentIndex) invertedFile : null;

        if (node.getLevel() == 0) {
            // Process leaf node - gather documents from entries
            logger.debug("Processing leaf node: {}", nodeId);

            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int docId = entry.getKey();
                List<WeightEntry> document = ds.read(docId).weights;

                if (document == null) {
                    throw new IllegalStateException("Document not found with ID: " + docId);
                }

                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Leaf entry " + docId + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(nodeId, docId, document, mbr);
                } else {
                    invertedFile.addDocument(nodeId, docId, document);
                }
            }
        } else {
            // Process index node - recurse through child nodes
            logger.debug("Processing index node: {}", nodeId);

            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                int childNodeId = entry.getKey();
                Node childNode = readNode(childNodeId);
                if (childNode == null) {
                    throw new IllegalStateException("Unable to read child node " + childNodeId);
                }
                List<WeightEntry> childPseudoDoc = dirTraversal(ds, invertedFile, childNode);

                if (childPseudoDoc == null) {
                    throw new IllegalStateException("Failed to generate pseudo-document for node: " + childNodeId);
                }

                if (aggregateIndex != null) {
                    Region mbr = entry.getValue().getMBR();
                    if (mbr == null) {
                        throw new IllegalStateException("Internal entry " + childNodeId + " is missing MBR for aggregate textual index");
                    }
                    aggregateIndex.addDocument(nodeId, childNodeId, childPseudoDoc, mbr);
                } else {
                    invertedFile.addDocument(nodeId, childNodeId, childPseudoDoc);
                }
            }
        }

        // Store and return the aggregated pseudo-document for this node
        return invertedFile.store(nodeId);
    }


    /**
     * Validates the integrity of document collections across the entire tree structure.
     * This method performs a breadth-first traversal of the tree and verifies that:
     * 1. All nodes have valid document collections
     * 2. Index nodes have properly initialized child document sets
     * 3. Each node's document collection matches the union of its children's document sets
     *
     * @return true if the document structure is valid, false if any inconsistency is detected
     */
    public boolean validateDocumentStructure() {
        logger.debug("Starting document structure validation from root ID: {}", rootID);
        Stack<Integer> nodesToVisit = new Stack<>();
        nodesToVisit.push(rootID);
        int nodesChecked = 0;

        while (!nodesToVisit.isEmpty()) {
            int currentNodeId = nodesToVisit.pop();
            Node currentNode = readNode(currentNodeId);
            nodesChecked++;

            // Validate node has a document collection
            if (currentNode.getNodeDocuments() == null) {
                logger.error("Validation failed: Node {} has null document collection", currentNodeId);
                return false;
            }

            // For index nodes, verify document consistency between node and children
            if (!currentNode.isLeaf()) {
                // Collect all documents from children into a single set
                HashSet<Integer> combinedChildDocuments = new HashSet<>();

                // Iterate through node entries to get child documents
                for (Map.Entry<Integer, NodeEntry> entry : currentNode.getNodeEntries().entrySet()) {
                    Integer childNodeId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();

                    // Get documents from the node entry
                    if (nodeEntry.getDocument() != null) {
                        combinedChildDocuments.addAll(nodeEntry.getDocument());
                    }
                }

                // Compare node's document set with union of its children's document sets
                if (!currentNode.getNodeDocuments().equals(combinedChildDocuments)) {
                    logger.warn("Document inconsistency in node {}: node has {} documents, combined children have {} documents",
                            currentNodeId, currentNode.getNodeDocuments().size(), combinedChildDocuments.size());
                }

                // Queue child nodes for checking
                for (Integer childNodeId : currentNode.getNodeEntries().keySet()) {
                    nodesToVisit.push(childNodeId);
                }
            }
        }

        logger.info("Document structure validation complete: {} nodes checked", nodesChecked);
        return true;
    }


    /**
     * {@inheritDoc}
     * This is the DIR-Tree implementation of the GNNK baseline algorithm.
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
