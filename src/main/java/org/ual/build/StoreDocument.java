package org.ual.build;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.storage.*;

import java.util.*;

/**
 * Legacy class to process weights in an DocumentStore manager
 */
@Deprecated
public class StoreDocument {
    private static final Logger logger = LogManager.getLogger(StoreDocument.class);

    /**
     * Pass a weight list to store it in store manager.
     *
     * @param weights
     * @return storage manager
     */
    public static AbstractDocumentStore storeDocumentData(LinkedHashMap<String, LinkedHashMap<String, Double>> weights) {
        AbstractDocumentStore dms = new HashMapDocumentStore();
        ArrayList<WeightEntry> words;

        int count = 0;
        int id;

        long start = System.currentTimeMillis();

        for (Map.Entry<String, LinkedHashMap<String, Double>> lineEntry : weights.entrySet()) {
            id = Integer.parseInt(lineEntry.getKey());

            Iterator<Map.Entry<String, Double>> iter = lineEntry.getValue().entrySet().iterator();
            words = new ArrayList<>();

            while (iter.hasNext()) {
                Map.Entry<String, Double> entry = iter.next();
                WeightEntry de = new WeightEntry(Integer.parseInt(entry.getKey()), entry.getValue());
                words.add(de);
            }

            dms.write(new Weight(id, words));

            if ((count % 1000) == 0)
                logger.info("Operations: {}", count);

            count++;
        }

        long end = System.currentTimeMillis();

        logger.info("Operations: {}", count);
        logger.info("Time: {} minutes", ((end - start) / 1000.0f) / 60.0f);

        return dms;
    }
}
