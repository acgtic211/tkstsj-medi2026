package org.ual.spatialindex.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class HashMapDocumentStore extends AbstractDocumentStore implements IStore{
    private HashMap<Integer, Weight> doc;
    //public static int maxWord;

    public HashMapDocumentStore() {
        this.doc = new HashMap<>();
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

    public Iterator<Weight> iterator() {
        return doc.values().iterator();
    }


}
