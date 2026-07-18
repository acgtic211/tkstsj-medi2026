package org.ual.document;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.storage.IStore;
import org.ual.spatialindex.storage.Weight;
import org.ual.spatialindex.storage.WeightEntry;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.sampling.ContiguousWindowSampler;
import org.ual.utils.sampling.SamplingStrategy;
import org.ual.utils.sampling.SystematicSampler;

import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.*;

public class WeightCompute {
    private static final Logger logger = LogManager.getLogger(WeightCompute.class);


    /**
     * Generates a Weighted Keyword list from an input file (ex. words.txt).
     * This will populate {@code weightList} with {@code Weight} objects, which contains a {@code docID} and a collection of terms and their weights.
     *
     * The source file utils.Parameters contains some variables specific to the dataset. Edit these parameters if necessary.
     *
     * @param wordsFile
     * @param weightList
     */
    @Deprecated
    public static void ComputeTermWeights(String wordsFile, IStore weightList) {
        double lmd = 0.2; // smoothing factor

        // Contains <term, frequency> pairs for each term in the input file
        ArrayList<String[]> lines = new ArrayList<>();
        // Term Frequency dictionary
        HashMap<String, Integer> wordsFreq = new HashMap<>();
        // Weights per term
        ArrayList<WeightEntry> wordWeights = new ArrayList<>();

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        try {
            LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile));
            int totalLength = 0;
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

            // Calculate term weight based on total term frequency
            for (String[] cols : lines) {
                // Contains <term, frequency> pairs for each term in individual document
                HashMap<String, Integer> sent = new HashMap<>();
                String wordID = cols[0];

                for (int i = 1; i < cols.length; i++) {
                    if (sent.containsKey(cols[i])) {
                        int count = sent.get(cols[i]);
                        sent.put(cols[i], count + 1);
                    } else {
                        sent.put(cols[i], 1);
                    }
                }

                Iterator<Map.Entry<String, Integer>> iter = sent.entrySet().iterator();
                String buf = "";
                while (iter.hasNext()) {
                    Map.Entry<String, Integer> entry = iter.next();
                    String word = entry.getKey();
                    double termFreqInRow = entry.getValue();	// Term frequency in this row
                    double termTotalFreq = wordsFreq.get(word);			// Term frequency in all documents

                    double weight = (1 - lmd) * termFreqInRow / (cols.length - 1)
                            + lmd * termTotalFreq / totalLength;
                    weight = Math.pow(weight, termFreqInRow);
                    wordWeights.add(new WeightEntry(Integer.parseInt(word), weight));
                    buf += word + " " + weight + ",";
                    maxWeight = Math.max(maxWeight, weight);
                }
                buf = buf.substring(0, buf.length() - 1);
                logger.debug("WordID: {}, Weight: {}", wordID, buf);

                weightList.write(new Weight(Integer.parseInt(wordID), new ArrayList<>(wordWeights)));
                //weightList.write(Integer.parseInt(wordID), wordWeights);
                wordWeights.clear();
            }
        } catch (Exception e) {
            logger.error("Error while operating with weight file.", e);
        }
        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed(); // Memory without cleaning
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("Weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("Weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed/1024)/1024);
        logger.info("Weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed/1024)/1024);
        //long testMem = StatisticsLogic.getMemUsed();

//        logger.info("Weights memory usage: {} Megabytes", ((endMem - initMem)/1024)/1024);
//        logger.info("Weights clean memory usage: {} Megabytes", ((testMem)/1024)/1024);
    }


    /**
     * Generates a Weighted Keyword list from an input file (ex. words.txt).
     * This will populate {@code weightList} with {@code Weight} objects, which contains a {@code docID} and a collection of terms and their weights.
     *
     * The source file utils.Parameters contains some variables specific to the dataset. Edit these parameters if necessary.
     *
     * @param wordsFile filepath
     * @param weightList list of weights
     */
    @Deprecated
    public static void ComputeTermWeights(String wordsFile, List<Weight> weightList) {
        double lmd = 0.2; // smoothing factor

        // Contains <term, frequency> pairs for each term in the input file
        ArrayList<String[]> lines = new ArrayList<>();
        // Term Frequency dictionary
        HashMap<String, Integer> wordsFreq = new HashMap<>();
        // Weights per term
        ArrayList<WeightEntry> wordWeights = new ArrayList<>();


        try {
            LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile));
            int totalLength = 0;
            double maxWeight = 0;

            long start = System.currentTimeMillis();

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

            // Calculate term weight based on total term frequency
            for (String[] cols : lines) {
                // Contains <term, frequency> pairs for each term in individual document
                HashMap<String, Integer> sent = new HashMap<>();
                String wordID = cols[0];

                for (int i = 1; i < cols.length; i++) {
                    if (sent.containsKey(cols[i])) {
                        int count = sent.get(cols[i]);
                        sent.put(cols[i], count + 1);
                    } else {
                        sent.put(cols[i], 1);
                    }
                }

                Iterator<Map.Entry<String, Integer>> iter = sent.entrySet().iterator();
                String buf = "";
                while (iter.hasNext()) {
                    Map.Entry<String, Integer> entry = iter.next();
                    String word = entry.getKey();
                    double termFreqInRow = entry.getValue();	// Term frequency in this row
                    double termTotalFreq = wordsFreq.get(word);			// Term frequency in all documents

                    double weight = (1 - lmd) * termFreqInRow / (cols.length - 1)
                            + lmd * termTotalFreq / totalLength;
                    weight = Math.pow(weight, termFreqInRow);
                    wordWeights.add(new WeightEntry(Integer.parseInt(word), weight));
                    buf += word + " " + weight + ",";
                    maxWeight = Math.max(maxWeight, weight);
                }
                buf = buf.substring(0, buf.length() - 1);
                logger.debug("WordID: {}, Weight: {}", wordID, buf);
                //System.out.println(wordID + "," + buf);

                weightList.add(new Weight(Integer.parseInt(wordID), wordWeights));
                wordWeights.clear();
            }
            long end = System.currentTimeMillis();
            logger.info("Weight processing done in: {} ms", (end - start));
        } catch (Exception e) {
            logger.error("Error while operating with weight file.", e);
        }

    }



    /**
      * Generates a Weighted Keyword list from an input file by processing document terms and calculating their weights.
      * This will populate {@code weightList} with {@code Weight} objects, each containing a {@code docID} and a collection
      * of terms with their computed weights using a smoothing-based approach:
      * <p>
      * weight = (1 - λ) * (tf_d/|d|) + λ * (tf_c/|c|)
      * final_weight = weight^tf_d
      * <p>
      * where:
      * <ul>
      * <li>λ (smoothingFactor): balance between local and collection weights [0,1]
      * <li>tf_d: term frequency in current document
      * <li>|d|: total terms in current document
      * <li>tf_c: term frequency in entire collection
      * <li>|c|: total terms in collection
      * </ul>
      *
      * @param wordsFile path to input file containing document terms in CSV format (docID,term1,term2,...)
      * @param weightList storage interface for persisting computed weights
      * @param smoothingFactor value between 0 and 1 that balances local and global term frequencies
      */
//    public static void ComputeTermWeights(String wordsFile, IStore weightList, double smoothingFactor) {
//         // Contains <term, frequency> pairs for each term in the input file
//         List<String[]> documentLines = new ArrayList<>();
//         // Term Frequency dictionary
//         Map<String, Integer> globalTermFrequency = new HashMap<>();
//
//         long initMem = StatisticsLogic.getClearedMem();
//         long startTime = System.currentTimeMillis();
//
//         HashSet<Integer> ids = new HashSet<>(); // To track unique document IDs
//         try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
//             int totalTermCount = 0;
//
//             // First pass: read all documents and count global term frequencies
//             String line = lr.readLine();
//             while (line != null) {
//                 String[] terms = line.split(",");
//
//                 int id = Integer.parseInt(terms[0]);
//                 if (ids.contains(id)) {
//                     //logger.error("Duplicate id " + id + " in Keyword file. Skipping this entry.");
//                     line = lr.readLine();
//                     continue;
//                 }
//                 ids.add(id);
//
//
//                 documentLines.add(terms);
//
//                 // Count terms starting from index 1 (skip document ID at index 0)
//                 for (int i = 1; i < terms.length; i++) {
//                     totalTermCount++;
//                     globalTermFrequency.merge(terms[i], 1, Integer::sum);
//                 }
//                 line = lr.readLine();
//             }
//
//             // Second pass: calculate weights for each term in each document
//             for (String[] terms : documentLines) {
//                 String documentId = terms[0];
//                 int documentLength = terms.length - 1;
//
//                 // Count term frequencies within this document
//                 Map<String, Integer> localTermFrequency = new HashMap<>();
//                 for (int i = 1; i < terms.length; i++) {
//                     localTermFrequency.merge(terms[i], 1, Integer::sum);
//                 }
//
//                 // Calculate weights for each term in this document
//                 List<WeightEntry> documentWeights = new ArrayList<>();
//                 StringBuilder debugInfo = new StringBuilder();
//
//                 for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
//                     String term = entry.getKey();
//                     double termFreqInDocument = entry.getValue();
//                     double termFreqGlobal = globalTermFrequency.get(term);
//
//                     // Calculate term weight using smoothing
//                     double weight = (1 - smoothingFactor) * termFreqInDocument / documentLength
//                             + smoothingFactor * termFreqGlobal / totalTermCount;
//                     weight = Math.pow(weight, termFreqInDocument);
//
//                     int termId = Integer.parseInt(term);
//                     documentWeights.add(new WeightEntry(termId, weight));
//
//                     // Append to debug info
//                     if (logger.isDebugEnabled()) {
//                         debugInfo.append(term).append(" ").append(weight).append(",");
//                     }
//                 }
//
//                 // Log debug information if needed
//                 if (logger.isDebugEnabled() && debugInfo.length() > 0) {
//                     debugInfo.setLength(debugInfo.length() - 1); // Remove trailing comma
//                     logger.debug("WordID: {}, Weight: {}", documentId, debugInfo);
//                 }
//
//                 // Store the weights
//                 weightList.write(new Weight(Integer.parseInt(documentId), (ArrayList<WeightEntry>) documentWeights));
//             }
//         } catch (Exception e) {
//             logger.error("Error while processing weight file: {}", e.getMessage(), e);
//         }
//
//         // Record statistics
//         long stopTime = System.currentTimeMillis();
//         long endMem = StatisticsLogic.getMemUsed();
//         StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
//         StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
//         StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);
//
//         // Log performance metrics
//         logger.info("Weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
//         logger.info("Weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed/1024)/1024);
//         logger.info("Weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed/1024)/1024);
//     }

    public static void ComputeTermWeights(String wordsFile, IStore weightList, double smoothingFactor) {
        // Store document lines and term frequencies
        List<String[]> documentLines = new ArrayList<>();
        Map<String, Integer> globalTermFrequency = new HashMap<>();
        Set<Integer> ids = new HashSet<>();

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        int totalTermCount = 0;

        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
            String line;
            while ((line = lr.readLine()) != null) {
                String[] terms = line.split(",");
                int id = Integer.parseInt(terms[0]);
                if (!ids.add(id)) {
                    continue; // Skip duplicate IDs
                }
                documentLines.add(terms);
                for (int i = 1; i < terms.length; i++) {
                    totalTermCount++;
                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            logger.error("Error while reading weight file: {}", e.getMessage(), e);
            return;
        }

        // Calculate weights for each document
        for (String[] terms : documentLines) {
            String documentId = terms[0];
            int documentLength = terms.length - 1;
            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", documentId);
                continue;
            }

            Map<String, Integer> localTermFrequency = new HashMap<>();
            for (int i = 1; i < terms.length; i++) {
                localTermFrequency.merge(terms[i], 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;

            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
                String term = entry.getKey();
                int termFreqInDocument = entry.getValue();
                int termFreqGlobal = globalTermFrequency.get(term);

                double weight = (1 - smoothingFactor) * ((double) termFreqInDocument / documentLength)
                        + smoothingFactor * ((double) termFreqGlobal / totalTermCount);
                weight = Math.pow(weight, termFreqInDocument);

                int termId = Integer.parseInt(term);
                documentWeights.add(new WeightEntry(termId, weight));

                if (debugInfo != null) {
                    debugInfo.append(term).append(" ").append(weight).append(",");
                }
            }

            if (debugInfo != null && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1);
                logger.debug("WordID: {}, Weight: {}", documentId, debugInfo);
            }

            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
        }

        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("Weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("Weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("Weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }


    // TODO Testing
    public static void ComputeTermWeights(String wordsFile, IStore weightList, double smoothingFactor, double datasetUsagePercentage) {
        // Store document lines and term frequencies
        List<String[]> documentLines = new ArrayList<>();
        Map<String, Integer> globalTermFrequency = new HashMap<>();
        Set<Integer> ids = new HashSet<>();

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        int totalTermCount = 0;
        int targetLineCount = Integer.MAX_VALUE;

        // Count total lines if using percentage
        if (datasetUsagePercentage < 1.0) {
            try (LineNumberReader countReader = new LineNumberReader(new FileReader(wordsFile))) {
                while (countReader.readLine() != null) {
                    // Just count lines
                }
                int totalLines = countReader.getLineNumber();
                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
                logger.info("Using {}% of dataset: {} out of {} lines",
                        datasetUsagePercentage * 100, targetLineCount, totalLines);
            } catch (Exception e) {
                logger.error("Error counting lines in file: {}", e.getMessage(), e);
                return;
            }
        }

        // Read only target number of lines
        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
            String line;
            int linesRead = 0;

            while ((line = lr.readLine()) != null && linesRead < targetLineCount) {
                String[] terms = line.split(",");
                int id = Integer.parseInt(terms[0]);
                if (!ids.add(id)) {
                    continue; // Skip duplicate IDs
                }
                documentLines.add(terms);
                linesRead++;

                for (int i = 1; i < terms.length; i++) {
                    totalTermCount++;
                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            logger.error("Error while reading weight file: {}", e.getMessage(), e);
            return;
        }

        // Calculate weights for each document
        for (String[] terms : documentLines) {
            String documentId = terms[0];
            int documentLength = terms.length - 1;
            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", documentId);
                continue;
            }

            Map<String, Integer> localTermFrequency = new HashMap<>();
            for (int i = 1; i < terms.length; i++) {
                localTermFrequency.merge(terms[i], 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;

            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
                String term = entry.getKey();
                int termFreqInDocument = entry.getValue();
                int termFreqGlobal = globalTermFrequency.get(term);

                double weight = (1 - smoothingFactor) * ((double) termFreqInDocument / documentLength)
                        + smoothingFactor * ((double) termFreqGlobal / totalTermCount);
                weight = Math.pow(weight, termFreqInDocument);

                int termId = Integer.parseInt(term);
                documentWeights.add(new WeightEntry(termId, weight));

                if (debugInfo != null) {
                    debugInfo.append(term).append(" ").append(weight).append(",");
                }
            }

            if (debugInfo != null && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1);
                logger.debug("WordID: {}, Weight: {}", documentId, debugInfo);
            }

            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
        }

        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("Weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("Weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("Weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }


    /**
     * Calculates weights for a collection of textual objects where each object is represented as:
     * <docID, [termID₁, termID₂, ..., termIDₙ]>
     *
     * Input format:
     * - docID: Unique identifier for each document
     * - termIDₙ: Integer IDs representing terms in the document, where duplicates indicate term frequency
     *
     * The weight for each unique term in a document is calculated using a smoothing-based formula:
     *
     * Initial weight calculation:
     *    weight = (1 - λ) * (tf_d/|d|) + λ * (tf_c/|c|)
     *
     * Final weight with frequency boost:
     *    final_weight = weight^tf_d
     *
     * Parameters:
     * - λ (smoothingFactor): Smoothing parameter in range [0,1]
     *                        0 = only local weights, 1 = only global weights
     * - tf_d: Term frequency in current document (local count)
     * - |d|: Document length (total term count in document)
     * - tf_c: Term frequency in collection (global count)
     * - |c|: Collection length (total term count across all documents)
     */
    public static void ComputeTermWeights(HashMap<Integer, ArrayList<Integer>> textualObjects, IStore weightList, double smoothingFactor) {
        // Term Frequency dictionary for the entire collection
        Map<Integer, Integer> globalTermFrequency = new HashMap<>();
        int totalTermCountInCollection = 0;

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        // First pass: Calculate global term frequencies and total term count
        for (ArrayList<Integer> termsInDoc : textualObjects.values()) {
            for (Integer termId : termsInDoc) {
                globalTermFrequency.merge(termId, 1, Integer::sum);
                totalTermCountInCollection++;
            }
        }

        // Second pass: Calculate weights for each term in each document
        for (Map.Entry<Integer, ArrayList<Integer>> docEntry : textualObjects.entrySet()) {
            Integer docId = docEntry.getKey();
            ArrayList<Integer> termsInDoc = docEntry.getValue();
            int documentLength = termsInDoc.size();

            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", docId);
                continue;
            }

            // Count term frequencies within this document
            Map<Integer, Integer> localTermFrequency = new HashMap<>();
            for (Integer termId : termsInDoc) {
                localTermFrequency.merge(termId, 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>();
            StringBuilder debugInfo = new StringBuilder();

            for (Map.Entry<Integer, Integer> termEntry : localTermFrequency.entrySet()) {
                Integer termId = termEntry.getKey();
                double termFreqInDocument = termEntry.getValue();
                double termFreqGlobal = globalTermFrequency.getOrDefault(termId, 0); // Should always exist if term is in a doc

                // Calculate term weight using smoothing
                double weight = (1 - smoothingFactor) * (termFreqInDocument / documentLength)
                        + smoothingFactor * (termFreqGlobal / totalTermCountInCollection);
                weight = Math.pow(weight, termFreqInDocument);

                documentWeights.add(new WeightEntry(termId, weight));

                // Append to debug info
                if (logger.isDebugEnabled()) {
                    debugInfo.append(termId).append(" ").append(weight).append(",");
                }
            }

            // Log debug information if needed
            if (logger.isDebugEnabled() && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1); // Remove trailing comma
                logger.debug("DocID: {}, Weights: {}", docId, debugInfo);
            }

            // Store the weights
            weightList.write(new Weight(docId, (ArrayList<WeightEntry>) documentWeights));
        }

        // Record statistics
        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        // Log performance metrics
        logger.info("Weight processing from HashMap done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("Weights peak memory usage (HashMap input): {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("Weights clean memory usage (HashMap input): {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }


    public static void ComputeTF_IDFWeights(String wordsFile, IStore weightList, double smoothingFactor) {
        // Store document lines and term frequencies
        List<String[]> documentLines = new ArrayList<>();
        Map<String, Integer> globalTermFrequency = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        // DF: number of documents containing each term
        Set<Integer> ids = new HashSet<>();


        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        int totalDocumentCount = 0;

        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
            String line;
            while ((line = lr.readLine()) != null) {
                String[] terms = line.split(",");
                int id = Integer.parseInt(terms[0]);
                if (!ids.add(id)) {
                    continue; // Skip duplicate IDs
                }
                documentLines.add(terms);
                totalDocumentCount++;

                // Track unique terms in this document for DF calculation
                Set<String> uniqueTermsInDoc = new HashSet<>();
                for (int i = 1; i < terms.length; i++) {
                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
                    uniqueTermsInDoc.add(terms[i]);
                }

                // Update document frequency for each unique term in this document
                for (String term : uniqueTermsInDoc) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            logger.error("Error while reading weight file: {}", e.getMessage(), e);
            return;
        }

        // Calculate TF-IDF weights for each document
        for (String[] terms : documentLines) {
            String documentId = terms[0];
            int documentLength = terms.length - 1;
            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", documentId);
                continue;
            }

            Map<String, Integer> localTermFrequency = new HashMap<>();
            for (int i = 1; i < terms.length; i++) {
                localTermFrequency.merge(terms[i], 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;

            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
                String term = entry.getKey();
                int termFreqInDocument = entry.getValue();
                int docFreq = documentFrequency.get(term);

                // Use normalized TF that's more suitable for weighted Jaccard
                double tf = 0.1 + 0.9 * ((double) termFreqInDocument /
                        Collections.max(localTermFrequency.values()));

                // Use smoother IDF that doesn't penalize common terms too heavily
                double idf = 1.0 + Math.log((double) totalDocumentCount / docFreq);

                // Calculate base TF-IDF weight
                double tfidfWeight = tf * idf;

                // Apply additional boosting for frequent terms in document
                double frequencyBoost = 1.0 + Math.log(1.0 + termFreqInDocument);
                tfidfWeight *= frequencyBoost;

                // Apply smoothing factor if specified
                if (smoothingFactor > 0) {
                    int termFreqGlobal = globalTermFrequency.get(term);
                    double globalWeight = (double) termFreqGlobal / (totalDocumentCount * documentLength);
                    tfidfWeight = (1 - smoothingFactor) * tfidfWeight + smoothingFactor * globalWeight;
                }

                int termId = Integer.parseInt(term);
                documentWeights.add(new WeightEntry(termId, tfidfWeight));

                if (debugInfo != null) {
                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
                }
            }

            if (debugInfo != null && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1);
                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
            }

            // Use square root normalization to preserve relative differences while boosting smaller values
            if (!documentWeights.isEmpty()) {
                double maxWeight = documentWeights.stream().mapToDouble(w -> w.weight).max().orElse(1.0);

                for (WeightEntry weightEntry : documentWeights) {
                    // Square root normalization followed by scaling to [0.1, 1.0]
                    double normalizedWeight = Math.sqrt(weightEntry.weight / maxWeight);
                    weightEntry.weight = 0.1 + 0.9 * normalizedWeight;
                }
            }

            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
        }

        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }

    // TODO TESTING: dataset usage
//    public static void ComputeTF_IDFWeights(String wordsFile, IStore weightList, double smoothingFactor, double datasetUsagePercentage) {
//        // Store document lines and term frequencies
//        List<String[]> documentLines = new ArrayList<>();
//        Map<String, Integer> globalTermFrequency = new HashMap<>();
//        Map<String, Integer> documentFrequency = new HashMap<>();
//        // DF: number of documents containing each term
//        Set<Integer> ids = new HashSet<>();
//
//
//        long initMem = StatisticsLogic.getClearedMem();
//        long startTime = System.currentTimeMillis();
//
//        int totalDocumentCount = 0;
//        int targetLineCount = Integer.MAX_VALUE;
//
//        // First pass: count total lines to calculate target
//        if (datasetUsagePercentage < 1.0) {
//            try (LineNumberReader countReader = new LineNumberReader(new FileReader(wordsFile))) {
//                while (countReader.readLine() != null) {
//                    // Just count lines
//                }
//                int totalLines = countReader.getLineNumber();
//                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
//                logger.info("Using {}% of dataset: {} out of {} lines",
//                        datasetUsagePercentage * 100, targetLineCount, totalLines);
//            } catch (Exception e) {
//                logger.error("Error counting lines in file: {}", e.getMessage(), e);
//                return;
//            }
//        }
//
//        // Second pass: read only the target number of lines
//        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
//            String line;
//            int linesRead = 0;
//
//            while ((line = lr.readLine()) != null && linesRead < targetLineCount) {
//                String[] terms = line.split(",");
//                int id = Integer.parseInt(terms[0]);
//                if (!ids.add(id)) {
//                    continue; // Skip duplicate IDs
//                }
//                documentLines.add(terms);
//                totalDocumentCount++;
//                linesRead++;
//
//                // Track unique terms in this document for DF calculation
//                Set<String> uniqueTermsInDoc = new HashSet<>();
//                for (int i = 1; i < terms.length; i++) {
//                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
//                    uniqueTermsInDoc.add(terms[i]);
//                }
//
//                // Update document frequency for each unique term in this document
//                for (String term : uniqueTermsInDoc) {
//                    documentFrequency.merge(term, 1, Integer::sum);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error while reading weight file: {}", e.getMessage(), e);
//            return;
//        }
//
//        // Calculate TF-IDF weights for each document
//        for (String[] terms : documentLines) {
//            String documentId = terms[0];
//            int documentLength = terms.length - 1;
//            if (documentLength == 0) {
//                logger.warn("Document with ID {} is empty, skipping.", documentId);
//                continue;
//            }
//
//            Map<String, Integer> localTermFrequency = new HashMap<>();
//            for (int i = 1; i < terms.length; i++) {
//                localTermFrequency.merge(terms[i], 1, Integer::sum);
//            }
//
//            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
//            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;
//
//            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
//                String term = entry.getKey();
//                int termFreqInDocument = entry.getValue();
//                int docFreq = documentFrequency.get(term);
//
//                // Use normalized TF that's more suitable for weighted Jaccard
//                double tf = 0.1 + 0.9 * ((double) termFreqInDocument /
//                        Collections.max(localTermFrequency.values()));
//
//                // Use smoother IDF that doesn't penalize common terms too heavily
//                double idf = 1.0 + Math.log((double) totalDocumentCount / docFreq);
//
//                // Calculate base TF-IDF weight
//                double tfidfWeight = tf * idf;
//
//                // Apply additional boosting for frequent terms in document
//                double frequencyBoost = 1.0 + Math.log(1.0 + termFreqInDocument);
//                tfidfWeight *= frequencyBoost;
//
//                // Apply smoothing factor if specified
//                if (smoothingFactor > 0) {
//                    int termFreqGlobal = globalTermFrequency.get(term);
//                    double globalWeight = (double) termFreqGlobal / (totalDocumentCount * documentLength);
//                    tfidfWeight = (1 - smoothingFactor) * tfidfWeight + smoothingFactor * globalWeight;
//                }
//
//                int termId = Integer.parseInt(term);
//                documentWeights.add(new WeightEntry(termId, tfidfWeight));
//
//                if (debugInfo != null) {
//                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
//                }
//            }
//
//            if (debugInfo != null && debugInfo.length() > 0) {
//                debugInfo.setLength(debugInfo.length() - 1);
//                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
//            }
//
//            // Use square root normalization to preserve relative differences while boosting smaller values
//            if (!documentWeights.isEmpty()) {
//                double maxWeight = documentWeights.stream().mapToDouble(w -> w.weight).max().orElse(1.0);
//
//                for (WeightEntry weightEntry : documentWeights) {
//                    // Square root normalization followed by scaling to [0.1, 1.0]
//                    double normalizedWeight = Math.sqrt(weightEntry.weight / maxWeight);
//                    weightEntry.weight = 0.1 + 0.9 * normalizedWeight;
//                }
//            }
//
//            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
//        }
//
//        long stopTime = System.currentTimeMillis();
//        long endMem = StatisticsLogic.getMemUsed();
//        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
//        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
//        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);
//
//        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
//        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
//        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
//    }

    @Deprecated
    public static void ComputeTF_IDFWeights(String wordsFile, IStore weightList, double smoothingFactor, double datasetUsagePercentage) {
//        ComputeTF_IDFWeights(wordsFile, weightList, smoothingFactor, datasetUsagePercentage,
//                SamplingStrategy.SamplingMethod.SYSTEMATIC, 42);
        ComputeTF_IDFWeights(wordsFile, weightList, smoothingFactor, datasetUsagePercentage,
                SamplingStrategy.SamplingMethod.RANDOMIZED, 42L, 0);
//        ComputeTF_IDFWeights(wordsFile, weightList, smoothingFactor, datasetUsagePercentage,
//                SamplingStrategy.SamplingMethod.CONTIGUOUS, 999999999L, 0);
    }

    /**
     * Generates TF-IDF weights from an input file with configurable sampling strategy.
     * Supports both systematic sampling (default, sequential) and randomized sampling.
     * Both methods can load the same lines when using the same seed, ensuring index consistency.
     *
     * @param wordsFile path to input file containing document terms in CSV format (docID,term1,term2,...)
     * @param weightList storage interface for persisting computed weights
     * @param smoothingFactor value between 0 and 1 that balances local and global term frequencies
     * @param datasetUsagePercentage percentage of dataset to use (0.0 to 1.0)
     * @param samplingMethod the sampling strategy (SYSTEMATIC, RANDOMIZED, or CONTIGUOUS)
     * @param samplingRandomSeed the random seed for RANDOMIZED sampling (ignored for SYSTEMATIC and CONTIGUOUS)
     */
    public static void ComputeTF_IDFWeights(String wordsFile, IStore weightList, double smoothingFactor, 
                                           double datasetUsagePercentage, SamplingStrategy.SamplingMethod samplingMethod, 
                                           long samplingRandomSeed) {
        ComputeTF_IDFWeights(wordsFile, weightList, smoothingFactor, datasetUsagePercentage, samplingMethod, samplingRandomSeed, 0);
    }

    /**
     * Generates TF-IDF weights with full sampling configuration.
     *
     * @param wordsFile path to input file containing document terms in CSV format (docID,term1,term2,...)
     * @param weightList storage interface for persisting computed weights
     * @param smoothingFactor value between 0 and 1 that balances local and global term frequencies
     * @param datasetUsagePercentage percentage of dataset to use (0.0 to 1.0)
     * @param samplingMethod the sampling strategy (SYSTEMATIC, RANDOMIZED, CONTIGUOUS)
     * @param samplingRandomSeed the random seed for RANDOMIZED sampling (ignored otherwise)
     * @param samplingStartLine zero-based requested start line for CONTIGUOUS sampling
     */
    public static void ComputeTF_IDFWeights(String wordsFile, IStore weightList, double smoothingFactor,
                                           double datasetUsagePercentage, SamplingStrategy.SamplingMethod samplingMethod,
                                           long samplingRandomSeed, int samplingStartLine) {
        // Store document lines and term frequencies
        List<String[]> documentLines = new ArrayList<>();
        Map<String, Integer> globalTermFrequency = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        // DF: number of documents containing each term
        Set<Integer> ids = new HashSet<>();

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        int totalDocumentCount = 0;
        int targetLineCount = Integer.MAX_VALUE;
        int totalLines = Integer.MAX_VALUE; // Scoped outside so it's accessible to the second pass

        // First pass: count total lines to calculate target
        if (datasetUsagePercentage < 1.0) {
            try (LineNumberReader countReader = new LineNumberReader(new FileReader(wordsFile))) {
                while (countReader.readLine() != null) {
                    // Just count lines
                }
                totalLines = countReader.getLineNumber(); // Updated to modify outer scope variable
                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
                logger.info("Using {}% of dataset: {} out of {} lines",
                        datasetUsagePercentage * 100, targetLineCount, totalLines);
                logger.info("Sampling method: {} {}", samplingMethod,
                        samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED ? 
                        "(seed=" + samplingRandomSeed + ")" : "");
            } catch (Exception e) {
                logger.error("Error counting lines in file: {}", e.getMessage(), e);
                return;
            }
        }

        // Create appropriate sampler based on configured method
        SamplingStrategy samplingStrategy = null;
        SystematicSampler systematicSampler = null;
        ContiguousWindowSampler contiguousWindowSampler = null;

        if (datasetUsagePercentage < 1.0) {
            if (samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED) {
                samplingStrategy = SamplingStrategy.randomized(samplingRandomSeed);
            } else if (samplingMethod == SamplingStrategy.SamplingMethod.CONTIGUOUS) {
                contiguousWindowSampler = new ContiguousWindowSampler(totalLines, targetLineCount, samplingStartLine);
                logger.info("Contiguous sampling window: requestedStart={}, effectiveStart={}, endExclusive={}",
                        samplingStartLine,
                        contiguousWindowSampler.getStartLineInclusive(),
                        contiguousWindowSampler.getEndLineExclusive());
            } else {
                systematicSampler = new SystematicSampler();
            }
        }

        // Second pass: read and sample across the entire file
        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
            String line;

            // FIX: Removed "linesRead < targetLineCount" to process the entire file structure
            while ((line = lr.readLine()) != null) {

                // Apply sampling based on configured method
                if (datasetUsagePercentage < 1.0) {
                    boolean shouldSelect;
                    if (samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED) {
                        shouldSelect = samplingStrategy.shouldSelectRandomized(totalLines, targetLineCount);
                    } else if (samplingMethod == SamplingStrategy.SamplingMethod.CONTIGUOUS) {
                        shouldSelect = contiguousWindowSampler.shouldSelect();
                    } else {
                        shouldSelect = systematicSampler.shouldSelect(totalLines, targetLineCount);
                    }
                    if (!shouldSelect) {
                        continue; // Skip this line
                    }
                }

                String[] terms = line.split(",");
                int id = Integer.parseInt(terms[0]);
                if (!ids.add(id)) {
                    continue; // Skip duplicate IDs
                }
                documentLines.add(terms);
                totalDocumentCount++;

                // Track unique terms in this document for DF calculation
                Set<String> uniqueTermsInDoc = new HashSet<>();
                for (int i = 1; i < terms.length; i++) {
                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
                    uniqueTermsInDoc.add(terms[i]);
                }

                // Update document frequency for each unique term in this document
                for (String term : uniqueTermsInDoc) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            logger.error("Error while reading weight file: {}", e.getMessage(), e);
            return;
        }

        // Calculate TF-IDF weights for each document
        for (String[] terms : documentLines) {
            String documentId = terms[0];
            int documentLength = terms.length - 1;
            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", documentId);
                continue;
            }

            Map<String, Integer> localTermFrequency = new HashMap<>();
            for (int i = 1; i < terms.length; i++) {
                localTermFrequency.merge(terms[i], 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;

            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
                String term = entry.getKey();
                int termFreqInDocument = entry.getValue();
                int docFreq = documentFrequency.get(term);

                // Use normalized TF that's more suitable for weighted Jaccard
                double tf = 0.1 + 0.9 * ((double) termFreqInDocument /
                        Collections.max(localTermFrequency.values()));

                // Use smoother IDF that doesn't penalize common terms too heavily
                double idf = 1.0 + Math.log((double) totalDocumentCount / docFreq);

                // Calculate base TF-IDF weight
                double tfidfWeight = tf * idf;

                // Apply additional boosting for frequent terms in document
                double frequencyBoost = 1.0 + Math.log(1.0 + termFreqInDocument);
                tfidfWeight *= frequencyBoost;

                // Apply smoothing factor if specified
                if (smoothingFactor > 0) {
                    int termFreqGlobal = globalTermFrequency.get(term);
                    double globalWeight = (double) termFreqGlobal / (totalDocumentCount * documentLength);
                    tfidfWeight = (1 - smoothingFactor) * tfidfWeight + smoothingFactor * globalWeight;
                }

                int termId = Integer.parseInt(term);
                documentWeights.add(new WeightEntry(termId, tfidfWeight));

                if (debugInfo != null) {
                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
                }
            }

            if (debugInfo != null && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1);
                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
            }

            // Use square root normalization to preserve relative differences while boosting smaller values
            if (!documentWeights.isEmpty()) {
                double maxWeight = documentWeights.stream().mapToDouble(w -> w.weight).max().orElse(1.0);

                for (WeightEntry weightEntry : documentWeights) {
                    // Square root normalization followed by scaling to [0.1, 1.0]
                    double normalizedWeight = Math.sqrt(weightEntry.weight / maxWeight);
                    weightEntry.weight = 0.1 + 0.9 * normalizedWeight;
                }
            }

            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
        }

        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }


//    public static void ComputeTF_IDFWeights2(String wordsFile, IStore weightList, double smoothingFactor) {
//        // Store document lines and term frequencies
//        List<String[]> documentLines = new ArrayList<>();
//        Map<String, Integer> globalTermFrequency = new HashMap<>();
//        Map<String, Integer> documentFrequency = new HashMap<>(); // DF: number of documents containing each term
//        Set<Integer> ids = new HashSet<>();
//
//        long initMem = StatisticsLogic.getClearedMem();
//        long startTime = System.currentTimeMillis();
//
//        int totalDocumentCount = 0;
//
//        try (LineNumberReader lr = new LineNumberReader(new FileReader(wordsFile))) {
//            String line;
//            while ((line = lr.readLine()) != null) {
//                String[] terms = line.split(",");
//                int id = Integer.parseInt(terms[0]);
//                if (!ids.add(id)) {
//                    continue; // Skip duplicate IDs
//                }
//                documentLines.add(terms);
//                totalDocumentCount++;
//
//                // Track unique terms in this document for DF calculation
//                Set<String> uniqueTermsInDoc = new HashSet<>();
//                for (int i = 1; i < terms.length; i++) {
//                    globalTermFrequency.merge(terms[i], 1, Integer::sum);
//                    uniqueTermsInDoc.add(terms[i]);
//                }
//
//                // Update document frequency for each unique term in this document
//                for (String term : uniqueTermsInDoc) {
//                    documentFrequency.merge(term, 1, Integer::sum);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error while reading weight file: {}", e.getMessage(), e);
//            return;
//        }
//
//        // Calculate TF-IDF weights for each document
//        for (String[] terms : documentLines) {
//            String documentId = terms[0];
//            int documentLength = terms.length - 1;
//            if (documentLength == 0) {
//                logger.warn("Document with ID {} is empty, skipping.", documentId);
//                continue;
//            }
//
//            Map<String, Integer> localTermFrequency = new HashMap<>();
//            for (int i = 1; i < terms.length; i++) {
//                localTermFrequency.merge(terms[i], 1, Integer::sum);
//            }
//
//            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
//            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;
//
//            for (Map.Entry<String, Integer> entry : localTermFrequency.entrySet()) {
//                String term = entry.getKey();
//                int termFreqInDocument = entry.getValue();
//                int docFreq = documentFrequency.get(term);
//
//                // Calculate TF (Term Frequency)
//                double tf = (double) termFreqInDocument / documentLength;
//
//                // Calculate IDF (Inverse Document Frequency)
//                double idf = Math.log((double) totalDocumentCount / docFreq);
//
//                // Calculate TF-IDF weight
//                double tfidfWeight = tf * idf;
//
//                // Apply smoothing factor to blend with original smoothing approach if needed
//                if (smoothingFactor > 0) {
//                    int termFreqGlobal = globalTermFrequency.get(term);
//                    double smoothedWeight = (1 - smoothingFactor) * tf
//                            + smoothingFactor * ((double) termFreqGlobal / (totalDocumentCount * documentLength));
//                    tfidfWeight = tfidfWeight * (1 - smoothingFactor) + smoothedWeight * smoothingFactor;
//                }
//
//                int termId = Integer.parseInt(term);
//                documentWeights.add(new WeightEntry(termId, tfidfWeight));
//
//                if (debugInfo != null) {
//                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
//                }
//            }
//
//            if (debugInfo != null && debugInfo.length() > 0) {
//                debugInfo.setLength(debugInfo.length() - 1);
//                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
//            }
//
//            weightList.write(new Weight(Integer.parseInt(documentId), new ArrayList<>(documentWeights)));
//        }
//
//        long stopTime = System.currentTimeMillis();
//        long endMem = StatisticsLogic.getMemUsed();
//        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
//        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
//        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);
//
//        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
//        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
//        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
//    }


//    public static void ComputeTF_IDFWeights(HashMap<Integer, ArrayList<Integer>> textualObjects, IStore weightList, double smoothingFactor) {
//        Map<Integer, Integer> globalTermFrequency = new HashMap<>();
//        Map<Integer, Integer> documentFrequency = new HashMap<>();
//        int totalDocumentCount = textualObjects.size();
//
//        // First pass: calculate global term frequency and document frequency
//        for (Map.Entry<Integer, ArrayList<Integer>> entry : textualObjects.entrySet()) {
//            ArrayList<Integer> termsInDoc = entry.getValue();
//            Set<Integer> uniqueTermsInDoc = new HashSet<>(termsInDoc);
//            for (Integer term : termsInDoc) {
//                globalTermFrequency.merge(term, 1, Integer::sum);
//            }
//            for (Integer term : uniqueTermsInDoc) {
//                documentFrequency.merge(term, 1, Integer::sum);
//            }
//        }
//
//        long initMem = StatisticsLogic.getClearedMem();
//        long startTime = System.currentTimeMillis();
//
//        // Calculate TF-IDF weights for each document
//        for (Map.Entry<Integer, ArrayList<Integer>> entry : textualObjects.entrySet()) {
//            Integer documentId = entry.getKey();
//            ArrayList<Integer> termsInDoc = entry.getValue();
//            int documentLength = termsInDoc.size();
//            if (documentLength == 0) {
//                logger.warn("Document with ID {} is empty, skipping.", documentId);
//                continue;
//            }
//
//            Map<Integer, Integer> localTermFrequency = new HashMap<>();
//            for (Integer term : termsInDoc) {
//                localTermFrequency.merge(term, 1, Integer::sum);
//            }
//
//            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
//            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;
//
//            for (Map.Entry<Integer, Integer> termEntry : localTermFrequency.entrySet()) {
//                Integer term = termEntry.getKey();
//                int termFreqInDocument = termEntry.getValue();
//                int docFreq = documentFrequency.get(term);
//
//                // Calculate TF (Term Frequency)
//                double tf = (double) termFreqInDocument / documentLength;
//
//                // Calculate IDF (Inverse Document Frequency)
//                double idf = Math.log((double) totalDocumentCount / docFreq);
//
//                //double idf = Math.log((totalDocumentCount + 1.0) / (docFreq + 1.0)) + 1.0;
//
//                // Calculate TF-IDF weight
//                double tfidfWeight = tf * idf;
//
//                // Apply smoothing factor to blend with original smoothing approach if needed
//                if (smoothingFactor > 0) {
//                    int termFreqGlobal = globalTermFrequency.get(term);
//                    double smoothedWeight = (1 - smoothingFactor) * tf
//                            + smoothingFactor * ((double) termFreqGlobal / (totalDocumentCount * documentLength));
//                    tfidfWeight = tfidfWeight * (1 - smoothingFactor) + smoothedWeight * smoothingFactor;
//                }
//
//                documentWeights.add(new WeightEntry(term, tfidfWeight));
//
//                if (debugInfo != null) {
//                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
//                }
//            }
//
//            if (debugInfo != null && debugInfo.length() > 0) {
//                debugInfo.setLength(debugInfo.length() - 1);
//                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
//            }
//
//            // TODO TESTING: Normalize weights to ensure they are between 0 and 1
//            // After computing TF-IDF weights for each document
//            double maxWeight = documentWeights.stream().mapToDouble(w -> w.weight).max().orElse(1.0);
//            for (WeightEntry weightEntry : documentWeights) {
//                weightEntry.weight /= maxWeight; // Normalize so max weight is 1.0
//            }
//
//            weightList.write(new Weight(documentId, new ArrayList<>(documentWeights)));
//        }
//
//        long stopTime = System.currentTimeMillis();
//        long endMem = StatisticsLogic.getMemUsed();
//        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
//        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
//        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);
//
//        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
//        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
//        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
//    }

    public static void ComputeTF_IDFWeights(HashMap<Integer, ArrayList<Integer>> textualObjects, IStore weightList, double smoothingFactor) {
        Map<Integer, Integer> globalTermFrequency = new HashMap<>();
        Map<Integer, Integer> documentFrequency = new HashMap<>();
        int totalDocumentCount = textualObjects.size();

        // First pass: calculate global term frequency and document frequency
        for (Map.Entry<Integer, ArrayList<Integer>> entry : textualObjects.entrySet()) {
            ArrayList<Integer> termsInDoc = entry.getValue();
            Set<Integer> uniqueTermsInDoc = new HashSet<>(termsInDoc);
            for (Integer term : termsInDoc) {
                globalTermFrequency.merge(term, 1, Integer::sum);
            }
            for (Integer term : uniqueTermsInDoc) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();

        // Calculate TF-IDF weights for each document
        for (Map.Entry<Integer, ArrayList<Integer>> entry : textualObjects.entrySet()) {
            Integer documentId = entry.getKey();
            ArrayList<Integer> termsInDoc = entry.getValue();
            int documentLength = termsInDoc.size();
            if (documentLength == 0) {
                logger.warn("Document with ID {} is empty, skipping.", documentId);
                continue;
            }

            Map<Integer, Integer> localTermFrequency = new HashMap<>();
            for (Integer term : termsInDoc) {
                localTermFrequency.merge(term, 1, Integer::sum);
            }

            List<WeightEntry> documentWeights = new ArrayList<>(localTermFrequency.size());
            StringBuilder debugInfo = logger.isDebugEnabled() ? new StringBuilder() : null;

            for (Map.Entry<Integer, Integer> termEntry : localTermFrequency.entrySet()) {
                Integer term = termEntry.getKey();
                int termFreqInDocument = termEntry.getValue();
                int docFreq = documentFrequency.get(term);

                // Use normalized TF that's more suitable for weighted Jaccard
                double tf = 0.1 + 0.9 * ((double) termFreqInDocument /
                    Collections.max(localTermFrequency.values()));

                // Use smoother IDF that doesn't penalize common terms too heavily
                double idf = 1.0 + Math.log((double) totalDocumentCount / docFreq);

                // Calculate base TF-IDF weight
                double tfidfWeight = tf * idf;

                // Apply additional boosting for frequent terms in document
                double frequencyBoost = 1.0 + Math.log(1.0 + termFreqInDocument);
                tfidfWeight *= frequencyBoost;

                // Apply smoothing factor if specified
                if (smoothingFactor > 0) {
                    int termFreqGlobal = globalTermFrequency.get(term);
                    double globalWeight = (double) termFreqGlobal / (totalDocumentCount * documentLength);
                    tfidfWeight = (1 - smoothingFactor) * tfidfWeight + smoothingFactor * globalWeight;
                }

                documentWeights.add(new WeightEntry(term, tfidfWeight));

                if (debugInfo != null) {
                    debugInfo.append(term).append(" ").append(tfidfWeight).append(",");
                }
            }

            if (debugInfo != null && debugInfo.length() > 0) {
                debugInfo.setLength(debugInfo.length() - 1);
                logger.debug("WordID: {}, TF-IDF Weights: {}", documentId, debugInfo);
            }

            // Use square root normalization to preserve relative differences while boosting smaller values
            if (!documentWeights.isEmpty()) {
                double maxWeight = documentWeights.stream().mapToDouble(w -> w.weight).max().orElse(1.0);

                for (WeightEntry weightEntry : documentWeights) {
                    // Square root normalization followed by scaling to [0.1, 1.0]
                    double normalizedWeight = Math.sqrt(weightEntry.weight / maxWeight);
                    weightEntry.weight = 0.1 + 0.9 * normalizedWeight;
                }
            }

            weightList.write(new Weight(documentId, new ArrayList<>(documentWeights)));
        }

        long stopTime = System.currentTimeMillis();
        long endMem = StatisticsLogic.getMemUsed();
        StatisticsLogic.weightIndexPeakMemUsed = (endMem - initMem);
        StatisticsLogic.weightIndexMemUsed = (StatisticsLogic.getClearedMem() - initMem);
        StatisticsLogic.weightIndexBuildTime = (stopTime - startTime);

        logger.info("TF-IDF weight processing done in: {} ms", StatisticsLogic.weightIndexBuildTime);
        logger.info("TF-IDF weights peak memory usage: {} Megabytes", (StatisticsLogic.weightIndexPeakMemUsed / 1024) / 1024);
        logger.info("TF-IDF weights clean memory usage: {} Megabytes", (StatisticsLogic.weightIndexMemUsed / 1024) / 1024);
    }

}
