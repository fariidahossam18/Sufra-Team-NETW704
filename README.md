# Sufra – Food & Grocery Ordering System (NETW704)
 
Sufra is a food and grocery ordering system inspired by delivery apps like Talabat. It is made of **two separate Android apps** written in Kotlin that share one Firebase project and one AWS S3 bucket:
 
| Folder | App | Package | Who signs in |
| --- | --- | --- | --- |
| `SufraBuyer/` | **Sufra** (orange) | `com.example.sufrabuyer` | Buyers |
| `SufraSeller/` | **Sufra Seller** (purple) | `com.example.sufraseller` | Sellers |
 
## Features
 
### Milestone 1 – Authentication and real-time profile
 
- **Email/password sign up and sign in** with Firebase Authentication
- **Buyer and Seller roles** (sellers also enter a shop name and shop category)
- **Role verification at login**: the saved role is read from the database and the user is sent to the correct home screen
- **Profile saved right after registration** in Firebase Realtime Database as JSON under `/users/{uid}`
- **Real-time profile screen**: data loads automatically and updates instantly when it changes in Firebase
- **Edit profile**: name, phone and address (plus shop details for sellers), saved with `updateChildren()`
- **Auto-login**: a signed-in user skips the login screen
- **Password reset** by email, input validation and clear error messages
- **Security rules**: each user can only read and write their own data
### Milestone 2 – Separate apps and AWS S3 profile photos
 
- **Two distinct Android Studio projects**: a Buyer app and a Seller app
- **Role-specific sign in / sign up**: the Buyer app only accepts buyers and the Seller app only accepts sellers; signing in with the other account type shows a "Wrong app" message
- **Profile photo upload and update** in both apps, from the phone's gallery
- **Automatic compression**: every photo is resized and compressed to **under 0.5 MB** before upload
- **AWS S3 storage**: photos are uploaded to the team bucket using the team credentials (`putObject`), stored as `profile-photos/<uid>/<timestamp>.jpg`
- **Photo URL saved in Firebase** (`photoUrl`, `photoUpdatedAt`)
- **Photo fetched from S3** with the credentials (`getObject`) and shown on the profile and home screens
- **Credentials kept out of GitHub**: read from `local.properties` at build time
## Tech stack
 
| Part | Used |
| --- | --- |
| Language | Kotlin |
| UI | XML layouts, Material Components, View Binding |
| Authentication | Firebase Authentication (Email/Password) |
| Database | Firebase Realtime Database (JSON) |
| File storage | AWS S3 (AWS Android SDK) |
| Background work | Kotlin coroutines |
| Min SDK | API 24 (Android 7.0) |
 
## Database structure
 
```json
{
  "users": {
    "<uid>": {
      "uid": "<uid>",
      "role": "buyer | seller",
      "name": "Full Name",
      "email": "user@example.com",
      "phone": "01012345678",
      "address": "Cairo, Egypt",
      "shopName": "Only for sellers",
      "shopCategory": "Only for sellers",
      "photoUrl": "https://<bucket>.s3.<region>.amazonaws.com/profile-photos/<uid>/<timestamp>.jpg",
      "photoUpdatedAt": 1760000000000,
      "createdAt": 1759050000000,
      "updatedAt": 1760000000000
    }
  }
}
```
 
## Security rules
 
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    }
  }
}
```
 
## Project structure (same in both apps)
 
```
app/src/main/java/com/example/sufrabuyer/   (or sufraseller)
├── AppConfig.kt           # Which role this app is for and its home screen
├── SplashActivity.kt      # Launcher: auto-login and role check
├── LoginActivity.kt       # Sign in + role verification + password reset
├── SignUpActivity.kt      # Registration + saving the profile to /users/{uid}
├── BuyerHomeActivity.kt   # Buyer home (Buyer app only)
├── SellerHomeActivity.kt  # Seller dashboard (Seller app only)
├── ProfileActivity.kt     # Real-time profile: view, edit, photo upload, log out
├── S3Manager.kt           # AWS S3 upload and download
├── ImageUtils.kt          # Photo compression (under 0.5 MB)
├── User.kt                # Data model mapped from the Firebase JSON
├── FirebaseRefs.kt        # Shared Firebase references
└── Utils.kt               # Validation, navigation and UI helpers
```
 
## How to run
 
1. Open `SufraBuyer` or `SufraSeller` in Android Studio.
2. Put the Firebase `google-services.json` in that project's `app/` folder.
3. Add the AWS team credentials at the end of that project's `local.properties`:
```properties
   aws.accessKey=...
   aws.secretKey=...
   aws.sessionToken=
   aws.region=...
   aws.bucket=...
```
   `local.properties` is ignored by Git, so the keys are never uploaded.
4. Sync Gradle and run on an emulator or phone with Google Play services.
 
## Roadmap
 
- **Milestone 3:** shops, products, cart and orders
- **Milestone 4:** checkout, notifications and VoIP calls
## Team
 
- Farida Hossam
- Fayrouz muhammad
- yassin salah
