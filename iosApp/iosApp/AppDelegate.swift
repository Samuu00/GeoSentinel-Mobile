import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import Shared

// Push (M7.1): Firebase Cloud Messaging sopra APNs. GoogleService-Info.plist non è nel repo: senza, l'app parte lo stesso
// e le push restano spente (come su Android senza google-services.json).
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Koin prima di tutto: il tocco su una notifica ad app chiusa arriva prima che la UI sia pronta
        MainViewControllerKt.startShared()
        UNUserNotificationCenter.current().delegate = self
        startGoogleSignIn()
        guard Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return true }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        // A fine sessione il token si invalida (le push dell'utente uscito non arrivano più qui) e se ne chiede uno nuovo
        PushBridge.shared.setTokenRevoker {
            Messaging.messaging().deleteToken { _ in
                Messaging.messaging().token { token, _ in
                    if let token { PushBridge.shared.onNewToken(token: token) }
                }
            }
        }
        // Il permesso lo chiede l'app quando serve (RequestNotificationPermission): registrarsi ad APNs non lo richiede
        application.registerForRemoteNotifications()
        return true
    }

    // Login con Google (ADR-0024 del backend): l'SDK legge GIDClientID dall'Info.plist e restituisce l'ID token, che
    // Kotlin manda a POST /auth/google. Senza client ID il launcher non c'è e il pulsante non compare.
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

    // Il foglio di Google si presenta sopra quello che è già aperto (es. un dialogo)
    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // Token FCM, a ogni avvio e quando cambia: lo registra App da loggati
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushBridge.shared.onNewToken(token: token)
    }

    // Push con l'app in primo piano: lo stato dell'SOS si rilegge subito e la notifica si mostra lo stesso
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        PushBridge.shared.onMessage(userInfo: notification.request.content.userInfo)
        completionHandler([.banner, .list, .sound])
    }

    // Tocco sulla notifica, anche ad app chiusa: App apre l'allerta
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        PushBridge.shared.onNotificationOpened(userInfo: response.notification.request.content.userInfo)
        completionHandler()
    }
}
