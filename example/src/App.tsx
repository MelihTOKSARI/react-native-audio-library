import React, { useEffect, useState } from 'react';

import { FlatList, View, Button } from 'react-native';

import AsyncStorage from '@react-native-async-storage/async-storage';
import { AudioSdk, Device } from 'react-native-audio-library';

import {
  Call,
  ConferenceLiveArray,
  LoginScreen,
  VertoClient,
  VertoInstanceManager,
  VertoParams,
  VertoView,
  ViewType,
} from 'react-native-verto-typescript';

let audioSdk: AudioSdk;

export default function App() {
  const [loggedIn, setLoggedIn] = useState(false);
  const [vertoClient, setVertoClient] = useState<VertoClient>();
  const [vertoParams, setVertoParams] = useState({
    webSocket: {
      url: '',
      login: '',
      password: '',
    },
    deviceParams: {
      useMic: 'any',
      useCamera: 'any',
      useSpeaker: 'any ',
    },
    videoParams: {
      minWidth: 320,
      maxWidth: 640,
      minHeight: 180,
      maxHeight: 480,
    },
    remoteVideo: 'remote-video',
    localVideo: 'local-video',
    iceServers: true,
  });
  const [callParams, setCallParams] = useState({
    to: 'CH1SN0S1',
    from: '1000',
    callerName: 'Hi',
  });
  const [callState, setCallState] = useState('');
  const [audioState, setAudioState] = useState(true);
  const [cameraState, setCameraState] = useState(true);
  const [audioDevices, setAudioDevices] = useState<Device[]>([]);

  useEffect(() => {
    audioSdk = new AudioSdk({
      onAudioDevicesUpdated,
    });
  }, []);

  useEffect(() => {
    checkLoginParams();
    checkCallParams();
    setTimeout(() => {
      setCallState('call');
    }, 200);
  }, []);

  const callbacks = {
    onPrivateEvent: (
      vertoClient: VertoClient,
      dataParams: VertoParams,
      userData: ConferenceLiveArray
    ) => {
      console.log(
        'onPrivateEvent',
        'vertoClient is null? ',
        vertoClient === null,
        ' - dataParams is null? ',
        dataParams === null,
        ' - userData is null? ',
        userData === null
      );
    },
    onEvent: (
      vertoClient: VertoClient,
      dataParams: VertoParams,
      userData: ConferenceLiveArray
    ) => {
      console.log(
        'onEvent',
        'vertoClient is null? ',
        vertoClient === null,
        ' - dataParams is null? ',
        dataParams === null,
        ' - userData is null? ',
        userData === null
      );
    },
    onCallStateChange: (state: any) => {
      console.log('onCallStateChange state.current.name:', state.current.name);
      if (state.current.name == 'active') {
        audioSdk.updateAudioMode(true, false);
        console.log('onCallStateChange 1');
        // updateCallAudioMode();
      } else if (state.current.name == 'destroy') {
        audioSdk.updateAudioMode(false);
        console.log('onCallStateChange 2');
      } else {
        console.log('onCallStateChange 3');
      }
    },
    onInfo: (params: any) => {
      console.log('onInfo params:', params);
    },
    onClientReady: (params: any) => {
      console.log('onClientReady params:', params);
    },
    onDisplay: (params: any) => {
      console.log('onDisplay params:', params);
    },
    onNewCall: (call: Call) => {
      console.log('onNewCall=>', call);
      setTimeout(() => {
        call.answer();
      }, 2000);
    },
  };

  const checkLoginParams = async () => {
    const loginValue = await AsyncStorage.getItem('login');
    if(loginValue != null) {
      const loginParams = JSON.parse(loginValue);

      setVertoAuthParams(loginParams);
      
      if(loginParams.password) {
        setLoggedIn(true);

        const tmpVertoClient = VertoInstanceManager.createInstance(
          {
            ...vertoParams,
            webSocket: loginParams
          }, 
          callbacks,
          true
        )
    
        setVertoClient(tmpVertoClient);
      }
    }
  }

  const checkCallParams = () => {
    if (!callParams) {
      setCallParams({
        to: 'CH1SN0S1',
        from: '1000',
        callerName: 'Hi',
      });
    }
  };

  const setVertoAuthParams = (authParams: any) => {
    const newVertoParams = {
      ...vertoParams,
      webSocket: authParams,
    };

    setVertoParams(newVertoParams);
  };

  const onLoginHandler = (login: string, password: string, url: string) => {
    if (!login || !password) {
      // TODO Show login warning
      return;
    }

    setVertoAuthParams({ login, password, url });

    setLoggedIn(true);

    AsyncStorage.setItem('login', JSON.stringify({ login, password, url }));
  };

  const onLogoutClicked = async () => {
    const loginValue = await AsyncStorage.getItem('login');
    if (!loginValue) {
      return;
    }

    const authItem = JSON.parse(loginValue.toString());
    authItem.password = '';

    await AsyncStorage.setItem('login', JSON.stringify(authItem));

    setLoggedIn(false);
  };

  const onAudioDevicesUpdated = (devices: Array<Device>) => {
    console.log('onAudioDevicesUpdated devices:', devices);
    setAudioDevices(devices);
  };

  const updateDevices = () => {
    audioSdk.updateDeviceList();
  };

  const audioDeviceChangeListener = (type: string) => {
    console.log(
      'audioDeviceChangeListener type:',
      type,
      ' - audioDevices:',
      audioDevices
    );
    if (audioSdk) {
      audioSdk.updateAudioDevice(type);
    }
  };

  const renderAudioDevice = (device: Device) => {
    return (
      <Button
        title={device.type || 'not'}
        onPress={() => audioDeviceChangeListener(device.uid || device.type)}
      />
    );
  };

  return (
    <View
      style={{
        flex: 1,
        justifyContent: 'center',
      }}
    >
      {!loggedIn && (
        <LoginScreen
          authParams={vertoParams.webSocket}
          onLoginHandler={onLoginHandler}
        />
      )}
      {loggedIn && (
        <VertoView 
          callState={callState}
          isAudioOff={audioState}
          isCameraOff={cameraState}
          isCallScreenVisible={true}
          isRemoteAudioOff={false}
          isToolboxVisible={true}
          onLogoutClicked={onLogoutClicked}
          showLogs={true}
          viewKey="view1"
          viewType={ViewType.both} 
        />
      )}
      {
        <View style={{ maxHeight: 60, flex: 1, flexDirection: 'row' }}>
          <Button title={'Audio'} onPress={() => setAudioState(!audioState)} />
          <Button
            title={'Video'}
            onPress={() => setCameraState(!cameraState)}
          />
          {audioDevices && (
            <FlatList
              data={audioDevices}
              keyExtractor={(device: Device) => device.type}
              renderItem={(item: any) => renderAudioDevice(item.item)}
            />
          )}
          <Button title={'Update Devices'} onPress={() => updateDevices()} />
        </View>
      }
    </View>
  );
}
