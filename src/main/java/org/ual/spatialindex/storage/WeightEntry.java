package org.ual.spatialindex.storage;

import java.io.Serializable;

public class WeightEntry implements Serializable {
    public int word;
    public double weight;

    public WeightEntry(int id, double w){
        word = id;
        weight = w;
    }

    public int getWord() {
        return word;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return "WeightEntry{" +
                "word=" + word +
                ", weight=" + weight +
                '}';
    }
}
