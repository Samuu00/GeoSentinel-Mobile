// Punto d'ingresso dell'app iOS in SwiftUI. Crea la finestra con la vista che ospita l'interfaccia Compose del
// modulo condiviso e collega l'AppDelegate, che gestisce Firebase, le notifiche push e il login con Google.
import SwiftUI
import GoogleSignIn

/// Struttura principale dell'app iOS, indicata dall'attributo @main.
@main
struct iOSApp: App {
    /// AppDelegate di UIKit: Firebase e APNs passano dai suoi callback, che SwiftUI non espone.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    /// Scena unica dell'app: una finestra con l'interfaccia Compose.
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Ritorno nell'app dopo il login con Google, tramite lo schema URL del client iOS.
                .onOpenURL { url in GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
