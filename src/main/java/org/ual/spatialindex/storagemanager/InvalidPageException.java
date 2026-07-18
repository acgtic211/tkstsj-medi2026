package org.ual.spatialindex.storagemanager;

public class InvalidPageException extends RuntimeException {
    public InvalidPageException(int id)
    {
        super("" + id);
    }
}
