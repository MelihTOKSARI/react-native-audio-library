//
//  AudioSession.m
//  Audio
//
//  Created by KULLANICI on 26.11.2021.
//  Copyright © 2021 Facebook. All rights reserved.
//

#import "AudioSession.h"
#import "AudioSession+Private.h"

@implementation AudioSession

+ (RTCAudioSession *)rtcAudioSession {
    return [RTCAudioSession sharedInstance];
}

+ (void)activateWithAudioSession:(AVAudioSession *)session {
    [self.rtcAudioSession audioSessionDidActivate:session];
}

+ (void)deactivateWithAudioSession:(AVAudioSession *)session {
    [self.rtcAudioSession audioSessionDidDeactivate:session];
}

@end
