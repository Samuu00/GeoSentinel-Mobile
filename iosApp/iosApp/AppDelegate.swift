// AppDelegate dell'app iOS: avvia il codice Kotlin condiviso, configura Firebase Cloud Messaging sopra APNs per le
// notifiche push e prepara il login con Google. GoogleService-Info.plist non è nel repository: senza, l'app parte
// lo stesso e le push restano spente, come su Android senza google-services.json.
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import Shared

/// Riceve gli eventi di sistema di UIKit (avvio, token APNs, notifiche) e li inoltra ai bridge Kotlin
/// PushBridge e GoogleSignInBridge.
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    /// Avvio dell'app: inizializza il codice condiviso, imposta il delegate delle notifiche, prepara il login con
    /// Google e, se c'è la configurazione di Firebase, avvia FCM e la registrazione ad APNs.
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Koin si avvia prima di tutto: il tocco su una notifica ad app chiusa arriva prima che l'interfaccia sia pronta.
        MainViewControllerKt.startShared()
        UNUserNotificationCenter.current().delegate = self
        startGoogleSignIn()
        guard Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return true }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        // A fine sessione il token viene invalidato, così le push dell'utente uscito non arrivano più a questo telefono,
        // e se ne chiede uno nuovo.
        PushBridge.shared.setTokenRevoker {
            Messaging.messaging().deleteToken { _ in
                Messaging.messaging().token { token, _ in
                    if let token { PushBridge.shared.onNewToken(token: token) }
                }
            }
        }
        // Il permesso delle notifiche lo chiede l'app quando serve: registrarsi ad APNs non lo richiede.
        application.registerForRemoteNotifications()
        return true
    }

    /// Prepara il login con Google. L'SDK legge GIDClientID dall'Info.plist e restituisce l'ID token, che il codice
    /// Kotlin invia a POST /auth/google. Senza client ID il launcher non viene registrato e il pulsante non compare.
    private func startGoogleSignIn() {
        guard let clientId = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String, !clientId.isEmpty else { return }
        GoogleSignInBridge.shared.setLauncher { callback in
            DispatchQueue.main.async {
                guard let presenter = Self.topViewController() else {
                    callback.onFailed()
                    return
                }
                GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                    if let token = result?.user.idToken?.tokenString {
                        callback.onToken(idToken: token)
                    } else if let error = error as NSError?, error.code == GIDSignInError.canceled.rawValue {
                        callback.onCancelled()
                    } else {
                        callback.onFailed()
                    }
                }
            }
        }
    }

    /// Restituisce il view controller in cima alla finestra principale, così il foglio di Google si presenta sopra
    /// quello che è già aperto (per esempio un dialogo).
    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    /// Passa a Firebase il token APNs del dispositivo, necessario per ricevere le push FCM.
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    /// Riceve il token FCM a ogni avvio e quando cambia; l'app lo registra sul backend quando l'utente è autenticato.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushBridge.shared.onNewToken(token: token)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        /// Push ricevuta con l'app in primo piano: lo stato dell'SOS si rilegge subito e la notifica viene mostrata comunque.
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        PushBridge.shared.onMessage(userInfo: notification.request.content.userInfo)
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        /// Tocco sulla notifica, anche ad app chiusa: l'app apre l'allerta indicata.
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        PushBridge.shared.onNotificationOpened(userInfo: response.notification.request.content.userInfo)
        completionHandler()
    }
}
