package org.ual.utils.analyze;

import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

public class LocationAnalyzer {
    static Dataset dataset = Dataset.PARKS_SET;

    public static void main(String[] args) throws IOException {
        DatasetParameters datasetParameters = ParametersFactory.getParameters(dataset);
        analyze(datasetParameters.locationFile);
    }

    private static void analyze(String locationsFilePath) throws IOException {
        LineNumberReader location_reader = new LineNumberReader(new FileReader(locationsFilePath));

        int id;
        double x1, y1;
        String line;
        String[] temp;

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLg = Double.POSITIVE_INFINITY;
        double maxLg = Double.NEGATIVE_INFINITY;

        List<Point> allPoints = new ArrayList<>();

        while ((line = location_reader.readLine()) != null) {
            temp = line.split(",");
            id = Integer.parseInt(temp[0]);
            x1 = Double.parseDouble(temp[1]);
            y1 = Double.parseDouble(temp[2]);

            allPoints.add(new Point(x1, y1));

            // OSM use Lt/Lg but datasets should be Lg/Lt
            maxLg = Math.max(x1, maxLg);
            minLg = Math.min(x1, minLg);

            maxLat = Math.max(y1, maxLat);
            minLat = Math.min(y1, minLat);


        }
        location_reader.close();

        System.out.println("Dataset: " + dataset.name());
        System.out.println("MaxLat: " + maxLat);
        System.out.println("MinLat: " + minLat);
        System.out.println("MaxLg: " + maxLg);
        System.out.println("MinLg: " + minLg);
        System.out.println("Total points: " + allPoints.size());

        // Find dense regions
        findDenseRegions(allPoints, minLat, maxLat, minLg, maxLg);
    }

    private static void findDenseRegions(List<Point> points, double minLat, double maxLat, double minLg, double maxLg) {
        int gridSize = 20; // 20x20 grid
        int[][] densityGrid = new int[gridSize][gridSize];

        double latStep = (maxLat - minLat) / gridSize;
        double lgStep = (maxLg - minLg) / gridSize;

        // Count points in each grid cell
        for (Point point : points) {
            int latIndex = (int) ((point.lat - minLat) / latStep);
            int lgIndex = (int) ((point.lng - minLg) / lgStep);

            latIndex = Math.max(0, Math.min(gridSize - 1, latIndex));
            lgIndex = Math.max(0, Math.min(gridSize - 1, lgIndex));

            densityGrid[lgIndex][latIndex]++;


//            int latIndex = Math.min(gridSize - 1, (int) ((point.lat - minLat) / latStep));
//            int lgIndex = Math.min(gridSize - 1, (int) ((point.lng - minLg) / lgStep));
//            densityGrid[latIndex][lgIndex]++;
        }

        // Find top 10 densest regions
        List<DenseRegion> denseRegions = new ArrayList<>();
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                if (densityGrid[i][j] > 0) {
                    double centerLng = minLg + (i + 0.5) * lgStep;
                    double centerLat = minLat + (j + 0.5) * latStep;
                    denseRegions.add(new DenseRegion(centerLng, centerLat, densityGrid[i][j]));
                }
            }
        }

        // Sort by density and get top 10
        denseRegions.sort((a, b) -> Integer.compare(b.density, a.density));
        int topRegions = Math.min(10, denseRegions.size());

        System.out.println("\nTop " + topRegions + " densest regions:");
        for (int i = 0; i < topRegions; i++) {
            DenseRegion region = denseRegions.get(i);
            System.out.printf("Region %d: lng=%.6f, lat=%.6f, density=%d points%n",
                             i + 1, region.lng, region.lat, region.density);
        }
    }

    // Helper classes
    private static class Point {
        double lat, lng;
        Point(double lng, double lat) { this.lat = lat; this.lng = lng; }
    }

    private static class DenseRegion {
        double lat, lng;
        int density;
        DenseRegion(double lng, double lat, int density) {
            this.lat = lat; this.lng = lng; this.density = density;
        }
    }
}
