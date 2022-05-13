import { NativeEventEmitter, NativeModules } from 'react-native';
import Callback from './Callback';
import Device from './models/Device';

const { Audio } = NativeModules;
const AudioEmitter = new NativeEventEmitter(Audio);

export default class AudioSdk {
  private mode: any;
  private audioCallbacks: Callback;

  private state: any;

  constructor(callback: Callback) {
    this.audioCallbacks = callback;
    this.state = {
      devices: [],
    };
    this.initializeSdk();
  }

  private initializeSdk = () => {
    AudioEmitter.addListener(
      Audio.DEVICE_CHANGE_EVENT,
      this.onDevicesUpdate,
      this
    );
  };

  private updateDevices(devices: Array<Device>) {
    this.state.devices = devices;
    if (this.audioCallbacks) {
      this.audioCallbacks.onAudioDevicesUpdated(this.state.devices);
    } else {
      console.log('AudioCallbacks is undefined!');
    }
  }

  /**
   * Handles audio device changes. The list will be stored on the redux store.
   *
   * @param {Object} devices - The current list of devices.
   * @private
   * @returns {void}
   */
  private onDevicesUpdate(devices: Array<Device>): void {
    this.updateDevices(devices);
  }

  /**
   * Updates the audio mode based on the current (redux) state.
   *
   * @public
   * @returns {void}.
   */
  public updateAudioMode(inCall?: boolean, isVideo?: boolean, isSilent?: boolean): void {
    this.mode = inCall
      ? isSilent
        ? Audio.SILENT
        : isVideo
          ? Audio.VIDEO_CALL
          : Audio.AUDIO_CALL
      : Audio.DEFAULT;

    Audio.setMode(this.mode).catch((err: any) =>
      console.log(`Failed to set audio mode ${String(this.mode)}: ${err}`)
    );
  }

  public updateDeviceList(): void {
    Audio.updateDeviceList && Audio.updateDeviceList();
  }

  public updateAudioDevice(type: string): void {
    try {
      Audio.setAudioDevice(type);
    } catch (error) {
      console.log(`Failed to set audio device ${String(type)}: ${error}`);
    }
    // switch(type) {
    //     case 'Speaker':
    //         AudioLibrary.setAudioDevice("SPEAKER");
    //         break;
    //     case 'Headphone':
    //         AudioLibrary.setAudioDevice("HEADPHONES");
    //         break;
    //     default:
    //         console.log('default updateAudioDevice type:', type);
    // }
  }
}
