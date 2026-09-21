# DigiCash Android

DigiCash is an Android-based **offline peer-to-peer digital cash application** that enables users to create, sign, store, and synchronize digital transactions securely.

The application uses **QR-based device-to-device communication** for offline transactions and synchronizes pending transactions with a Spring Boot backend when connectivity is available.

## Features

* Offline peer-to-peer wallet transactions
* Dynamic QR-based transaction exchange
* RSA key-pair based device identity
* SHA256withRSA transaction signing
* Local transaction verification
* Offline wallet and transaction storage
* Automatic pending transaction synchronization
* Secure backend synchronization
* Transaction status tracking

## Tech Stack

**Java • Android • Room • Retrofit • WorkManager • RSA • SHA-256**

## Architecture

```text
Android Device A
      │
      │ QR Transaction
      ▼
Android Device B
      │
      │ Pending Transaction
      ▼
 WorkManager
      │
      │ REST API
      ▼
Spring Boot Backend
      │
      ▼
    MySQL
```

## Backend

The Android application communicates with the deployed DigiCash backend for transaction synchronization.

**Backend Repository:**
https://github.com/ZaidTech87/DigiCash_Backend

**Live Backend:**
https://digicash-backend.onrender.com

## APK

Download and install the latest DigiCash Android APK:

**[Download DigiCash APK](./apk/DigiCash.apk)**

> The APK is provided for demonstration and testing purposes.

## Project Structure

```text
DigiCash-Android/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           └── res/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Security

DigiCash uses:

* Android KeyStore
* RSA public/private key pairs
* SHA256withRSA digital signatures
* SHA-256 nonce generation
* Local signature verification
* Server-side transaction validation

## Project Status

**Completed Prototype / Demonstration Project**

The project demonstrates offline digital cash transactions, cryptographic verification, local persistence, and backend synchronization.

## Related Repository

**DigiCash Backend:**
https://github.com/ZaidTech87/DigiCash_Backend
