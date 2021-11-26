//
//  AudioSession.h
//  Audio
//
//  Created by KULLANICI on 26.11.2021.
//  Copyright © 2021 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@class AVAudioSession;

@interface AudioSession : NSObject

+ (void)activateWithAudioSession:(AVAudioSession *)session;
+ (void)deactivateWithAudioSession:(AVAudioSession *)session;

@end
