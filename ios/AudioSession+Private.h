//
//  AudioSession+Private.h
//  Audio
//
//  Created by KULLANICI on 26.11.2021.
//  Copyright © 2021 Facebook. All rights reserved.
//

#import "AudioSession.h"
#import <WebRTC/WebRTC.h>

@interface AudioSession (Private)

+ (RTCAudioSession *)rtcAudioSession;

@end
