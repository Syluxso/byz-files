package com.nyberg.files.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileServiceSanitizeNameTest {

    @Test
    void stripsPathSeparatorsAndUnsafeChars() {
        assertEquals("report.pdf", FileService.sanitizeName("report.pdf"));
        assertEquals("a_b_c.txt", FileService.sanitizeName("a/b\\c.txt"));
        // spaces kept; ! replaced
        assertEquals("weird name_.bin", FileService.sanitizeName("weird name!.bin"));
    }

    @Test
    void blankOrOnlyUnsafeBecomesFile() {
        assertEquals("file", FileService.sanitizeName("   "));
        // "!!!" → "___" then trim still non-blank — treat as file when no alnum left
        assertEquals("file", FileService.sanitizeName("!!!"));
    }
}
