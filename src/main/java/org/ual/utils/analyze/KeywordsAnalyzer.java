package org.ual.utils.analyze;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;

import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;

public class KeywordsAnalyzer {
    private static final Logger logger = LogManager.getLogger(KeywordsAnalyzer.class);

    public static void main(String[] args) {
        DatasetParameters datasetParameters = ParametersFactory.getParameters(Dataset.TESTING_SET);
        analyze(datasetParameters.keywordFile);
    }

    //TODO: Clean up this method
    public static void analyze(String wordsFile) {
        // Contains <term, frequency> pairs for each term in the input file
        ArrayList<String[]> lines = new ArrayList<>();
        // Term Frequency dictionary
        HashMap<String, Integer> wordsFreq = new HashMap<>();

        try {
            LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile));
            int totalLength = 0;
            int totalLines = 0;
            double maxWeight = 0;

            String line = lr.readLine();    // 0,1,2,3,4,5
            while (line != null) {
                String[] cols = line.split(",");    // [0,1,2,3,4,5]
                lines.add(cols);
                for (int i = 1; i < cols.length; i++) {
                    totalLength++;
                    if (wordsFreq.containsKey(cols[i])) {
                        int count = wordsFreq.get(cols[i]);
                        wordsFreq.put(cols[i], count + 1);
                    } else {
                        wordsFreq.put(cols[i], 1);
                    }
                }
                line = lr.readLine();
            }
            lr.close();

            // For now print everything in the logger
            logger.info("Total lines read: {}", lines.size());
            logger.info("Total number of keywords: {}", totalLength);
//            int cnt = 0;
//            for(Integer freq : wordsFreq.values()) {
//                cnt+=freq;
//            }
//            logger.info("Total freq: {}", cnt);
            logger.info("Unique words:  {}", wordsFreq.size());

            // Sort the words by frequency and print the top 10
            ArrayList<String> topWords = new ArrayList<>();
            wordsFreq.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())).limit(10).forEach(e -> topWords.add(e.getKey()));
            logger.info("Top 10 words: {}", topWords);
            //logger.info("Unique keywords: {} - List: {}", wordsFreq.size(), wordsFreq.toString());

        }  catch (Exception e) {
            logger.error("Error while operating with weight file.", e);
        }
    }
}
