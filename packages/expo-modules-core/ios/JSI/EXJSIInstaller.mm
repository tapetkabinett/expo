// Copyright 2018-present 650 Industries. All rights reserved.

#import <ExpoModulesCore/EXJSIInstaller.h>
#import <ExpoModulesCore/ExpoModulesProxySpec.h>
#import <ExpoModulesCore/ExpoModulesHostObject.h>
#import <ExpoModulesCore/Swift.h>

using namespace facebook;
using namespace react;

namespace expo {

void installRuntimeObjects(jsi::Runtime &runtime, std::shared_ptr<CallInvoker> callInvoker, EXNativeModulesProxy *nativeModulesProxy)
{
  auto expoModulesProxyModule = std::make_shared<ExpoModulesProxySpec>(callInvoker, nativeModulesProxy);

  runtime
    .global()
    .setProperty(runtime, "ExpoModulesProxy", jsi::Object::createFromHostObject(runtime, expoModulesProxyModule));
}

} // namespace expo

@implementation EXJavaScriptRuntimeManager

+ (void)installExpoModulesToRuntime:(nonnull EXJavaScriptRuntime *)runtime withSwiftInterop:(nonnull SwiftInteropBridge *)swiftInterop
{
  std::shared_ptr<expo::ExpoModulesHostObject> hostObjectPtr = std::make_shared<expo::ExpoModulesHostObject>(swiftInterop);
  EXJavaScriptObject *global = [runtime global];

  global[@"ExpoModules"] = [runtime createHostObject:hostObjectPtr];
}

@end
