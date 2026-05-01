# 🃏 Spotlight: Real-Time Multiplayer Card Game

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Firebase](https://img.shields.io/badge/Database-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Hilt](https://img.shields.io/badge/Architecture-Dagger%2FHilt-02569B?style=for-the-badge&logo=android&logoColor=white)

Spotlight is a peer-to-peer, real-time party card game for Android. Built with a focus on **distributed state management** and **fault-tolerant architecture**, the application synchronizes game phases across multiple remote clients with millisecond latency.

<div align="center">
  <img width="1080" height="701" alt="SPOTLIGHT-DEMO" src="https://github.com/user-attachments/assets/69a8ebdd-127e-4de4-a619-ddbb0f4f61c1" />
</div>

---

## 🏗️ Engineering Highlights

This project was built to tackle the inherent complexities of real-time mobile multiplayer networking, focusing heavily on race conditions, state synchronization, and architecture design.

### 1. Distributed Fault Tolerance (Host Migration)
To eliminate single points of failure, the game implements a deterministic host-election algorithm. If the room creator (Host) loses network connection or terminates the app, the remaining clients detect the Firebase `onDisconnect` event, automatically sort remaining players by join-timestamp, and promote the oldest player to Host. This allows the game state to progress seamlessly without server-side intervention.

### 2. Real-Time State Synchronization
Utilized **Firebase Realtime Database** coupled with Android's **LiveData** to create a reactive UI pipeline. The game leverages transactional updates and strict database security rules to prevent race conditions when multiple players submit answers or cast votes simultaneously. 

### 3. Enterprise-Grade Dependency Injection
Migrated from manual `ViewModelFactory` boilerplate to **Dagger/Hilt**. By defining `@Singleton` repositories and scoping dependencies to the Activity lifecycle via `@HiltViewModel`, the architecture ensures clean separation of concerns, testability, and memory efficiency across the application.

### 4. Frictionless Onboarding (Deep Linking)
Implemented Custom URL Schemes (`spotlight://join?code=...`) integrated directly into the Android Manifest. Players can share their lobby via SMS or Discord, allowing recipients to bypass the main menu and automatically inject the room code into the session.

### 5. Client-Side Input Sanitization
Developed an aggressive, local profanity-filtering algorithm that normalizes "leet-speak" (e.g., converting "4" to "a", "0" to "o") and strips special characters before evaluating strings against a blocked dictionary, ensuring a safe environment prior to Firebase payload submission.

---

## 🛠️ Tech Stack & Architecture

Spotlight adheres strictly to the **MVVM (Model-View-ViewModel)** architectural pattern.

* **Language:** Java 8+
* **UI/View:** XML Layouts with ViewBinding
* **Architecture:** Android Architecture Components (ViewModel, LiveData)
* **Dependency Injection:** Dagger/Hilt
* **Backend Backend:** Firebase Realtime Database
* **Authentication:** Firebase Anonymous Auth

---

## 🚀 Getting Started

To run this project locally, you will need Android Studio and your own Firebase environment.

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/tim-perez/Spotlight.git](https://github.com/tim-perez/Spotlight.git)
