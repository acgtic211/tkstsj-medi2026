package org.ual.spatialindex.parameters;

import org.ual.spatialindex.spatialindex.Point;

public class ParametersFactory {
    public static DatasetParameters getParameters(Dataset dataset) {
        switch (dataset) {
            case PAPER_SET:
                return new DatasetParameters("src/main/resources/data/paper_keywords.txt",
                        "src/main/resources/data/paper_locations.txt",
                        0, 30,
                        0, 27,
                        5,
                        new int[]{2, 1, 3, 5, 4},
                        new Point[]{
                                new Point(new double[]{0,675000, 0,750000}),
                                new Point(new double[]{7,425000, 9,750000}),
                                new Point(new double[]{7,425000, 20,250000}),
                                new Point(new double[]{16,875000, 26,250000}),
                                new Point(new double[]{22,275000, 15,750000}),
                                new Point(new double[]{26,325000, 3,750000}),
                                new Point(new double[]{26,325000, 29,250000})
                        });
            case TESTING_SET:
                return new DatasetParameters("src/main/resources/data/key_test.txt",
                        "src/main/resources/data/loc_test.txt",
                        -5, 5,
                        -4, 8,
                        7,
                        new int[]{5, 2, 1, 3, 4, 6, 10},
                        new Point[]{
                                new Point(new double[]{-3.7, -4.75}),
                                new Point(new double[]{-3.7, 1.25}),
                                new Point(new double[]{-3.1, 0.25}),
                                new Point(new double[]{-3.1, 3.25}),
                                new Point(new double[]{-0.7, -2.75}),
                                new Point(new double[]{2.3, -0.75}),
                                new Point(new double[]{2.3, 2.25}),
                                new Point(new double[]{5.3, -1.75}),
                                new Point(new double[]{5.3, 4.75}),
                                new Point(new double[]{7.7, 0.25})
                        });
            case ORIGINAL_SET:
                return new DatasetParameters("src/main/resources/data/keywords.txt",
                        "src/main/resources/data/locations.txt",
                        -5.0, 5,
                        -4.0, 5.0,
                        6,
                        new int[]{5, 1, 2, 4, 3, 6},
                        new Point[]{
                                new Point(new double[]{-3.775, -4.75}),
                                new Point(new double[]{-3.775, 1.25}),
                                new Point(new double[]{-2.875, 0.25}),
                                new Point(new double[]{-2.875, 3.25}),
                                new Point(new double[]{2.075, -0.75}),
                                new Point(new double[]{2.075, 2.25}),
                                new Point(new double[]{4.775, -1.75}),
                                new Point(new double[]{4.775, 4.75})
                        });
            case HOTEL_SET:
                return new DatasetParameters("src/main/resources/data/hotel_doc",
                        "src/main/resources/data/hotel_loc_fix",
                        19, 70,
                        -159, -68,
                        600,
                        new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                        new Point[]{
                                new Point(new double[]{-75.071658, 41.182834}),
                                new Point(new double[]{-116.232766, 33.504148}),
                                new Point(new double[]{-120.806223, 38.623272}),
                                new Point(new double[]{-84.218571, 33.504148}),
                                new Point(new double[]{-79.645114, 36.063710}),
                                new Point(new double[]{-88.792027, 41.182834}),
                                new Point(new double[]{-84.218571, 41.182834}),
                                new Point(new double[]{-84.218571, 38.623272}),
                                new Point(new double[]{-75.071658, 38.623272}),
                                new Point(new double[]{-84.218571, 36.063710})
                        });
            case POSTAL_CODES_SET:
                return new DatasetParameters("src/main/resources/data/postal_doc.txt",
                        "src/main/resources/data/postal_loc.txt",
                        -55, 74,
                        -176, 180,
                        549405,
                        new int[]{1, 8, 6, 872, 892, 890, 878, 882, 881, 898},
                        new Point[]{
                                new Point(new double[]{10.338534, 50.886150}),
                                new Point(new double[]{10.338534, 44.449354}),
                                new Point(new double[]{28.142196, 50.886150}),
                                new Point(new double[]{-7.465127, 44.449354}),
                                new Point(new double[]{-78.679772, 38.012557}),
                                new Point(new double[]{-7.465127, 50.886150}),
                                new Point(new double[]{-78.679772, 44.449354}),
                                new Point(new double[]{-96.483433, 38.012557}),
                                new Point(new double[]{-7.465127, 38.012557}),
                                new Point(new double[]{-96.483433, 44.449354})
                        });
            case SPORTS_SET:
                return new DatasetParameters("src/main/resources/data/sports_doc.txt",
                        "src/main/resources/data/sports_loc.txt",
                        -90, 79,
                        -180, 180,
                        452950,
                        new int[]{82, 138, 1, 164, 202, 118, 3462, 203, 17, 907},
                        new Point[]{
                                new Point(new double[]{8.933659, 49.147281}),
                                new Point(new double[]{8.933659, 40.716061}),
                                new Point(new double[]{-80.973918, 40.716061}),
                                new Point(new double[]{-9.047856, 49.147281}),
                                new Point(new double[]{-9.047856, 40.716061}),
                                new Point(new double[]{8.933659, 57.578500}),
                                new Point(new double[]{26.915175, 49.147281}),
                                new Point(new double[]{-98.955434, 40.716061}),
                                new Point(new double[]{-116.936950, 32.284842}),
                                new Point(new double[]{26.915175, 57.578500})
                        });
            case PARKS_SET:
                return new DatasetParameters("src/main/resources/data/parks_doc.txt",
                        "src/main/resources/data/parks_loc.txt",
                        -90, 81,
                        -180, 180,
                        1002722,
                        new int[]{15, 3, 4, 384, 39, 156, 0, 1, 25, 1162},
                        new Point[]{
                                new Point(new double[]{8.910315, 51.080628}),
                                new Point(new double[]{8.910315, 42.532236}),
                                new Point(new double[]{26.870788, 51.080628}),
                                new Point(new double[]{-9.050157, 51.080628}),
                                new Point(new double[]{44.831260, 51.080628}),
                                new Point(new double[]{-80.892046, 42.532236}),
                                new Point(new double[]{26.870788, 59.629020}),
                                new Point(new double[]{8.910315, 59.629020}),
                                new Point(new double[]{26.870788, 42.532236}),
                                new Point(new double[]{44.831260, 59.629020})
                        });
            default:
                throw new IllegalArgumentException("Unknown dataset: " + dataset);
        }
    }
}
