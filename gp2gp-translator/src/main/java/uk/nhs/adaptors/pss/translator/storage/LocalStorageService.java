package uk.nhs.adaptors.pss.translator.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalStorageService implements StorageService {

    // Configuration singleton parameters
    private final Map<String, byte[]> storage;

    public LocalStorageService() {
        storage = new ConcurrentHashMap<>();
    }

    public void uploadFile(String filename, byte[] fileAsString) throws StorageException {
        try {
            storage.put(filename, fileAsString);
        } catch (Exception e) {
            throw new StorageException("Error occurred uploading to Local Storage", e);
        }
    }

    public byte[] downloadFile(String filename) throws StorageException {
        if (!storage.containsKey(filename)) {
            throw new StorageException(String.format("Attempting to download file \"%s\" but does not exist.", filename), null);
        }
        return storage.get(filename);
    }

    public void deleteFile(String filename) {
        try {
            storage.remove(filename);
        } catch (Exception e) {
            throw new StorageException("Error occurred deleting from Local Storage", e);
        }
    }

    public String getFileLocation(String filename) {
        return filename;
    }
}
