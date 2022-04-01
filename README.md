# react-native-audio-library

Display active audio devices and change audio device output on webrtc auido stream

## Installation

Run below command to install package.

```sh
npm install react-native-audio-library
```
This package is constructed on <strong>react-native-webrtc</strong> package for onyl call operations. Therefore, you should run below command if <strong>react-native-webrtc</strong> package is not installed already.

```sh
npm install react-native-webrtc
```

## Usage

First, create instance of <strong>AudioSdk</strong> to get list of active audio output devices and change audio device.

### \#AudioSdk

```js
import { AudioSdk, Device } from "react-native-audio-library";

const onAudioDevicesUpdated = (devices: Array<Device>) => {
    console.log('[onAudioDevicesUpdated] devices:', JSON.stringfy(devices));
}

const audioSdk: AudioSdk = new AudioSdk({
    onAudioDevicesUpdated
});

```

### \#updateAudioMode

Update AudioMode for call states to activate/deactivate callback for [audio devices](README.md#AudioSdk).

```js
/**
 * Updates the audio mode based on call states.
 * 
 * @param inCall Set true if any call exists
 * @param isVideo Set true for video calls
 */
public updateAudioMode(inCall?: boolean, isVideo?: boolean);
```

### \#updateDeviceList

<strong>Only for iOS</strong>
Get currently active devices.

```js
audioSdk.updateDeviceList();
```

### \#updateAudioDevice
Update currently active audio output device.

```js
audioSdk.updateAudioDevice(device.uid || device.type);
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
