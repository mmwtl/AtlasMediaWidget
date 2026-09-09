package com.ecarx.xui.adaptapi.binder;

public interface IConnectable {
    interface IConnectWatcher {
        void onConnected();
        void onDisConnected();
    }

    void connect();
    void disconnect();
    void registerConnectWatcher(IConnectWatcher iConnectWatcher);
    void unregisterConnectWatcher();
}
