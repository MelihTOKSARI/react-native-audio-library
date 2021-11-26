package com.reactnativeaudio;

import android.media.AudioManager;
import android.os.Build;
import android.telecom.CallAudioState;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link AudioModule.AudioDeviceHandlerInterface} module implementing device handling for
 * Android versions >= O when ConnectionService is enabled.
 */
@SuppressWarnings("CommentedOutCode")
@RequiresApi(Build.VERSION_CODES.O)
public class AudioDeviceHandlerConnectionService implements
        AudioModule.AudioDeviceHandlerInterface, RNConnectionService.CallAudioStateListener {

    private final static String TAG = AudioDeviceHandlerConnectionService.class.getSimpleName();

    /**
     * {@link AudioManager} instance used to interact with the Android audio subsystem.
     */
    private final AudioManager audioManager;

    /**
     * Reference to the main {@code AudioModule}.
     */
    private AudioModule module;

    /**
     * Converts any of the "DEVICE_" constants into the corresponding
     * {@link CallAudioState} "ROUTE_" number.
     *
     * @param audioDevice one of the "DEVICE_" constants.
     * @return a route number {@link CallAudioState#ROUTE_EARPIECE} if
     * no match is found.
     */
    private static int audioDeviceToRouteInt(String audioDevice) {
        if (audioDevice == null) {
            return CallAudioState.ROUTE_SPEAKER;
        }
        switch (audioDevice) {
            case AudioModule.DEVICE_BLUETOOTH:
                return CallAudioState.ROUTE_BLUETOOTH;
            case AudioModule.DEVICE_EARPIECE:
                return CallAudioState.ROUTE_EARPIECE;
            case AudioModule.DEVICE_HEADPHONES:
                return CallAudioState.ROUTE_WIRED_HEADSET;
            case AudioModule.DEVICE_SPEAKER:
                return CallAudioState.ROUTE_SPEAKER;
            default:
                Log.e(TAG, " Unsupported device name: " + audioDevice);
                return CallAudioState.ROUTE_SPEAKER;
        }
    }

    /**
     * Populates given route mask into the "DEVICE_" list.
     *
     * @param supportedRouteMask an integer coming from
     * {@link CallAudioState#getSupportedRouteMask()}.
     * @return a list of device names.
     */
    private static Set<String> routesToDeviceNames(int supportedRouteMask) {
        Set<String> devices = new HashSet<>();
        if ((supportedRouteMask & CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_EARPIECE) {
            devices.add(AudioModule.DEVICE_EARPIECE);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_BLUETOOTH) == CallAudioState.ROUTE_BLUETOOTH) {
            devices.add(AudioModule.DEVICE_BLUETOOTH);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_SPEAKER) == CallAudioState.ROUTE_SPEAKER) {
            devices.add(AudioModule.DEVICE_SPEAKER);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_WIRED_HEADSET) == CallAudioState.ROUTE_WIRED_HEADSET) {
            devices.add(AudioModule.DEVICE_HEADPHONES);
        }
        return devices;
    }

    /**
     * Used to store the most recently reported audio devices.
     * Makes it easier to compare for a change, because the devices are stored
     * as a mask in the {@link CallAudioState}. The mask is populated into
     * the {@code availableDevices} on each update.
     */
    private int supportedRouteMask = -1;

    public AudioDeviceHandlerConnectionService(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    @Override
    public void onCallAudioStateChange(final CallAudioState state) {
        module.runInAudioThread(() -> {
            boolean audioRouteChanged
                    = audioDeviceToRouteInt(module.getSelectedDevice()) != state.getRoute();
            int newSupportedRoutes = state.getSupportedRouteMask();
            boolean audioDevicesChanged = supportedRouteMask != newSupportedRoutes;
            if (audioDevicesChanged) {
                supportedRouteMask = newSupportedRoutes;
                Set<String> devices = routesToDeviceNames(supportedRouteMask);
                module.replaceDevices(devices);
            }

            if (audioRouteChanged || audioDevicesChanged) {
                module.resetSelectedDevice();
                module.updateAudioRoute();
            }
        });
    }

    @Override
    public void start(AudioModule AudioModule) {
        module = AudioModule;
        RNConnectionService rcs = ReactInstanceManagerHolder.getNativeModule(module.getReactContext(), RNConnectionService.class);
        if (rcs != null) {
            rcs.setCallAudioStateListener(this);
        } else {
            Log.e(TAG, " Couldn't set call audio state listener, module is null");
        }
    }

    @Override
    public void stop() {
        /*
        RNConnectionService rcs = ReactInstanceManagerHolder.getNativeModule(RNConnectionService.class);
        if (rcs != null) {
            rcs.setCallAudioStateListener(null);
        } else {
            Log.e(TAG, " Couldn't set call audio state listener, module is null");
        }
        */
    }

    public void setAudioRoute(String audioDevice) {
        // int newAudioRoute = audioDeviceToRouteInt(audioDevice);

        // RNConnectionService.setAudioRoute(newAudioRoute);
    }

    @Override
    public boolean setMode(int mode) {
        if (mode != AudioModule.DEFAULT) {
            // This shouldn't be needed when using ConnectionService, but some devices have been
            // observed not doing it.
            try {
                audioManager.setMicrophoneMute(false);
            } catch (Throwable tr) {
                Log.e(TAG, "Failed to unmute the microphone");
            }
        }

        return true;
    }
}
