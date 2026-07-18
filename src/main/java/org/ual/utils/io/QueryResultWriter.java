package org.ual.utils.io;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class QueryResultWriter {
    private static StringBuilder stringBuilder = new StringBuilder();
    private int queryCount = 0;

    private static final Logger logger = LogManager.getLogger(QueryResultWriter.class);

    public void writeAggregateSKNNResult(List<AggregateSKNNQuery.Result> results) {
        stringBuilder.append("Query ").append(queryCount).append("\n");
        for (AggregateSKNNQuery.Result result : results) {
            // Fix for NullPointer Reference
            if (result.getQueryIds() != null) {
                stringBuilder.append(String.format("ID: %d COST: %.3f QRY_IDs: %s\n", result.getId(), result.getAggregateCost().getCombinedCost(), result.getQueryIds()));
            } else {
                stringBuilder.append(String.format("ID: %d COST: %.3f\n", result.getId(), result.getAggregateCost().getCombinedCost()));
            }
        }
        queryCount++;
        stringBuilder.append("\n");
    }


    public void writeSKNNResult(List<SKNNQuery.Result> results) {
        stringBuilder.append("Query ").append(queryCount).append("\n");
        for (SKNNQuery.Result result : results) {
            stringBuilder.append(String.format("ID: %d - MinDist: %.3f - Cost: %.3f\n", result.getId(), result.getSpatialCost(), result.getSpatialCost()));
        }
        queryCount++;
        stringBuilder.append("\n");
    }

    public void writeSKJOINResult(List<SKJoinQuery.Result> results) {
        stringBuilder.append("Query ").append(queryCount).append("\n");
        for (SKJoinQuery.Result result : results) {
            stringBuilder.append(String.format("ID1: %d, ID2: %d - SpatialCost: %.3f - TextualCost: %.3f - CombineCost: %.3f\n",
                    result.getPairId1(), result.getPairId2(), result.spatialCost, result.textualCost, result.combineCost));
        }
            queryCount++;
            stringBuilder.append("\n");
    }


    public void writeLineSeparator() {
        stringBuilder.append("====================================================\n");
    }


    public void write(String str, boolean printNewline) {
        stringBuilder.append(str);
        if (printNewline) {
            stringBuilder.append("\n");
        }
    }


    // Writer to disk when done
    public void writeToDisk(String filePath, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath + fileName + ".txt", true))) {
            writer.append(stringBuilder);
            stringBuilder.setLength(0); // Reset the StringBuilder
        } catch (IOException e) {
            logger.error("Fail to write queries", e);
        }
//        try (FileWriter fw = new FileWriter(filePath + fileName + ".txt", true);
//             BufferedWriter bw = new BufferedWriter(fw);
//             bw.write(stringBuilder);
//             PrintWriter out = new PrintWriter(bw)) {
//            out.println("==================================================");
//            out.println("Number of Queries: " + queryCount);
//            out.println("==================================================");
//            out.append(stringBuilder);
//        } catch (IOException e) {
//            logger.error("Fail to write queries", e);
//        }
    }


}
