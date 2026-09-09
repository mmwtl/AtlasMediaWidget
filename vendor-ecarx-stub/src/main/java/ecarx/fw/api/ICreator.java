package ecarx.fw.api;

import android.content.Context;
import ecarx.fw.api.exceptions.APIInstantiationException;

public interface ICreator<T> {
    T create(Context context) throws APIInstantiationException;
}
