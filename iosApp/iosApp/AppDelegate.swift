import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
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
        guard Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return true }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        // Il permesso lo chiede l'app quando serve (RequestNotificationPermission): registrarsi ad APNs non lo richiede
        application.registerForRemoteNotifications()
        return true
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
