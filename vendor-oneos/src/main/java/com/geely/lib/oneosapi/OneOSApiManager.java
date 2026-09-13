package com.geely.lib.oneosapi;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.geely.lib.oneosapi.common.ServiceBaseManager;
import com.geely.lib.oneosapi.listener.ApiConnectCallBack;
import com.geely.lib.oneosapi.listener.ServiceConnectionListener;
import com.geely.lib.oneosapi.mediacenter.MediaCenterManager;

/* loaded from: classes.dex */
public class OneOSApiManager implements ServiceConnectionListener {
    private static final String TAG = "OneOSApiManager";
    private static final int SERVICE_MEDIA_CENTER = 3;
    private static volatile OneOSApiManager sInstance;
    private final Context mContext;
    private volatile MediaCenterManager mMediaCenterManager;
    private final ServiceConnectionManager mServiceConnectionManager;

    public static OneOSApiManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (OneOSApiManager.class) {
                if (sInstance == null) {
                    sInstance = new OneOSApiManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    public void init() {
        this.mServiceConnectionManager.connect();
    }

    public void init(ApiConnectCallBack callBack) {
        this.mServiceConnectionManager.connect(callBack);
    }

    public void release() {
        this.mServiceConnectionManager.release();
        this.mMediaCenterManager = null;
    }

    public void registerServiceConnectionListener(ServiceConnectionListener listener) {
        this.mServiceConnectionManager.registerServiceConnectionListener(listener);
    }

    public void unregisterServiceConnectionListener(ServiceConnectionListener listener) {
        this.mServiceConnectionManager.unregisterServiceConnectionListener(listener);
    }

    private OneOSApiManager(Context context) {
        this.mContext = context;
        ServiceConnectionManager serviceConnectionManager = new ServiceConnectionManager(context);
        this.mServiceConnectionManager = serviceConnectionManager;
        serviceConnectionManager.registerServiceConnectionListener(this);
    }

    public MediaCenterManager getMediaCenterManager() {
        if (this.mMediaCenterManager == null) {
            synchronized (OneOSApiManager.class) {
                if (this.mMediaCenterManager == null && this.mServiceConnectionManager.isServiceBound()) {
                    try {
                        this.mMediaCenterManager = new MediaCenterManager(this.mContext, this.mServiceConnectionManager.getServiceManager().getService(SERVICE_MEDIA_CENTER));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }
        }
        return this.mMediaCenterManager;
    }

    private void updateServiceBinder(ServiceBaseManager manager, int serviceType) {
        if (manager != null) {
            try {
                IServiceManager serviceManager = this.mServiceConnectionManager.getServiceManager();
                if (serviceManager != null) {
                    manager.setService(serviceManager.getService(serviceType));
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override // com.geely.lib.oneosapi.listener.ServiceConnectionListener
    public void onServiceConnectionChanged(boolean connectionState) {
        Log.i(TAG, "onBinderStateChanged  binderState:" + connectionState);
        if (connectionState) {
            updateServiceBinder(this.mMediaCenterManager, SERVICE_MEDIA_CENTER);
        }
    }

    @Override // com.geely.lib.oneosapi.listener.ServiceConnectionListener
    public void onServiceBinderUpdated(int binderType) {
        Log.i(TAG, "onBinderUpdate  binderType:" + binderType);
        if (binderType == SERVICE_MEDIA_CENTER) {
            updateServiceBinder(this.mMediaCenterManager, binderType);
        }
    }

    public boolean addService(int type, IBinder binder) {
        Log.d(TAG, "addService() called with: type = [" + type + "], binder = [" + binder + "]");
        if (!this.mServiceConnectionManager.isServiceBound()) {
            return false;
        }
        Log.d(TAG, "isServiceBound");
        try {
            this.mServiceConnectionManager.getServiceManager().addService(type, binder);
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.d(TAG, "RemoteException." + e.getMessage());
            return false;
        }
    }
}