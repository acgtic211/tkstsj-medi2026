package org.ual.spatialindex.rtree;

import org.junit.jupiter.api.Test;
import org.ual.spatialindex.rtreebase.AbstractRTree;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RTreeMBRStressTest {

    @Test
    void randomBulkInsertMaintainsMbrConsistencyAcrossParents() {
        PropertySet properties = new PropertySet();
        properties.setProperty("Dimension", 2);
        properties.setProperty("IndexCapacity", 25);
        properties.setProperty("LeafCapacity", 25);
        properties.setProperty("FillFactor", 0.7f);
        properties.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        properties.setProperty("NearMinimumOverlapFactor", 8);

        DatasetParameters params = ParametersFactory.getParameters(Dataset.HOTEL_SET);
        RTree tree = new RTree(properties, new NodeStorageManager(), params, false);

        Random random = new Random(42L);
        final int inserts = 20_000;

        for (int i = 0; i < inserts; i++) {
            double x = random.nextDouble() * 10_000.0;
            double y = random.nextDouble() * 10_000.0;
            // Small boxes avoid degenerate zero-area cases while still stressing split logic.
            Region mbr = new Region(new double[]{x, y}, new double[]{x + 0.01, y + 0.01});
            tree.insertData(i, mbr);
        }

        assertTrue(tree.isIndexValid(), "Tree failed built-in structural validation after stress insertions");

        AbstractRTree.TreeQualitySnapshot snapshot = tree.collectTreeQualitySnapshot();
        assertEquals(0, snapshot.getParentContainmentViolations(),
                "Found parent containment violations in tree-quality snapshot");
        assertEquals(0, snapshot.getParentEntryMismatchViolations(),
                "Found parent-entry/child-node MBR mismatches in tree-quality snapshot");
    }
}

