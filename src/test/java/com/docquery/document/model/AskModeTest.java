package com.docquery.document.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AskModeTest {

    @Test
    void blankDefaultsToKb() {
        assertEquals(AskMode.KB, AskMode.fromParam(null));
        assertEquals(AskMode.KB, AskMode.fromParam(""));
        assertEquals(AskMode.KB, AskMode.fromParam("  "));
    }

    @Test
    void acceptsChatAndKbAliases() {
        assertEquals(AskMode.CHAT, AskMode.fromParam("chat"));
        assertEquals(AskMode.KB, AskMode.fromParam("KB"));
        assertEquals(AskMode.KB, AskMode.fromParam("kb_search"));
    }

    @Test
    void rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> AskMode.fromParam("AUTO"));
    }
}
