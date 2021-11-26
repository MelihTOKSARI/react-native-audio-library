package com.reactnativeaudio;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.List;

public class ReactInstanceManagerHolder {

    public static List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new AudioModule(reactContext));

        if (AudioModule.useConnectionService()) {
            modules.add(new RNConnectionService(reactContext));
        }

        return modules;
    }

    /**
     * Helper function to send an event to JavaScript.
     *
     * @param eventName {@code String} containing the event name.
     * @param data {@code Object} optional ancillary data for the event.
     */
    static void emitEvent(ReactContext reactContext, String eventName, @Nullable Object data) {
        if (reactContext != null) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, data);
        }
    }

    /**
     * Finds a native React module for given class.
     *
     * @param nativeModuleClass the native module's class for which an instance
     * is to be retrieved from the React context.
     * @param <T> the module's type.
     * @return {@link NativeModule} instance for given interface type or
     * {@code null} if no instance for this interface is available, or if
     * ReactContext has not been initialized yet.
     */
    static <T extends NativeModule> T getNativeModule(ReactContext reactContext, Class<T> nativeModuleClass) {
        return reactContext != null
            ? reactContext.getNativeModule(nativeModuleClass) : null;
    }

}
