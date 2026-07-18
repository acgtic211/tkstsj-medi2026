package org.ual.documentindex.invertedlist;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostingListEntryTest {

    @Test
    void constructorWithoutClusterShouldSetDocumentAndWeight() {
        PostingListEntry entry = new PostingListEntry(11, 0.75);

        assertEquals(11, entry.documentId);
        assertEquals(0.75, entry.weight, 1e-9);
        assertEquals(0, entry.clusterId);
    }

    @Test
    void constructorWithClusterShouldSetAllFields() {
        PostingListEntry entry = new PostingListEntry(11, 0.75, 2);

        assertEquals(11, entry.documentId);
        assertEquals(0.75, entry.weight, 1e-9);
        assertEquals(2, entry.clusterId);
    }

    @Test
    void shouldRoundTripWithJavaSerialization() throws IOException, ClassNotFoundException {
        PostingListEntry original = new PostingListEntry(42, 0.33, 1);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(original);
        }

        PostingListEntry restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (PostingListEntry) in.readObject();
        }

        assertEquals(original.documentId, restored.documentId);
        assertEquals(original.weight, restored.weight, 1e-9);
        assertEquals(original.clusterId, restored.clusterId);
    }
}

