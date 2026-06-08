package uk.nhs.adaptors.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

public class FileUtilTest {

    @Test
    public void shouldReadTextFromClasspathResource() {
        var content = FileUtil.readResourceAsString("/file-util/sample.txt");

        assertEquals("sample-content\n", content);
    }

    @Test
    public void shouldThrowWhenResourceDoesNotExist() {
        assertThrows(FileNotFoundException.class, () -> FileUtil.readResourceAsString("/file-util/missing.txt"));
    }
}

