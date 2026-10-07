import SwiftUI

@main
struct iOSApp: App {
    // Push (M7.1): Firebase e APNs passano dall'AppDelegate
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
