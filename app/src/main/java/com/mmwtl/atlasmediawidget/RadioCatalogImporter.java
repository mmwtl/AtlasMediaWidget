package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RadioCatalogImporter {
    static final String MANIFEST_NAME = "stations.csv";
    private static final String ROOT_NAME = "radio_catalog";
    private static final long MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 256L * 1024L;
    private static final int MAX_ENTRIES = 300;
    private static final int MAX_IMAGE_EDGE = 4_096;

    static final class Result {
        final int stations;
        final int covers;

        Result(int stations, int covers) {
            this.stations = stations;
            this.covers = covers;
        }
    }

    private RadioCatalogImporter() {}

    static Result importZip(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("Файл не выбран");
        File root = catalogRoot(context);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("Не удалось создать внутренний каталог");
        }
        String directoryName = "catalog-" + System.currentTimeMillis() + '-'
                + Long.toUnsignedString(System.nanoTime());
        File destination = new File(root, directoryName);
        if (!destination.mkdir()) throw new IOException("Не удалось подготовить импорт");
        boolean committed = false;
        try {
            File covers = new File(destination, "covers");
            if (!covers.mkdir()) throw new IOException("Не удалось подготовить обложки");
            Extraction extraction;
            try (InputStream source = context.getContentResolver().openInputStream(uri)) {
                if (source == null) throw new IOException("Не удалось открыть ZIP");
                extraction = extract(new ZipInputStream(new BufferedInputStream(source)),
                        destination, covers);
            }
            File manifest = new File(destination, MANIFEST_NAME);
            if (!manifest.isFile()) throw new IOException("В ZIP отсутствует stations.csv");
            List<RadioCatalogCsv.Row> rows;
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(manifest), StandardCharsets.UTF_8)) {
                rows = RadioCatalogCsv.read(reader);
            }
            validateRows(rows, covers);
            new Prefs(context).putString(Prefs.KEY_CUSTOM_RADIO_CATALOG, directoryName);
            committed = true;
            cleanupOldCatalogs(root, destination);
            return new Result(rows.size(), extraction.covers);
        } finally {
            if (!committed) deleteTree(destination);
        }
    }

    static String activeSummary(Context context) {
        String name = new Prefs(context).getString(Prefs.KEY_CUSTOM_RADIO_CATALOG, "");
        File directory = catalogDirectory(context, name);
        if (directory == null) return "Используется встроенный каталог Пензы";
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(
                new File(directory, MANIFEST_NAME)), StandardCharsets.UTF_8)) {
            return "Пользовательский каталог: " + RadioCatalogCsv.read(reader).size() + " станций";
        } catch (IOException error) {
            return "Пользовательский каталог повреждён; используется встроенный";
        }
    }

    static File clear(Context context) {
        Prefs prefs = new Prefs(context);
        File previous = catalogDirectory(context,
                prefs.getString(Prefs.KEY_CUSTOM_RADIO_CATALOG, ""));
        prefs.putString(Prefs.KEY_CUSTOM_RADIO_CATALOG, "");
        return previous;
    }

    static void deleteCatalog(File directory) {
        if (directory != null) deleteTree(directory);
    }

    static File catalogDirectory(Context context, String name) {
        if (name == null || !name.matches("catalog-[0-9]+-[0-9]+")) return null;
        File root = catalogRoot(context);
        File candidate = new File(root, name);
        try {
            String rootPath = root.getCanonicalPath() + File.separator;
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.startsWith(rootPath) && candidate.isDirectory() ? candidate : null;
        } catch (IOException error) {
            return null;
        }
    }

    private static Extraction extract(ZipInputStream zip, File destination, File covers)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        Set<String> names = new HashSet<>();
        long total = 0L;
        int entries = 0;
        int coverCount = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (++entries > MAX_ENTRIES) throw new IOException("В ZIP слишком много файлов");
            String name = entry.getName();
            if (entry.isDirectory()) {
                zip.closeEntry();
                continue;
            }
            if (!names.add(name)) throw new IOException("Повторяющийся файл в ZIP: " + name);
            File output;
            long entryLimit;
            if (MANIFEST_NAME.equals(name)) {
                output = new File(destination, MANIFEST_NAME);
                entryLimit = MAX_MANIFEST_BYTES;
            } else if (name.startsWith("covers/")) {
                String fileName = name.substring("covers/".length());
                validateCoverName(fileName);
                output = new File(covers, fileName);
                entryLimit = MAX_ENTRY_BYTES;
                coverCount++;
            } else {
                throw new IOException("Неизвестный файл в ZIP: " + name);
            }
            long copied = copy(zip, output, buffer, entryLimit);
            total += copied;
            if (total > MAX_TOTAL_BYTES) throw new IOException("ZIP после распаковки больше 64 МБ");
            zip.closeEntry();
        }
        return new Extraction(coverCount);
    }

    private static long copy(InputStream source, File output, byte[] buffer, long limit)
            throws IOException {
        long total = 0L;
        try (FileOutputStream target = new FileOutputStream(output)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Файл в ZIP превышает допустимый размер");
                target.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void validateRows(List<RadioCatalogCsv.Row> rows, File covers)
            throws IOException {
        Set<Integer> frequencies = new HashSet<>();
        for (RadioCatalogCsv.Row row : rows) {
            if (!frequencies.add(row.frequencyKHz)) {
                throw new IOException("Частота " + row.frequencyKHz + " указана дважды");
            }
            if (row.cover.isBlank()) continue;
            validateCoverName(row.cover);
            File cover = new File(covers, row.cover);
            if (!cover.isFile()) throw new IOException("Не найдена обложка covers/" + row.cover);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(cover.getAbsolutePath(), bounds);
            if (bounds.outWidth < 32 || bounds.outHeight < 32
                    || bounds.outWidth > MAX_IMAGE_EDGE || bounds.outHeight > MAX_IMAGE_EDGE) {
                throw new IOException("Некорректный размер обложки " + row.cover);
            }
        }
    }

    private static void validateCoverName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IOException("Недопустимое имя обложки: " + name);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".webp") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            throw new IOException("Поддерживаются только WebP, PNG и JPEG: " + name);
        }
    }

    private static File catalogRoot(Context context) {
        return new File(context.getFilesDir(), ROOT_NAME);
    }

    private static void cleanupOldCatalogs(File root, File active) {
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null || directories.length <= 2) return;
        File newestOther = null;
        for (File directory : directories) {
            if (directory.equals(active)) continue;
            if (newestOther == null || directory.lastModified() > newestOther.lastModified()) {
                newestOther = directory;
            }
        }
        for (File directory : directories) {
            if (!directory.equals(active) && !directory.equals(newestOther)) deleteTree(directory);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) AppLog.info("Cannot remove obsolete radio catalog " + file.getName());
    }

    private static final class Extraction {
        final int covers;

        Extraction(int covers) {
            this.covers = covers;
        }
    }
}
