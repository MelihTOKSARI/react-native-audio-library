package com.reactnativeaudio;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link AudioModule.AudioDeviceHandlerInterface} module implementing device handling for
 * all post-M Android versions. This handler can be used on any Android versions >= M, but by
 * default it's only used on versions < O, since versions >= O use ConnectionService, but it
 * can be disabled.
 */
public class AudioDeviceHandlerGeneric implements
        AudioModule.AudioDeviceHandlerInterface,
        AudioManager.OnAudioFocusChangeListener {

    // private final static String TAG = AudioDeviceHandlerGeneric.class.getSimpleName();

    /**
     * Reference to the main {@code AudioModule}.
     */
    private AudioModule module;

    /**
     * Constant defining a USB headset. Only available on API level >= 26.
     * The value of: AudioDeviceInfo.TYPE_USB_HEADSET
     */
    private static final int TYPE_USB_HEADSET = 22;

    /**
     * Indicator that we have lost audio focus.
     */
    private boolean audioFocusLost = false;

    /**
     * {@link AudioManager} instance used to interact with the Android audio
     * subsystem.
     */
    private final AudioManager audioManager;

    /**
     * {@link Runnable} for running audio device detection in the audio thread.
     * This is only used on Android >= M.
     */
    private final Runnable onAudioDeviceChangeRunner = new Runnable() {
        @Override
        public void run() {
            Set<String> devices = new HashSet<>();
            AudioDeviceInfo[] deviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);

            for (AudioDeviceInfo info: deviceInfos) {
                switch (info.getType()) {
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                        devices.add(AudioModule.DEVICE_BLUETOOTH);
                        break;
                    case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                        devices.add(AudioModule.DEVICE_EARPIECE);
                        break;
                    case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                        devices.add(AudioModule.DEVICE_SPEAKER);
                        break;
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    case TYPE_USB_HEADSET:
                        devices.add(AudioModule.DEVICE_HEADPHONES);
                        break;
                }
            }

            module.replaceDevices(devices);

            module.updateAudioRoute();
        }
    };

    private final android.media.AudioDeviceCallback audioDeviceCallback =
            new android.media.AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(
                        AudioDeviceInfo[] addedDevices) {
                    onAudioDeviceChange();
                }

                @Override
                public void onAudioDevicesRemoved(
                        AudioDeviceInfo[] removedDevices) {
                    onAudioDeviceChange();
                }
            };

    public AudioDeviceHandlerGeneric(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * Helper method to trigger an audio route update when devices change. It
     * makes sure the operation is performed on the audio thread.
     */
    private void onAudioDeviceChange() {
        module.runInAudioThread(onAudioDeviceChangeRunner);
    }

    /**
     * {@link AudioManager.OnAudioFocusChangeListener} interface method. Called
     * when the audio focus of the system is updated.
     *
     * @param focusChange - The type of focus change.
     */
    @Override
    public void onAudioFocusChange(final int focusChange) {
        module.runInAudioThread(() -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN: {
                    // Some other application potentially stole our audio focus
                    // temporarily. Restore our mode.
                    if (audioFocusLost) {
                        module.resetAudioRoute();
                    }
                    audioFocusLost = false;
                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                    audioFocusLost = true;
                    break;
                }
            }
        });
    }

    /**
     * Helper method to set the output route to a Bluetooth device.
     *
     * @param enabled true if Bluetooth should use used, false otherwise.
     */
    private void setBluetoothAudioRoute(boolean enabled) {
        if (enabled) {
            audioManager.startBluetoothSco();
            audioManager.setBluetoothScoOn(true);
        } else {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
        }
    }

    @Override
    public void start(AudioModule AudioModule) {
        module = AudioModule;

        // Setup runtime device change detection.
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);

        // Do an initial detection.
        onAudioDeviceChange();
    }

    @Override
    public void stop() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
    }

    @Override
    public void setAudioRoute(String device) {
        // Turn speaker on / off
        audioManager.setSpeakerphoneOn(device.equals(AudioModule.DEVICE_SPEAKER));

        // Turn bluetooth on / off
        setBluetoothAudioRoute(device.equals(AudioModule.DEVICE_BLUETOOTH));
    }

    @Override
    public boolean setMode(int mode) {
        if (mode == AudioModule.DEFAULT) {
            audioFocusLost = false;
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.abandonAudioFocus(this);
            audioManager.setSpeakerphoneOn(false);
            setBluetoothAudioRoute(false);

            return true;
        }

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setMicrophoneMute(false);

        int gotFocus;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gotFocus = audioManager.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()
            );
        } else {
            gotFocus = audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        }

      return gotFocus != AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    }
}
