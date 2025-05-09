# Rakshak – AI Powered Women Safety System

**Rakshak** is a Kotlin-based Android application aimed at ensuring women’s safety using AI-powered emergency response mechanisms. It empowers users to trigger real-time SOS alerts through voice commands, eye gestures, health vitals, and button press, ensuring immediate help even in low-network areas.

---

## 🛡️ About the Project

With rising concerns around women's safety, Rakshak offers a multi-layered emergency system that includes:

- **Voice-Activated SOS**
- **Iris (Eye Blink) Detection**
- **Health Monitoring Alerts**
- **Real-time GPS Tracking**
- **Offline SMS-based Backup Alerts**

---

## 🧠 Features & Functionality

- **SOS Button**: One-tap distress alert with live location.
- **Voice Activation**: Detects predefined keywords like “Help” or “SOS”.
- **Eye Gesture Detection**: Triggers alert on three eye blinks (Google ML Kit).
- **Health-Based Alerts**: Sends SOS based on abnormal vitals (heart rate/stress).
- **Real-Time Location Sharing**: Sends GPS coordinates to trusted contacts.
- **Offline Mode Support**: Falls back to SMS if the internet is unavailable.
- **Push Notifications**: Emergency messages sent via FCM to emergency responders.

---

## 🔧 Tech Stack

### 👩‍💻 Android App (Main - for Users)
- **Language**: Kotlin
- **Platform**: Android Studio
- **UI**: XML
- **AI Models**:
  - Google ML Kit (Iris Detection)
  - Google Speech-to-Text
  - IBM Granite LLM (contextual understanding)

### ☁ Backend & Database
- **FastAPI / Node.js** (for managing backend requests)
- **MongoDB** (for secure emergency data logging)
- **Firebase Cloud Messaging (FCM)** – Real-time push notifications

### 📡 Services
- **Google Maps API + Geolocator**
- **Twilio / WhatsApp Chatbot (Rakshak Bot)**
- **IBM Cloud Storage** – Media and logs storage

---

## 🧬 Application Architecture

- **3-Tier Architecture**
  - **Presentation Layer**: Android App (User Interface)
  - **Logic Layer**: FastAPI/Flask & AI Models
  - **Data Layer**: Firebase, MongoDB, IBM Cloud

---

## 🚨 User Types

1. **Women Users** – Send emergency alerts.
2. **Emergency Contacts** – Receive alerts instantly.
3. **Police / Nurses / Hospitals** – Responders via dedicated app or bot.
4. **Admin Panel** – (Optional) for law enforcement or NGO oversight.

---

## 📲 Getting Started

### Prerequisites

- Android Studio Flamingo or later
- Kotlin 1.8+
- Google ML Kit SDK
- Firebase Project with FCM enabled
- MongoDB backend hosted or local instance


