package org.ual.utils.analyze;

import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.*;

public class DatasetValidator {
    public static void main(String[] args) throws IOException {
        DatasetParameters datasetParameters = ParametersFactory.getParameters(Dataset.PARKS_SET);
        validateDatasets(new String[]{datasetParameters.locationFile, datasetParameters.keywordFile});
        //repairDataset(datasetParameters);
    }

    /**
     * Validates that all dataset files have the same number of lines,
     * matching and unique ids.
     * @param filePaths array of file paths to validate
     * @throws IOException if file reading fails
     */
    public static void validateDatasets(String[] filePaths) throws IOException {
        List<List<Integer>> allIds = new ArrayList<>();
        int expectedLines = -1;

        for (String filePath : filePaths) {
            List<Integer> ids = new ArrayList<>();
            Set<Integer> idSet = new HashSet<>();
            try (LineNumberReader reader = new LineNumberReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] temp = line.split(",");
                    int id = Integer.parseInt(temp[0]);
                    if (!idSet.add(id)) {
                        throw new IllegalArgumentException("Duplicate id " + id + " in file: " + filePath);
                    }
                    ids.add(id);
                }
                if (expectedLines == -1) {
                    expectedLines = ids.size();
                } else if (ids.size() != expectedLines) {
                    throw new IllegalArgumentException("File " + filePath + " has a different number of lines.");
                }
            }
            allIds.add(ids);
        }

        List<Integer> reference = allIds.get(0);
        for (int i = 1; i < allIds.size(); i++) {
            if (!new HashSet<>(reference).equals(new HashSet<>(allIds.get(i)))) {
                throw new IllegalArgumentException("Ids do not match between files.");
            }
        }
        System.out.println("All files validated: same number of lines, matching and unique ids.");
    }

//    public static void repairDataset(DatasetParameters datasetParameters) throws IOException {
//        String spatialFile = datasetParameters.locationFile;
//        String keywordFile = datasetParameters.keywordFile;
//
//        List<String> spatialLines = new ArrayList<>();
//        List<String> keywordLines = new ArrayList<>();
//
//        try (LineNumberReader spatialReader = new LineNumberReader(new FileReader(spatialFile));
//             LineNumberReader keywordReader = new LineNumberReader(new FileReader(keywordFile))) {
//            String spatialLine, keywordLine;
//            while ((spatialLine = spatialReader.readLine()) != null &&
//                   (keywordLine = keywordReader.readLine()) != null) {
//                spatialLines.add(spatialLine);
//                keywordLines.add(keywordLine);
//            }
//            if (spatialReader.readLine() != null || keywordReader.readLine() != null) {
//                throw new IllegalArgumentException("Files have different number of lines.");
//            }
//        }
//
//        Map<Integer, Integer> idMap = new HashMap<>();
//        Set<Integer> seen = new HashSet<>();
//        int newId = 1;
//
//        for (int i = 0; i < spatialLines.size(); i++) {
//            int oldId = Integer.parseInt(spatialLines.get(i).split(",")[0]);
//            if (!seen.contains(oldId)) {
//                idMap.put(oldId, newId++);
//                seen.add(oldId);
//            } else {
//                idMap.put(oldId, newId++);
//            }
//        }
//
//        try (PrintWriter spatialWriter = new PrintWriter(spatialFile + ".fixed");
//             PrintWriter keywordWriter = new PrintWriter(keywordFile + ".fixed")) {
//            for (int i = 0; i < spatialLines.size(); i++) {
//                String[] spatialParts = spatialLines.get(i).split(",", 2);
//                String[] keywordParts = keywordLines.get(i).split(",", 2);
//                int oldId = Integer.parseInt(spatialParts[0]);
//                int fixedId = idMap.get(oldId);
//
//                spatialWriter.println(fixedId + (spatialParts.length > 1 ? "," + spatialParts[1] : ""));
//                keywordWriter.println(fixedId + (keywordParts.length > 1 ? "," + keywordParts[1] : ""));
//            }
//        }
//
//        System.out.println("Datasets repaired. Output: " + spatialFile + ".fixed and " + keywordFile + ".fixed");
//    }

    public static void repairDataset(DatasetParameters datasetParameters) throws IOException {
        String spatialFile = datasetParameters.locationFile;
        String keywordFile = datasetParameters.keywordFile;

        List<String> spatialLines = new ArrayList<>();
        List<String> keywordLines = new ArrayList<>();

        try (LineNumberReader spatialReader = new LineNumberReader(new FileReader(spatialFile));
             LineNumberReader keywordReader = new LineNumberReader(new FileReader(keywordFile))) {
            String spatialLine, keywordLine;
            while ((spatialLine = spatialReader.readLine()) != null &&
                   (keywordLine = keywordReader.readLine()) != null) {
                spatialLines.add(spatialLine);
                keywordLines.add(keywordLine);
            }
            if (spatialReader.readLine() != null || keywordReader.readLine() != null) {
                throw new IllegalArgumentException("Files have different number of lines.");
            }
        }

        // Count occurrences of each ID
        Map<Integer, Integer> idCount = new HashMap<>();
        for (String line : spatialLines) {
            int id = Integer.parseInt(line.split(",")[0]);
            idCount.put(id, idCount.getOrDefault(id, 0) + 1);
        }

        // Assign new IDs only to duplicates
        Map<Integer, Queue<Integer>> duplicateIdMap = new HashMap<>();
        int nextId = Collections.max(idCount.keySet()) + 1;
        for (Map.Entry<Integer, Integer> entry : idCount.entrySet()) {
            if (entry.getValue() > 1) {
                Queue<Integer> newIds = new LinkedList<>();
                for (int i = 0; i < entry.getValue() - 1; i++) {
                    newIds.add(nextId++);
                }
                duplicateIdMap.put(entry.getKey(), newIds);
            }
        }

        try (PrintWriter spatialWriter = new PrintWriter(spatialFile + ".fixed");
             PrintWriter keywordWriter = new PrintWriter(keywordFile + ".fixed")) {
            Map<Integer, Integer> seen = new HashMap<>();
            for (int i = 0; i < spatialLines.size(); i++) {
                String[] spatialParts = spatialLines.get(i).split(",", 2);
                String[] keywordParts = keywordLines.get(i).split(",", 2);
                int oldId = Integer.parseInt(spatialParts[0]);
                int newId = oldId;

                int count = seen.getOrDefault(oldId, 0);
                if (count > 0 && duplicateIdMap.containsKey(oldId)) {
                    newId = duplicateIdMap.get(oldId).poll();
                }
                seen.put(oldId, count + 1);

                spatialWriter.println(newId + (spatialParts.length > 1 ? "," + spatialParts[1] : ""));
                keywordWriter.println(newId + (keywordParts.length > 1 ? "," + keywordParts[1] : ""));
            }
        }

        System.out.println("Datasets repaired. Output: " + spatialFile + ".fixed and " + keywordFile + ".fixed");
    }

}
