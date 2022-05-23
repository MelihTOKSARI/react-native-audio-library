//
//  Audio2.m
//  Audio
//
//  Created by KULLANICI on 22.11.2021.
//  Copyright © 2021 Facebook. All rights reserved.
//

#import <AVFoundation/AVFoundation.h>

#import <React/RCTEventEmitter.h>
#import <React/RCTLog.h>
#import <WebRTC/WebRTC.h>

#import "AudioSession+Private.h"

// Audio mode
typedef enum {
    kAudioModeDefault,
    kAudioModeSilent,
    kAudioModeAudioCall,
    kAudioModeVideoCall
} AudioMode;

// Events
static NSString * const kDevicesChanged = @"audio-mode#devices-update";

// Device types (must match JS and Java)
static NSString * const kDeviceTypeHeadphones = @"HEADPHONES";
static NSString * const kDeviceTypeBluetooth  = @"BLUETOOTH";
static NSString * const kDeviceTypeEarpiece   = @"EARPIECE";
static NSString * const kDeviceTypeSpeaker    = @"SPEAKER";
static NSString * const kDeviceTypeUnknown    = @"UNKNOWN";


@interface Audio : RCTEventEmitter<RTCAudioSessionDelegate>

@property(nonatomic, strong) dispatch_queue_t workerQueue;

@end

@implementation Audio {
    AudioMode activeMode;
    RTCAudioSessionConfiguration *defaultConfig;
    RTCAudioSessionConfiguration *silentCallConfig;
    RTCAudioSessionConfiguration *audioCallConfig;
    RTCAudioSessionConfiguration *videoCallConfig;
    RTCAudioSessionConfiguration *earpieceConfig;
    BOOL forceSpeaker;
    BOOL forceEarpiece;
    BOOL isSpeakerOn;
    BOOL isEarpieceOn;
}

RCT_EXPORT_MODULE();

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[ kDevicesChanged ];
}

- (NSDictionary *)constantsToExport {
    return @{
        @"DEVICE_CHANGE_EVENT": kDevicesChanged,
        @"AUDIO_CALL" : [NSNumber numberWithInt: kAudioModeAudioCall],
        @"DEFAULT"    : [NSNumber numberWithInt: kAudioModeDefault],
        @"SILENT"     : [NSNumber numberWithInt: kAudioModeSilent],
        @"VIDEO_CALL" : [NSNumber numberWithInt: kAudioModeVideoCall]
    };
};

- (instancetype)init {
    self = [super init];
    if (self) {
        dispatch_queue_attr_t attributes =
        dispatch_queue_attr_make_with_qos_class(DISPATCH_QUEUE_SERIAL, QOS_CLASS_USER_INITIATED, -1);
        _workerQueue = dispatch_queue_create("AudioMode.queue", attributes);

        activeMode = kAudioModeDefault;

        defaultConfig = [[RTCAudioSessionConfiguration alloc] init];
        defaultConfig.category = AVAudioSessionCategoryAmbient;
        defaultConfig.categoryOptions = 0;
        defaultConfig.mode = AVAudioSessionModeDefault;
        
        silentCallConfig = [[RTCAudioSessionConfiguration alloc] init];
        silentCallConfig.category = AVAudioSessionCategoryAmbient;
        silentCallConfig.categoryOptions = 0;
        silentCallConfig.mode = AVAudioSessionModeDefault;

        audioCallConfig = [[RTCAudioSessionConfiguration alloc] init];
        audioCallConfig.category = AVAudioSessionCategoryPlayAndRecord;
        audioCallConfig.categoryOptions = AVAudioSessionCategoryOptionAllowBluetooth | AVAudioSessionCategoryOptionDefaultToSpeaker;
        audioCallConfig.mode = AVAudioSessionModeVoiceChat;

        videoCallConfig = [[RTCAudioSessionConfiguration alloc] init];
        videoCallConfig.category = AVAudioSessionCategoryPlayAndRecord;
        videoCallConfig.categoryOptions = AVAudioSessionCategoryOptionAllowBluetooth;
        videoCallConfig.mode = AVAudioSessionModeVideoChat;

        // Manually routing audio to the earpiece doesn't quite work unless one disables BT (weird, I know).
        earpieceConfig = [[RTCAudioSessionConfiguration alloc] init];
        earpieceConfig.category = AVAudioSessionCategoryPlayAndRecord;
        earpieceConfig.categoryOptions = 0;
        earpieceConfig.mode = AVAudioSessionModeVoiceChat;

        forceSpeaker = NO;
        forceEarpiece = NO;
        isSpeakerOn = NO;
        isEarpieceOn = NO;

        RTCAudioSession *session = AudioSession.rtcAudioSession;
        [session addDelegate:self];
    }

    return self;
}

- (dispatch_queue_t)methodQueue {
    // Use a dedicated queue for audio mode operations.
    return _workerQueue;
}

- (BOOL)setConfigWithoutLock:(RTCAudioSessionConfiguration *)config
                       error:(NSError * _Nullable *)outError {
    RTCAudioSession *session = AudioSession.rtcAudioSession;

    return [session setConfiguration:config error:outError];
}

- (BOOL)setConfig:(RTCAudioSessionConfiguration *)config
            error:(NSError * _Nullable *)outError {

    RTCAudioSession *session = AudioSession.rtcAudioSession;
    [session lockForConfiguration];
    BOOL success = [self setConfigWithoutLock:config error:outError];
    [session unlockForConfiguration];

    return success;
}

- (void)setInputGain:(CGFloat)gain
{
   AVAudioSession *audioSession = [AVAudioSession sharedInstance];
   if (audioSession.isInputGainSettable) {
     NSError *error = nil;
     BOOL success = [audioSession setInputGain:gain error:&error];
     if (!success) {
       NSLog(@"[AudioMode-setInputGain] %@", error);
     }
   }
   else {
     NSLog(@"[AudioMode-setInputGain] Cannot set input gain");
   }
}

#pragma mark - Exported methods

RCT_EXPORT_METHOD(setMode:(int)mode
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject) {
    RTCAudioSessionConfiguration *config = [self configForMode:mode];
    NSError *error;
    
    if (config == nil) {
        reject(@"setMode", @"Invalid mode", nil);
        return;
    }
    
    // Reset.
    if (mode == kAudioModeDefault || mode == kAudioModeSilent) {
        forceSpeaker = NO;
        forceEarpiece = NO;
        [self setInputGain:0.0];
    }
    
    activeMode = mode;
    
    if ([self setConfig:config error:&error]) {
        resolve(nil);
    } else {
        reject(@"setMode", error.localizedDescription, error);
    }
    
    [self notifyDevicesChanged];
}

RCT_EXPORT_METHOD(setAudioDevice:(NSString *)device
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    NSLog(@"[AudioMode] Selected device: %@", device);
    
    RTCAudioSession *session = AudioSession.rtcAudioSession;
    [session lockForConfiguration];
    BOOL success;
    NSError *error = nil;
    
    // Reset these, as we are about to compute them.
    forceSpeaker = NO;
    forceEarpiece = NO;
    
    // The speaker is special, so test for it first.
    if ([device isEqualToString:kDeviceTypeSpeaker]) {
        forceSpeaker = NO;
        success = [session overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:&error];
    } else {
        // Here we use AVAudioSession because RTCAudioSession doesn't expose availableInputs.
        AVAudioSession *_session = [AVAudioSession sharedInstance];
        AVAudioSessionPortDescription *port = nil;

        // Find the matching input device.
        for (AVAudioSessionPortDescription *portDesc in _session.availableInputs) {
            if ([portDesc.UID isEqualToString:device]) {
                port = portDesc;
                break;
            }
        }

        if (port != nil) {
            // First remove the override if we are going to select a different device.
            if (isSpeakerOn) {
                [session overrideOutputAudioPort:AVAudioSessionPortOverrideNone error:nil];
            }
            
            // Special case for the earpiece.
            if ([port.portType isEqualToString:AVAudioSessionPortBuiltInMic]) {
                forceEarpiece = YES;
                [self setConfigWithoutLock:earpieceConfig error:nil];
            } else if (isEarpieceOn) {
                // Reset the config.
                RTCAudioSessionConfiguration *config = [self configForMode:activeMode];
                [self setConfigWithoutLock:config error:nil];
            }

            // Select our preferred input.
            success = [session setPreferredInput:port error:&error];
        } else {
            [self notifyDevicesChanged];
            success = NO;
            error = RCTErrorWithMessage(@"Could not find audio device");
        }
    }
    
    [session unlockForConfiguration];
    
    if (success) {
        resolve(nil);
    } else {
        reject(@"setAudioDevice", error != nil ? error.localizedDescription : @"", error);
    }
}

RCT_EXPORT_METHOD(updateDeviceList) {
    [self notifyDevicesChanged];
}

#pragma mark - RTCAudioSessionDelegate

- (void)audioSessionDidChangeRoute:(RTCAudioSession *)session
                            reason:(AVAudioSessionRouteChangeReason)reason
                     previousRoute:(AVAudioSessionRouteDescription *)previousRoute {
    // Update JS about the changes.
    [self notifyDevicesChanged];

    dispatch_async(_workerQueue, ^{
        switch (reason) {
            case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
            case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
                // If the device list changed, reset our overrides.
                self->forceSpeaker = NO;
                self->forceEarpiece = NO;
                break;
            case AVAudioSessionRouteChangeReasonCategoryChange:
                // The category has changed. Check if it's the one we want and adjust as
                // needed.
                break;
            default:
                return;
        }

        // We don't want to touch the category when in default mode.
        // This is to play well with other components which could be integrated
        // into the final application.
        if (self->activeMode != kAudioModeDefault) {
            NSLog(@"[AudioMode] Route changed, reapplying RTCAudioSession config");
            RTCAudioSessionConfiguration *config = [self configForMode:self->activeMode];
            [self setConfig:config error:nil];
            if (self->forceSpeaker && !self->isSpeakerOn) {
                RTCAudioSession *session = AudioSession.rtcAudioSession;
                [session lockForConfiguration];
                [session overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:nil];
                [session unlockForConfiguration];
            }
        }
    });
}

- (void)audioSession:(RTCAudioSession *)audioSession didSetActive:(BOOL)active {
    NSLog(@"[AudioMode] Audio session didSetActive:%d", active);
}

#pragma mark - Helper methods

- (RTCAudioSessionConfiguration *)configForMode:(int) mode {
    if (mode != kAudioModeDefault && forceEarpiece) {
        return earpieceConfig;
    }

    switch (mode) {
        case kAudioModeAudioCall:
            return audioCallConfig;
        case kAudioModeDefault:
            return defaultConfig;
        case kAudioModeSilent:
            return silentCallConfig;
        case kAudioModeVideoCall:
            return videoCallConfig;
        default:
            return nil;
    }
}

// Here we convert input and output port types into a single type.
- (NSString *)portTypeToString:(AVAudioSessionPort) portType {
    if ([portType isEqualToString:AVAudioSessionPortHeadphones]
            || [portType isEqualToString:AVAudioSessionPortHeadsetMic]) {
        return kDeviceTypeHeadphones;
    } else if ([portType isEqualToString:AVAudioSessionPortBuiltInMic]
            || [portType isEqualToString:AVAudioSessionPortBuiltInReceiver]) {
        return kDeviceTypeEarpiece;
    } else if ([portType isEqualToString:AVAudioSessionPortBuiltInSpeaker]) {
        return kDeviceTypeSpeaker;
    } else if ([portType isEqualToString:AVAudioSessionPortBluetoothHFP]
            || [portType isEqualToString:AVAudioSessionPortBluetoothLE]
            || [portType isEqualToString:AVAudioSessionPortBluetoothA2DP]) {
        return kDeviceTypeBluetooth;
    } else {
        return kDeviceTypeUnknown;
    }
}

- (void)notifyDevicesChanged {
    dispatch_async(_workerQueue, ^{
        NSMutableArray *data = [[NSMutableArray alloc] init];
        // Here we use AVAudioSession because RTCAudioSession doesn't expose availableInputs.
        AVAudioSession *session = [AVAudioSession sharedInstance];
        NSString *currentPort = @"";
        AVAudioSessionRouteDescription *currentRoute = session.currentRoute;
        
        // Check what the current device is. Because the speaker is somewhat special, we need to
        // check for it first.
        if (currentRoute != nil) {
            AVAudioSessionPortDescription *output = currentRoute.outputs.firstObject;
            AVAudioSessionPortDescription *input = currentRoute.inputs.firstObject;
            if (output != nil && [output.portType isEqualToString:AVAudioSessionPortBuiltInSpeaker]) {
                currentPort = kDeviceTypeSpeaker;
                self->isSpeakerOn = YES;
            } else if (input != nil) {
                currentPort = input.UID;
                self->isSpeakerOn = NO;
                self->isEarpieceOn = [input.portType isEqualToString:AVAudioSessionPortBuiltInMic];
            }
        }
        
        BOOL headphonesAvailable = NO;
        for (AVAudioSessionPortDescription *portDesc in session.availableInputs) {
            if ([portDesc.portType isEqualToString:AVAudioSessionPortHeadsetMic] || [portDesc.portType isEqualToString:AVAudioSessionPortHeadphones]) {
                headphonesAvailable = YES;
                break;
            }
        }
        
        for (AVAudioSessionPortDescription *portDesc in session.availableInputs) {
            // Skip "Phone" if headphones are present.
            if (headphonesAvailable && [portDesc.portType isEqualToString:AVAudioSessionPortBuiltInMic]) {
                continue;
            }
            id deviceData
                = @{
                    @"type": [self portTypeToString:portDesc.portType],
                    @"name": portDesc.portName,
                    @"uid": portDesc.UID,
                    @"selected": [NSNumber numberWithBool:[portDesc.UID isEqualToString:currentPort]]
                };
            [data addObject:deviceData];
        }

        // We need to manually add the speaker because it will never show up in the
        // previous list, as it's not an input.
        [data addObject:
            @{ @"type": kDeviceTypeSpeaker,
               @"name": @"Speaker",
               @"uid": kDeviceTypeSpeaker,
               @"selected": [NSNumber numberWithBool:[kDeviceTypeSpeaker isEqualToString:currentPort]]
        }];
        
        [self sendEventWithName:kDevicesChanged body:data];
    });
}

@end
