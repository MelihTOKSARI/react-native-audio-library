import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-audio' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const Audio = NativeModules.Audio
  ? NativeModules.Audio
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

import AudioSdk from './audio-sdk/AudioSdk';
import Device from './audio-sdk/models/Device';

export function multiply(a: number, b: number): Promise<number> {
  return Audio.multiply(a, b);
}

export { AudioSdk, Device };