import SwiftUI
import GoogleSignIn

@main
struct iOSApp: App {
    // Push (M7.1): Firebase e APNs passano dall'AppDelegate
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // ritorno dal login con Google (schema URL del client iOS)
                .onOpenURL { url in GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
