package org.ual.utils.io;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.utils.ResultQueryCost;
import org.ual.utils.ResultQueryParameter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@Deprecated
public class StatisticsResultWriter {
    private static final Logger logger = LogManager.getLogger(StatisticsResultWriter.class);
    // Read

    // Write human-readable file
    public static void resultWriter(List<ResultQueryParameter> resultsByParam, String paramName, String queryType, String metricsDirectoryPath) {
        if(resultsByParam.isEmpty()) {
            logger.info("Skipping param: {} because is empty", paramName);
            return;
        }

        logger.info("Writing data for results in {}", queryType);
        String fileName = "[" + queryType + "]" + paramName; // [Aggregate] alpha

        // CPU
        try (FileWriter fw = new FileWriter(metricsDirectoryPath + fileName + ".txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            for(ResultQueryParameter resultData : resultsByParam) {
                out.println("Parameter: " + resultData.paramName + " - Value: " + resultData.paramValue);
                out.println("");
                //out.println("Type: " + resultData.typeName + " Value: " + resultData.typeValue);
                for(ResultQueryCost resultCost : resultData.results) {
                    out.printf("[%s] totalTime= %dms | avgTime= %dms | avgNodesVisited= %f | avgSpatCost= %.6f | avgIRCost= %.6f \n",
                            resultCost.queryName, resultCost.totalTime, resultCost.averageTime, resultCost.averageNodesVisited, resultCost.averageSpatialCost,
                            resultCost.averageIRCost);
                }
                out.println("");
                out.println("==================================================");
            }
        } catch (IOException e) {
            logger.error("Fail to write results", e);
        }
    }

    // Write in csv
    public static void writeCSV(List<ResultQueryParameter> resultsByParam, String paramName, String queryType, String metricsDirectoryPath) {
        if(resultsByParam.isEmpty()) {
            logger.info("Skipping param: {} because is empty", paramName);
            return;
        }

        ArrayList<String> headers = new ArrayList<>();
        headers.add(paramName);

        //HashMap<String, HashMap<String, String>> values = new HashMap<>();
        HashMap<String, ArrayList<String>> values = new HashMap<>();
        // paramName, paramVal, paramVal, ...
        // queryName, val, val, ...

        for(ResultQueryParameter resultData : resultsByParam) {
            headers.add(resultData.paramValue);
            for(ResultQueryCost resultCost : resultData.results) {
                //values.putIfAbsent(resultCost.queryName, new HashMap<>());
                //values.get(resultCost.queryName).putIfAbsent(resultData.paramValue, String.valueOf(resultCost.totalTime));
                values.putIfAbsent(resultCost.queryName, new ArrayList<>());
                values.get(resultCost.queryName).add(String.valueOf(resultCost.totalTime));
            }
        }

        logger.info("Writing data for results in {}", queryType);
        String fileName = "[" + queryType + "]" + paramName; // [Aggregate] alpha

        try (FileWriter fw = new FileWriter(metricsDirectoryPath + fileName + ".csv", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            // Write headers
            out.println(String.join(",", headers));

            for(Map.Entry<String, ArrayList<String>> row : values.entrySet()) {
                out.print(row.getKey());
                for(String val : row.getValue()) {
                    out.print(","  + val);
                }
                out.println();
            }
            out.println(String.join("", Collections.nCopies(headers.size(), ",")));

        } catch (IOException e) {
            logger.error("Fail to write results", e);
        }

    }

}
