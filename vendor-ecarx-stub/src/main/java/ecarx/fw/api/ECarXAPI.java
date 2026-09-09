package ecarx.fw.api;

import android.content.Context;
import ecarx.fw.api.exceptions.APIInstantiationException;

public final class ECarXAPI {
    private ECarXAPI() {
    }

    /*
     * Keep the public descriptor identical to the API shipped by OneOS.
     * In particular, the return type is ICreator rather than the concrete
     * implementation used by this compile-time stub.
     */
    public static <T> ICreator<T> creator(Class<T> cls) {
        return new Creator<>(cls);
    }

    private static final class Creator<T> implements ICreator<T> {
        private final Class<T> clazz;

        public Creator(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T create(Context context) throws APIInstantiationException {
            throw new APIInstantiationException(
                    "Compile-time ECarX stub cannot create runtime API: " + clazz);
        }
    }
}
