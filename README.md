Sufra – Food & Grocery Ordering System (NETW704)
Sufra is a food and grocery ordering system inspired by delivery apps like Talabat. It is made of two separate Android apps written in Kotlin that share one Firebase project and one AWS S3 bucket:
Folder	App	Package	Who signs in
`SufraBuyer/`	Sufra (orange)	`com.example.sufrabuyer`	Buyers
`SufraSeller/`	Sufra Seller (purple)	`com.example.sufraseller`	Sellers
Features
Milestone 1 – Authentication and real-time profile
Email/password sign up and sign in with Firebase Authentication
Buyer and Seller roles (sellers also enter a shop name and shop category)
Role verification at login: the saved role is read from the database and the user is sent to the correct home screen
Profile saved right after registration in Firebase Realtime Database as JSON under `/users/{uid}`
Real-time profile screen: data loads automatically and updates instantly when it changes in Firebase
Edit profile: name, phone and address (plus shop details for sellers), saved with `updateChildren()`
Auto-login: a signed-in user skips the login screen
Password reset by email, input validation and clear error messages
Security rules: each user can only read and write their own data
Milestone 2 – Separate apps and AWS S3 profile photos
Two distinct Android Studio projects: a Buyer app and a Seller app
Role-specific sign in / sign up: the Buyer app only accepts buyers and the Seller app only accepts sellers; signing in with the other account type shows a "Wrong app" message
Profile photo upload and update in both apps, from the phone's gallery
Automatic compression: every photo is resized and compressed to under 0.5 MB before upload
AWS S3 storage: photos are uploaded to the team bucket using the team credentials (`putObject`), stored as `profile-photos/<uid>/<timestamp>.jpg`
Photo URL saved in Firebase (`photoUrl`, `photoUpdatedAt`)
Photo fetched from S3 with the credentials (`getObject`) and shown on the profile and home screens
Credentials kept out of GitHub: read from `local.properties` at build time
Milestone 3 – Shops, products, cart and orders
Buyer app
Home with tabs: TabLayout + ViewPager2 with two fragments, Shops and Cart (tap a tab or swipe)
Shops grid: all shops in a 2-column RecyclerView grid with the shop photo and name, filtered by category chips and a search box
Shop page: opened with `Intent.putExtra(...)`, shows the shop's products in a 2-column grid (photo, name, price, stock)
Cart: add to cart, increase / decrease quantity (never above the stock), remove, clear cart, total price
Place order: checks the buyer's phone and address, re-checks the latest stock and prices, then saves the order in Firebase
Products and shops update live: a change made by a seller appears in the Buyer app straight away
Seller app
Dashboard with tabs: Products, Add and Orders fragments in a ViewPager2, with live product / order counters
My products grid: the seller's items in a 2-column grid with photo and name; quick  −/+  stock buttons save instantly
Edit product: change name, description, price, quantity and photo, or delete the product
Add product: photo from the camera or the gallery, compressed under 0.5 MB, uploaded to S3, then saved in Firebase
Incoming orders: orders placed at this shop appear instantly, newest first
The shop's public details are published to `shops/{uid}` so buyers can see the shop
Tech stack
Part	Used
Language	Kotlin
UI	XML layouts, Material Components, View Binding
Lists and tabs	RecyclerView (GridLayoutManager), Fragments, TabLayout + ViewPager2
Authentication	Firebase Authentication (Email/Password)
Database	Firebase Realtime Database (JSON)
File storage	AWS S3 (AWS Android SDK)
Background work	Kotlin coroutines
Min SDK	API 24 (Android 7.0)
Database structure
```json
{
  "users": {
    "<uid>": {
      "uid": "<uid>", "role": "buyer | seller", "name": "Full Name", "email": "user@example.com",
      "phone": "01012345678", "address": "Cairo, Egypt",
      "shopName": "Only for sellers", "shopCategory": "Only for sellers",
      "photoUrl": "https://<bucket>.s3.<region>.amazonaws.com/profile-photos/<uid>/<time>.jpg",
      "photoUpdatedAt": 1760000000000, "createdAt": 1759050000000, "updatedAt": 1760000000000
    }
  },
  "shops": {
    "<sellerUid>": {
      "uid": "<sellerUid>", "shopName": "Burger Barn", "shopCategory": "Restaurant",
      "address": "Nasr City", "ownerName": "Seller Name", "photoUrl": "<S3 URL>", "updatedAt": 1762000000000
    }
  },
  "products": {
    "<sellerUid>": {
      "<productId>": {
        "id": "<productId>", "sellerId": "<sellerUid>", "name": "Cheese burger",
        "description": "Beef, cheddar, pickles", "price": 90, "quantity": 25,
        "imageUrl": "https://<bucket>.s3.<region>.amazonaws.com/product-photos/<sellerUid>/<time>.jpg",
        "createdAt": 1762000000000, "updatedAt": 1762000000000
      }
    }
  },
  "orders": {
    "<orderId>": {
      "id": "<orderId>", "buyerId": "<buyerUid>", "buyerName": "Buyer Name",
      "buyerPhone": "01012345678", "address": "Cairo, Egypt",
      "sellerId": "<sellerUid>", "shopName": "Burger Barn",
      "items": {
        "<productId>": { "productId": "<productId>", "name": "Cheese burger", "price": 90, "quantity": 2, "imageUrl": "<S3 URL>" }
      },
      "itemCount": 2, "totalPrice": 180, "status": "pending", "createdAt": 1762000000000
    }
  }
}
```
Photos are stored in the S3 bucket under `profile-photos/<uid>/` and `product-photos/<sellerUid>/`. Firebase only keeps their URLs.
Security rules
`users`: each user can only read and write their own profile
`shops` and `products`: every signed-in user can read them; only the seller who owns them can write
`orders`: a buyer can create an order for themselves; a seller can only list the orders of their own shop
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    },
    "shops": {
      ".read": "auth != null",
      "$sellerId": {
        ".write": "auth != null && auth.uid === $sellerId"
      }
    },
    "products": {
      ".read": "auth != null",
      "$sellerId": {
        ".write": "auth != null && auth.uid === $sellerId"
      }
    },
    "orders": {
      ".read": "auth != null && ((query.orderByChild == 'sellerId' && query.equalTo == auth.uid) || (query.orderByChild == 'buyerId' && query.equalTo == auth.uid))",
      ".indexOn": ["sellerId", "buyerId"],
      "$orderId": {
        ".read": "auth != null && (data.child('buyerId').val() === auth.uid || data.child('sellerId').val() === auth.uid)",
        ".write": "auth != null && ((!data.exists() && newData.child('buyerId').val() === auth.uid) || (data.exists() && (data.child('buyerId').val() === auth.uid || data.child('sellerId').val() === auth.uid)))"
      }
    }
  }
}
```
Project structure
Shared by both apps (`app/src/main/java/com/example/sufrabuyer/` or `sufraseller/`):
```
├── AppConfig.kt           # Which role this app is for and its home screen
├── SplashActivity.kt      # Launcher: auto-login and role check
├── LoginActivity.kt       # Sign in + role verification + password reset
├── SignUpActivity.kt      # Registration + saving the profile to /users/{uid}
├── ProfileActivity.kt     # Real-time profile: view, edit, photo upload, log out
├── HomePagerAdapter.kt    # Which fragment each tab of the home screen shows
├── User.kt, Shop.kt, Product.kt, Order.kt   # Data models mapped from the Firebase JSON
├── FirebaseRefs.kt        # Shared Firebase references (users, shops, products, orders)
├── S3Manager.kt           # AWS S3 upload, download and delete
├── ImageLoader.kt         # Shows S3 photos inside RecyclerView cards
├── ImageUtils.kt          # Photo compression (under 0.5 MB)
└── Utils.kt               # Validation, prices, navigation and UI helpers
```
Buyer app only:
```
├── BuyerHomeActivity.kt   # Header + tabs (Shops, Cart)
├── ShopsFragment.kt       # Shops grid with category chips and search
├── ShopAdapter.kt         # RecyclerView adapter for shop cards
├── ShopActivity.kt        # One shop (data received with getStringExtra)
├── ProductsFragment.kt    # Products grid of a shop, add to cart
├── ProductAdapter.kt      # RecyclerView adapter for product cards
├── CartManager.kt         # The cart (one shop, quantities limited by stock)
├── CartFragment.kt        # Cart tab: items, total, clear, place order
└── CartAdapter.kt         # RecyclerView adapter for cart rows
```
Seller app only:
```
├── SellerHomeActivity.kt      # Header + tabs (Products, Add, Orders), publishes shops/{uid}
├── SellerProductsFragment.kt  # My products grid with quick stock buttons
├── SellerProductAdapter.kt    # RecyclerView adapter for the seller's product cards
├── EditProductActivity.kt     # Edit or delete one product (id received with getStringExtra)
├── AddProductFragment.kt      # New product form, photo -> S3 -> Firebase
├── PhotoPicker.kt             # Camera or gallery photo (FileProvider for the camera)
├── SellerOrdersFragment.kt    # Incoming orders (Firebase query on sellerId)
└── SellerOrderAdapter.kt      # RecyclerView adapter for order cards
```
How to run
Open `SufraBuyer` or `SufraSeller` in Android Studio.
Put the Firebase `google-services.json` in that project's `app/` folder.
Add the AWS team credentials at the end of that project's `local.properties`:
```properties
   aws.accessKey=...
   aws.secretKey=...
   aws.sessionToken=
   aws.region=...
   aws.bucket=...
   ```
`local.properties` is ignored by Git, so the keys are never uploaded.
Sync Gradle and run on an emulator or phone with Google Play services.
Roadmap
Milestone 4: order states (accept, prepare, deliver), stock updates after an order, notifications and VoIP calls

## Team
 
- Farida Hossam
- Fayrouz muhammad
- yassin salah
- Hassan Mohamed
