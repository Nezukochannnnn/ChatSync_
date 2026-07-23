# ChatSync 💬

ChatSync is a modern, feature-packed real-time Android chat application built with Kotlin, Firebase Authentication, Cloud Firestore, and Firebase Storage. It features a modern dark purple gradient aesthetic, sleek chat bubbles, profile photo uploads, image sharing in chat, real-time message syncing, and automated login persistence.

---

## ✨ Features

- **🔐 User Authentication**: Secure Sign In & Sign Up using Firebase Auth.
- **⚡ Real-time Messaging**: Instant message delivery and real-time Firestore synchronization.
- **🖼️ Image Attachment**: Share photos directly in the chat with rounded visual cards.
- **👤 Profile Management**: Pick and upload custom profile pictures saved to Firebase Storage.
- **🎨 Modern Dark Aesthetic**: Custom dark theme with purple/violet gradients, styled Material input fields, and custom avatars.
- **🕒 Timestamps & Senders**: Clear timestamp formatting and sender identification for incoming messages.
- **📲 Responsive Android UI**: Built with Material Components, ConstraintLayout, and DataBinding.
- **🔄 Auto-Login Support**: Automatically restores active user sessions on app start.

---

## 🛠️ Built With

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Architecture**: XML DataBinding, ViewBinding, ConstraintLayout, Material3
- **Backend / Cloud**:
  - [Firebase Authentication](https://firebase.google.com/products/auth)
  - [Cloud Firestore](https://firebase.google.com/products/firestore)
  - [Firebase Storage](https://firebase.google.com/products/storage)
- **Image Loading**: [Picasso](https://github.com/square/picasso), [CircularImageView](https://github.com/lopspower/CircularImageView)
- **Target SDK**: Android 36 (Android 14+)

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug / Jellyfish or newer recommended)
- JDK 11 or higher
- Android SDK 36 installed
- Firebase project setup (`google-services.json` included in `/app`)

### Building the App

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Nezukochannnnn/ChatSync_.git
   cd ChatSync_
   ```

2. **Open in Android Studio**:
   Open Android Studio and choose **Open an Existing Project**, selecting the `ChatSync_` directory.

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📸 Screen Overview

- **Authentication Screen**: Dual-panel ViewFlipper interface for seamless Sign-In, Sign-Up, and Profile setup.
- **Global Chat Screen**: Material Toolbar with online status, custom message bubbles, attach button, and real-time RecyclerView scroll.

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
