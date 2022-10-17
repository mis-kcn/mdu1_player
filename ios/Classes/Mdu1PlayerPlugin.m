#import "Mdu1PlayerPlugin.h"
#if __has_include(<mdu1_player/mdu1_player-Swift.h>)
#import <mdu1_player/mdu1_player-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "mdu1_player-Swift.h"
#endif

@implementation Mdu1PlayerPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftMdu1PlayerPlugin registerWithRegistrar:registrar];
}
@end
