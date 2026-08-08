package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public final class RadioCatalogCsvTest {
    @Test public void parsesQuotedNamesAndOptionalCovers() throws Exception {
        List<RadioCatalogCsv.Row> rows = RadioCatalogCsv.read(new StringReader(
                "frequency_khz,name,band,cover\n"
                        + "100100,Радио 7,FM,radio7.webp\n"
                        + "999,\"Пример, AM\",AM,\n"));

        assertEquals(2, rows.size());
        assertEquals("Пример, AM", rows.get(1).name);
        assertEquals("", rows.get(1).cover);
    }

    @Test public void rejectsBandThatDoesNotMatchFrequencyRange() {
        IOException error = assertThrows(IOException.class, () -> RadioCatalogCsv.read(
                new StringReader("frequency_khz,name,band,cover\n100100,Test,AM,test.webp\n")));

        assertEquals("Строка 2: для частоты требуется FM", error.getMessage());
    }

    @Test public void rejectsUnexpectedHeader() {
        assertThrows(IOException.class, () -> RadioCatalogCsv.read(
                new StringReader("frequency,name\n100.1,Test\n")));
    }
}
