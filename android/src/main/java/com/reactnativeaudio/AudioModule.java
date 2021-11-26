package com.reactnativeaudio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ReactModule(name = AudioModule.NAME)
public class AudioModule extends ReactContextBaseJavaModule {
  public static final String NAME = "Audio";

  /**
   * Constants representing the audio mode.
   * - DEFAULT: Used before and after every call. It represents the default
   *   audio routing scheme.
   * - AUDIO_CALL: Used for audio only calls. It will use the earpiece by
   *   default, unless a wired or Bluetooth headset is connected.
   * - VIDEO_CALL: Used for video calls. It will use the speaker by default,
   *   unless a wired or Bluetooth headset is connected.
   */
  static final int DEFAULT    = 0;
  static final int AUDIO_CALL = 1;
  static final int VIDEO_CALL = 2;

  /**
   * Whether or not the ConnectionService is used for selecting audio devices.
   */
  @SuppressLint("AnnotateVersionCheck")
  private static final boolean supportsConnectionService = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
  private static boolean useConnectionService_ = supportsConnectionService;

  static boolean useConnectionService() {
    return supportsConnectionService && useConnectionService_;
  }

  /**
   * {@link AudioManager} instance used to interact with the Android audio
   * subsystem.
   */
  private final AudioManager audioManager;

  private AudioDeviceHandlerInterface audioDeviceHandler;

  /**
   * {@link ExecutorService} for running all audio operations on a dedicated
   * thread.
   */
  private static final ExecutorService executor = Executors.newSingleThreadExecutor();

  /**
   * Audio mode currently in use.
   */
  private int mode = -1;

  private final ReactApplicationContext reactContext;

  /**
   * Audio device types.
   */
  static final String DEVICE_BLUETOOTH  = "BLUETOOTH";
  static final String DEVICE_EARPIECE   = "EARPIECE";
  static final String DEVICE_HEADPHONES = "HEADPHONES";
  static final String DEVICE_SPEAKER    = "SPEAKER";

  /**
   * Device change event.
   */
  public static final String DEVICE_CHANGE_EVENT = "audio-mode#devices-update";

  /**
   * List of currently available audio devices.
   */
  private Set<String> availableDevices = new HashSet<>();

  /**
   * Currently selected device.
   */
  private String selectedDevice;

  /**
   * User selected device. When null the default is used depending on the
   * mode.
   */
  private String userSelectedDevice;

  public AudioModule(ReactApplicationContext reactContext) {
    super(reactContext);

    this.reactContext = reactContext;

    audioManager = (AudioManager)reactContext.getSystemService(Context.AUDIO_SERVICE);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }


  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  public void multiply(int a, int b, Promise promise) {
    promise.resolve(a * b);
  }

  public static native int nativeMultiply(int a, int b);

  /**
   * Gets a mapping with the constants this module is exporting.
   *
   * @return a {@link Map} mapping the constants to be exported with their
   * values.
   */
  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();

    constants.put("DEVICE_CHANGE_EVENT", DEVICE_CHANGE_EVENT);
    constants.put("AUDIO_CALL", AUDIO_CALL);
    constants.put("DEFAULT", DEFAULT);
    constants.put("VIDEO_CALL", VIDEO_CALL);

    return constants;
  }

  /**
   * Helper function to run operations on a dedicated thread.
   * @param runnable ui thread
   */
  void runInAudioThread(Runnable runnable) {
    executor.execute(runnable);
  }

  /**
   * Notifies JS land that the devices list has changed.
   */
  private void notifyDevicesChanged() {
    runInAudioThread(() -> {
      WritableArray data = Arguments.createArray();
      final boolean hasHeadphones = availableDevices.contains(DEVICE_HEADPHONES);
      for (String device : availableDevices) {
        if (hasHeadphones && device.equals(DEVICE_EARPIECE)) {
          // Skip earpiece when headphones are plugged in.
          continue;
        }
        WritableMap deviceInfo = Arguments.createMap();
        deviceInfo.putString("type", device);
        deviceInfo.putBoolean("selected", device.equals(selectedDevice));
        data.pushMap(deviceInfo);
      }

      ReactInstanceManagerHolder.emitEvent(reactContext, DEVICE_CHANGE_EVENT, data);
    });
  }

  public ReactApplicationContext getReactContext() {
    return reactContext;
  }

  /**
   * Initializes the audio device handler module. This function is called *after* all Catalyst
   * modules have been created, and that's why we use it, because {@link AudioDeviceHandlerConnectionService}
   * needs access to another Catalyst module, so doing this in the constructor would be too early.
   */
  @Override
  public void initialize() {
    runInAudioThread(this::setAudioDeviceHandler);
  }

  private void setAudioDeviceHandler() {

    if (audioDeviceHandler != null) {
      audioDeviceHandler.stop();
    }

        /*
        if (useConnectionService()) {
            audioDeviceHandler = new AudioDeviceHandlerConnectionService(audioManager);
        } else {
            audioDeviceHandler = new AudioDeviceHandlerGeneric(audioManager);
        }
        */
    audioDeviceHandler = new AudioDeviceHandlerGeneric(audioManager);

    audioDeviceHandler.start(this);

  }

  /**
   * Sets the user selected audio device as the active audio device.
   *
   * @param device the desired device which will become active.
   */
  @ReactMethod
  public void setAudioDevice(final String device) {
    runInAudioThread(() -> {
      if (!availableDevices.contains(device)) {
        userSelectedDevice = null;
        return;
      }

      if (mode != -1) {
        userSelectedDevice = device;
        updateAudioRoute(mode, false);
      }
    });
  }

  /**
   * Public method to set the current audio mode.
   *
   * @param mode the desired audio mode.
   * @param promise a {@link Promise} which will be resolved if the audio mode
   * could be updated successfully, and it will be rejected otherwise.
   */
  @ReactMethod
  public void setMode(final int mode, final Promise promise) {
    if (mode != DEFAULT && mode != AUDIO_CALL && mode != VIDEO_CALL) {
      promise.reject("setMode", "Invalid audio mode " + mode);
      return;
    }

    Activity currentActivity = getCurrentActivity();
    if (currentActivity != null) {
      if (mode == DEFAULT) {
        currentActivity.setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
      } else {
        currentActivity.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
      }
    }

    runInAudioThread(() -> {
      boolean success;

      try {
        success = updateAudioRoute(mode, false);
      } catch (Throwable e) {
        success = false;
      }
      if (success) {
        AudioModule.this.mode = mode;
        promise.resolve(null);
      } else {
        promise.reject("setMode", "Failed to set audio mode to " + mode);
      }
    });
  }

  /**
   * Sets whether ConnectionService should be used (if available) for setting the audio mode
   * or not.
   *
   * @param use Boolean indicator of where it should be used or not.
   */
  @ReactMethod
  public void setUseConnectionService(final boolean use) {
    runInAudioThread(() -> {
      useConnectionService_ = use;
      setAudioDeviceHandler();
    });
  }

  /**
   * Updates the audio route for the given mode.
   *
   * @param mode the audio mode to be used when computing the audio route.
   * @return {@code true} if the audio route was updated successfully;
   * {@code false}, otherwise.
   */
  private boolean updateAudioRoute(int mode, boolean force) {
    if (!audioDeviceHandler.setMode(mode)) {
      return false;
    }

    if (mode == DEFAULT) {
      selectedDevice = null;
      userSelectedDevice = null;

      notifyDevicesChanged();
      return true;
    }

    boolean bluetoothAvailable = availableDevices.contains(DEVICE_BLUETOOTH);
    boolean headsetAvailable = availableDevices.contains(DEVICE_HEADPHONES);

    // Pick the desired device based on what's available and the mode.
    String audioDevice;
    if (bluetoothAvailable) {
      audioDevice = DEVICE_BLUETOOTH;
    } else if (headsetAvailable) {
      audioDevice = DEVICE_HEADPHONES;
    } else {
      audioDevice = DEVICE_SPEAKER;
    }

    // Consider the user's selection
    if (userSelectedDevice != null && availableDevices.contains(userSelectedDevice)) {
      audioDevice = userSelectedDevice;
    }

    // If the previously selected device and the current default one
    // match, do nothing.
    if (!force && selectedDevice != null && selectedDevice.equals(audioDevice)) {
      return true;
    }

    selectedDevice = audioDevice;

    audioDeviceHandler.setAudioRoute(audioDevice);

    notifyDevicesChanged();
    return true;
  }

  /**
   * Gets the currently selected audio device.
   *
   * @return The selected audio device.
   */
  String getSelectedDevice() {
    return selectedDevice;
  }

  /**
   * Resets the current device selection.
   */
  void resetSelectedDevice() {
    selectedDevice = null;
    userSelectedDevice = null;
  }

  /**
   * Adds a new device to the list of available devices.
   *
   * @param device The new device.
   */
  void addDevice(String device) {
    availableDevices.add(device);
    resetSelectedDevice();
  }

  /**
   * Removes a device from the list of available devices.
   *
   * @param device The old device to the removed.
   */
  void removeDevice(String device) {
    availableDevices.remove(device);
    resetSelectedDevice();
  }

  /**
   * Replaces the current list of available devices with a new one.
   *
   * @param devices The new devices list.
   */
  void replaceDevices(Set<String> devices) {
    availableDevices = devices;
    resetSelectedDevice();
  }

  /**
   * Re-sets the current audio route. Needed when devices changes have happened.
   */
  void updateAudioRoute() {
    if (mode != -1) {
      updateAudioRoute(mode, false);
    }
  }

  /**
   * Re-sets the current audio route. Needed when focus is lost and regained.
   */
  void resetAudioRoute() {
    if (mode != -1) {
      updateAudioRoute(mode, true);
    }
  }

  /**
   * Interface for the modules implementing the actual audio device management.
   */
  public interface AudioDeviceHandlerInterface {
    /**
     * Start detecting audio device changes.
     * @param audioModule Reference to the main {@link AudioModule}.
     */
    void start(AudioModule audioModule);

    /**
     * Stop audio device detection.
     */
    void stop();

    /**
     * Set the appropriate route for the given audio device.
     *
     * @param device Audio device for which the route must be set.
     */
    void setAudioRoute(String device);

    /**
     * Set the given audio mode.
     *
     * @param mode The new audio mode to be used.
     * @return Whether the operation was successful or not.
     */
    boolean setMode(int mode);
  }
}
