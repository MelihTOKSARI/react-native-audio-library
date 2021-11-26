import Device from './models/Device';

export default interface Callback {
  onAudioDevicesUpdated(devices: Array<Device>): void;
}
