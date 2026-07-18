package org.ual.spatialindex.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

public class TreeMapDocumentStore extends AbstractDocumentStore implements IStore {
   private TreeMap<Integer, Weight> doc;

    public TreeMapDocumentStore() {
        this.doc = new TreeMap<>();
    }

    @Override
    public Weight read(int wordId) {
        return doc.get(wordId);
    }

    @Override
    public void write(Weight weight) {
        doc.put(weight.wordId, weight);
    }

    @Override
    public Iterator<Weight> iterator() {
        return doc.values().iterator();
    }

    @Override
    public HashSet<Integer> readSet(int id) {
        ArrayList<WeightEntry> words = doc.get(id).weights;
        if(words == null)
            return null;

        HashSet<Integer> set = new HashSet<>();
        for(int i = 0; i < words.size(); i++){
            WeightEntry de = words.get(i);
            if(de.word <= maxWord)
                set.add(de.word);
        }

        return set;
    }

    @Override
    public int getSize() {
        return doc.size();
    }
}
