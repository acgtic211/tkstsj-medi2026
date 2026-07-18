package org.ual.spatialindex.spatialindex;

public class SpatialIndex {
    public static final String EMAIL = "marioh@cs.ucr.edu";
    public static final String VERSION = "0.44.2b";
    public static final String DATE = "27 July 2003";

    public static final double EPSILON = 1.192092896e-07;

    public static final int RtreeVariantQuadratic = 1;
    public static final int RtreeVariantLinear = 2;
    public static final int RtreeVariantRstar = 3;

    public static final int PersistentIndex = 1;
    public static final int PersistentLeaf = 2;

    public static final int ContainmentQuery = 1;
    public static final int IntersectionQuery = 2;


    public static String getTreeVariantString(int variant) {
        switch (variant) {
            case RtreeVariantQuadratic:
                return "RTreeVariantQuadratic";
            case RtreeVariantLinear:
                return "RTreeVariantLinear";
            case RtreeVariantRstar:
                return "RTreeVariantRstar";
            default:
                return "Unknown variant";
        }
    }
}
