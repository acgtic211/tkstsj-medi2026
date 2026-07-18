package org.ual.spatialindex.rtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.rtreebase.AbstractRTree;
import org.ual.spatialindex.rtreebase.Index;
import org.ual.spatialindex.rtreebase.Leaf;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.utils.main.StatisticsLogic;

import java.util.ArrayList;
import java.util.Collections;

public class BulkLoader {
    private static final Logger logger = LogManager.getLogger(BulkLoader.class);

    public void bulkLoadUsingSTR(RTree rTree, ArrayList<AbstractRTree.Data> spatialData, int index, int leaf) {
        if (spatialData.isEmpty())
            throw new IllegalArgumentException("BulkLoadUsingSTR: spatialData cannot be empty");

        StatisticsLogic.startTimeMeasurement();
        // Read the root node and delete it.
        Node n = rTree.readNode(rTree.getRootIdentifier());
        rTree.deleteNode(n);
        StatisticsLogic.stopTimeMeasurement();
        logger.info("STR First read: {}  ms", StatisticsLogic.getElapsedTimeMillis());

        // Initialize the first sorter.
        ExternalSorter firstSorter = new ExternalSorter();

        StatisticsLogic.startTimeMeasurement();
        // Insert all data entries into the first sorter (root)
        for (AbstractRTree.Data data : spatialData) {
            firstSorter.insert(new Record(data.getIdentifier(), (Region) data.getShape(), 0));
        }
        // Sort the first sorter.
        firstSorter.sort();
        StatisticsLogic.stopTimeMeasurement();
        logger.info("STR insert and sort records: {}  ms", StatisticsLogic.getElapsedTimeMillis());

        // Update the statistics.
        rTree.getStatistics().setDataCount(firstSorter.getTotalEntries());

        // Create the rest of the levels (leafs).
        int level = 0;

        // The first level is a leaf level.
        while (true) {
            rTree.getStatistics().addNodeInLevel(0);

            // Create a new sorter for the next level.
            ExternalSorter secondSorter = new ExternalSorter();

            // Create the next level.
            createLevel(rTree, firstSorter, 0, leaf, index, level++, secondSorter);

            // Overwrite the first sorter with the second sorter.
            firstSorter = secondSorter;

            // If the first sorter has only one entry, then we are done.
            if (firstSorter.getTotalEntries() == 1)
                break;

            // Sort the first sorter.
            firstSorter.sort();
        }

        // Update the statistics with the tree height.
        rTree.getStatistics().setTreeHeight(level);
    }


    protected void createLevel(RTree rTree, ExternalSorter firstSorter, int dimension, int leaf, int index, int level,
                               ExternalSorter secondSorter) {
        // b is set to leaf or index depending on the level.
        int b = (level == 0) ? leaf : index; // b = branching factor.

        // Calculate nodes needed in this level (P) and nodes per stripe (S) in one step
        int totalEntries = firstSorter.getTotalEntries();
        int P = (int)(Math.ceil((double)totalEntries / (double)b)); // P = number of nodes in this level.
        int S = (int)(Math.ceil(Math.sqrt(P))); // S = number of nodes in a stripe.

        // Leaf Level or Last Dimension - process directly in these cases:
        // - If S is 1 (single stripe needed)
        // - Current dimension is the last dimension
        // - Entries perfectly fit into nodes
        if (S == 1 || dimension == rTree.getDimension() - 1 || S * b == totalEntries) {
            ArrayList<Record> nodeRecords = new ArrayList<>(b); // Pre-allocate for better performance
            Record record;

            // Process records in batches of size b
            while (true) {
                record = firstSorter.getNextRecord();
                if (record == null)
                    break;

                nodeRecords.add(record);

                // When we have b records, create a node
                if (nodeRecords.size() == b) {
                    Node node = createNode(rTree, nodeRecords, level);
                    nodeRecords.clear();
                    rTree.writeNode(node);
                    secondSorter.insert(new Record(node.getIdentifier(), node.getMBR(), 0));
                    rTree.setRootIdentifier(node.getIdentifier());
                }
            }

            // Handle any remaining records
            if (!nodeRecords.isEmpty()) {
                Node node = createNode(rTree, nodeRecords, level);
                rTree.writeNode(node);
                secondSorter.insert(new Record(node.getIdentifier(), node.getMBR(), 0));
                rTree.setRootIdentifier(node.getIdentifier());
            }
        } else {
            // Process records in stripes for higher dimensions
            boolean more = true;

            while (more) {
                ExternalSorter thirdSorter = new ExternalSorter();
                Record record;
                int count = 0;

                // Fill the third sorter with a stripe of records
                while (count < S * b) {
                    record = firstSorter.getNextRecord();
                    if (record == null) {
                        more = false;
                        break;
                    }

                    record.sortingDimension = dimension + 1;
                    thirdSorter.insert(record);
                    count++;
                }

                // Only sort and process if we have records
                if (count > 0) {
                    thirdSorter.sort();
                    createLevel(rTree, thirdSorter, dimension + 1, leaf, index, level, secondSorter);
                }
            }
        }
    }


    protected Node createNode(RTree rTree, ArrayList<Record> records, int level) {
        // Create the appropriate node type based on level
        Node node = (level == 0)
                ? new Leaf(rTree, -1)
                : new Index(rTree, -1, level);

        // Pre-calculate the number of records for potentially better memory allocation
        int recordCount = records.size();

        // Insert all records into the node - avoid iterator creation if possible
        for (int i = 0; i < recordCount; i++) {
            Record record = records.get(i);
            node.insertEntry(record.id, record.region);
        }

        // Clear the records collection to free memory
        records.clear();

        return node;
    }


    protected class ExternalSorter {
        private boolean insertionPhase;
        private final ArrayList<Record> buffer;
        private int totalEntries;
        private int currentIndex;

        public ExternalSorter() {
            this.insertionPhase = true;
            this.buffer = new ArrayList<>();
            this.totalEntries = 0;
            this.currentIndex = 0;
        }

        public void insert(Record record) {
            if (!insertionPhase) {
                throw new IllegalStateException("ExternalSorter::insert: Input has already been sorted.");
            }

            buffer.add(record);
            totalEntries++;
        }

        public void sort() {
            if (!insertionPhase) {
                throw new IllegalStateException("ExternalSorter.sort: Input has already been sorted.");
            }

            // The data fits in main memory. No need to store to disk.
            Collections.sort(buffer);
            insertionPhase = false;
        }

        public Record getNextRecord() {
            if (insertionPhase) {
                throw new IllegalStateException("ExternalSorter.getNextRecord: Input has not been sorted yet.");
            }

            if (currentIndex >= buffer.size()) {
                return null;
            }

            Record record = buffer.get(currentIndex);
            // Setting to null helps with garbage collection for large datasets
            buffer.set(currentIndex, null);
            currentIndex++;
            return record;
        }

        public int getTotalEntries() {
            return totalEntries;
        }
    }


    protected class Record implements Comparable<Record> {
        public int id;
        public Region region;
        public int sortingDimension;
        // length field was unused so it's been removed

        public Record(int id, Region region, int sortingDimension) {
            this.region = region;
            this.id = id;
            this.sortingDimension = sortingDimension;
        }

        @Override
        public int compareTo(Record other) {
            if (sortingDimension != other.sortingDimension) {
                throw new IllegalStateException("Record comparison requires matching sorting dimensions");
            }

            double thisCenter = region.getLow(sortingDimension) + region.getHigh(sortingDimension);
            double otherCenter = other.region.getLow(sortingDimension) + other.region.getHigh(sortingDimension);

            return Double.compare(thisCenter, otherCenter);
        }
    }
}
