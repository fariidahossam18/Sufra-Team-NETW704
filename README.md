# Sufra – NETW704 Team Project

Two separate Android apps (Kotlin, XML + View Binding) that share one Firebase project and one AWS S3 bucket.

| Folder | App | Package | Who signs in |
| --- | --- | --- | --- |
| `SufraBuyer/` | **Sufra** (orange) | `com.example.sufrabuyer` | Buyers only |
| `SufraSeller/` | **Sufra Seller** (purple) | `com.example.sufraseller` | Sellers only |

## Milestone 2 features

- Two distinct, runnable Android Studio projects with **role-specific Sign In / Sign Up**. A seller can't sign in to the Buyer app (and the opposite); they're told to use the other app.
- **Profile photo** in both apps: pick from the gallery → the app compresses it to **under 0.5 MB** → on *Save changes* it is uploaded to **AWS S3** with the team credentials → the S3 URL is saved in Firebase as `photoUrl`.
- When the profile opens, the photo is **downloaded from S3 using the credentials** (from the URL saved in Firebase) and shown. The home screen avatar shows it too.
- Every upload gets a new file name (`profile-photos/<uid>/<timestamp>.jpg`), so the bucket keeps each version with its upload time.

## Firebase structure

```
users/<uid>: { uid, role ("buyer"|"seller"), name, email, phone, address,
               shopName, shopCategory (sellers), photoUrl, photoUpdatedAt, createdAt, updatedAt }
```

Rules: each user can read/write only `users/<their uid>`.

## How to run

1. Open `SufraBuyer` or `SufraSeller` in Android Studio.
2. Put the Firebase `google-services.json` in that project's `app/` folder.
3. Add the AWS team credentials at the end of that project's `local.properties` (see `local.properties.example`). This file is ignored by Git, so the keys are never uploaded.
4. Sync Gradle and run on an emulator/phone with Google Play services.
