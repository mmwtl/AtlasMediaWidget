package com.mmwtl.atlasmediawidget;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.IOException;

final class SettingsExportStore {
    static final class Result {
        final Uri uri;
        final String location;

        Result(Uri uri, String location) {
            this.uri = uri;
            this.location = location;
        }
    }

    private SettingsExportStore() {}

    static Result export(Context context, Prefs prefs) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues pending = new ContentValues();
        pending.put(MediaStore.MediaColumns.DISPLAY_NAME, SettingsBackup.FILE_NAME);
        pending.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        pending.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        pending.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending);
        if (uri == null) throw new IOException("Хранилище «Загрузки» недоступно");
        try {
            SettingsBackup.write(context, prefs, uri);
            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, published, null, null) != 1) {
                throw new IOException("Не удалось опубликовать файл в «Загрузках»");
            }
            return new Result(uri,
                    Environment.DIRECTORY_DOWNLOADS + "/" + SettingsBackup.FILE_NAME);
        } catch (Exception error) {
            resolver.delete(uri, null, null);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException("Не удалось сохранить файл в «Загрузки»", error);
        }
    }
}
