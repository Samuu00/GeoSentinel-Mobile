// Ponte tra SwiftUI e Compose Multiplatform: incapsula il view controller creato dal modulo Kotlin condiviso
// (MainViewController) in una vista SwiftUI che occupa tutto lo schermo.
import UIKit
import SwiftUI
import Shared

/// Adattatore che mostra in SwiftUI il UIViewController di Compose prodotto dal framework Shared.
struct ComposeView: UIViewControllerRepresentable {
    /// Crea il view controller di Compose, una sola volta per la vita della vista.
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    /// Non serve aggiornare nulla: lo stato dell'interfaccia lo gestisce Compose.
    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

/// Vista radice dell'app: l'interfaccia Compose disegnata fino ai bordi dello schermo. I margini della safe area
/// (tacca, Dynamic Island, indicatore Home) li applica Compose con WindowInsets.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}