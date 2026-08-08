package com.mmwtl.atlasmediawidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RadioCatalogCsv {
    static final class Row {
        final int frequencyKHz;
        final String name;
        final String band;
        final String cover;

        Row(int frequencyKHz, String name, String band, String cover) {
            this.frequencyKHz = frequencyKHz;
            this.name = name;
            this.band = band;
            this.cover = cover;
        }
    }

    private RadioCatalogCsv() {}

    static List<Row> read(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source : new BufferedReader(source);
        String header = reader.readLine();
        if (header == null || !header.replace("\uFEFF", "").trim()
                .equals("frequency_khz,name,band,cover")) {
            throw new IOException("Ожидается заголовок frequency_khz,name,band,cover");
        }
        List<Row> rows = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            List<String> fields = parseLine(line);
            if (fields.size() != 4) {
                throw new IOException("Строка " + lineNumber + ": требуется 4 поля");
            }
            int frequency;
            try {
                frequency = Integer.parseInt(fields.get(0).trim());
            } catch (NumberFormatException error) {
                throw new IOException("Строка " + lineNumber + ": неверная частота", error);
            }
            String name = fields.get(1).trim();
            String band = fields.get(2).trim().toUpperCase(Locale.ROOT);
            String cover = fields.get(3).trim();
            if (name.isEmpty() || name.length() > 80) {
                throw new IOException("Строка " + lineNumber + ": название должно быть от 1 до 80 символов");
            }
            if (!(frequency >= 87_500 && frequency <= 108_000
                    || frequency >= 500 && frequency <= 1_800)) {
                throw new IOException("Строка " + lineNumber + ": частота вне диапазона FM/AM");
            }
            String expectedBand = frequency >= 87_500 ? "FM" : "AM";
            if (!band.equals(expectedBand)) {
                throw new IOException("Строка " + lineNumber + ": для частоты требуется " + expectedBand);
            }
            rows.add(new Row(frequency, name, band, cover));
        }
        if (rows.isEmpty()) throw new IOException("Каталог не содержит радиостанций");
        if (rows.size() > 256) throw new IOException("В каталоге больше 256 радиостанций");
        return rows;
    }

    private static List<String> parseLine(String line) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else quoted = false;
                } else field.append(value);
            } else if (value == ',') {
                result.add(field.toString());
                field.setLength(0);
            } else if (value == '"' && field.length() == 0) {
                quoted = true;
            } else field.append(value);
        }
        if (quoted) throw new IOException("Незакрытая кавычка в CSV");
        result.add(field.toString());
        return result;
    }
}
