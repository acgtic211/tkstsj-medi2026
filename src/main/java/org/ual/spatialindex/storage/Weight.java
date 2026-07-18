package org.ual.spatialindex.storage;

import java.io.Serializable;
import java.util.ArrayList;

public class Weight implements Serializable {
    public int wordId;
    public ArrayList<WeightEntry> weights;

    public Weight(int word, ArrayList<WeightEntry> weightList) {
        wordId = word;
        weights = weightList;
    }

    public int getWordId() {
        return wordId;
    }

    public ArrayList<WeightEntry> getWeights() {
        return weights;
    }

    @Override
    public String toString() {
        return "Weight{" +
                "wordId=" + wordId +
                ", weights=" + weights +
                '}';
    }

}
