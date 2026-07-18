package org.ual.spatialindex.spatialindex;

import java.util.HashMap;

public class RtreeEntry implements IEntry {

    int id;
    public boolean isLeafEntry;

    public int treeId;
    public int wordHit;

    public HashMap<Integer, Integer> distMap;

    IShape mbr;

    public RtreeEntry(int id, boolean isLeafEntry) {
        this.id = id;
        this.isLeafEntry = isLeafEntry;
        distMap = new HashMap<>();
    }

    public RtreeEntry(int id, Region mbr, boolean isLeafEntry) {
        this.id = id;
        this.isLeafEntry = isLeafEntry;
        this.mbr = new Region(mbr);
        distMap = new HashMap<>();
    }

    public int getIdentifier() {
        return id;
    }

    public IShape getShape() {
        return mbr;
    }

    public void setIdentifier(int id) {
        this.id = id;
    }

    public static boolean checkOverlap(double[] min, double[] max, RtreeEntry e) {
        Region region = e.getShape().getMBR();
        for (int i = 0; i < min.length; i++) {
            if (min[i] > region.getHigh(i) || max[i] < region.getLow(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RtreeEntry [id=" + id + ", isLeafEntry=" + isLeafEntry + ", treeid=" + treeId + ", wordhit=" + wordHit
                + ", distmap=" + distMap + ", mbr=" + mbr + "]";
    }
}
