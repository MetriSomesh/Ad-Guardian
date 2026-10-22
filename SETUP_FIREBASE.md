# Setting up Firebase (free tier) for Order Guardian

Until this is done, both apps run in **on-device test mode**: the guardian
creates and stores approval requests on the grandfather's phone itself, and
they can only be decided in the guardian app on that same phone. Nothing is
sent anywhere. That mode exists so the family can test detection and
ejecting before any account is created — it is not a substitute for the
live setup below.

Everything here uses Firebase's **Spark (free) plan**. No card, no paid
tier, no Cloud Functions (which would need the Blaze plan), no VPS.

## What the live setup gives you

- The guardian phone writes each approval request to Cloud Firestore.
- The father's Approver app reads the same collection in real time and his
  Approve/Deny writes the decision back.
- The guardian phone hears the decision through its Firestore listener and,
  when an FCM token has been registered, a push wake-up.
- No component in this design can approve a request from the grandfather's
  phone in live mode: the guardian app's decision buttons only exist in
  on-device test mode.

## Steps

1. **Create the project.** Go to <https://console.firebase.google.com>,
   sign in with a Google account the family controls, and create a project
   (any name, e.g. `order-guardian-family`). Google Analytics is optional —
   you can decline it.
2. **Add the Android apps.** In the project, add two Android apps:
   - Guardian: package name `com.rockstar.orderguardian`
   - Approver: package name `com.rockstar.orderguardian.approver`

   Each one gives you a `google-services.json` download. Put each file in
   its own folder in this project — they are **not** interchangeable:
   - Guardian file → `guardian/google-services.json`
   - Approver file → `approver/google-services.json`
3. **Turn on Firestore.** In the console: Build → Firestore Database →
   Create database (production mode, any region — `asia-south1` is closest
   to India). The apps create the `approvalRequests` collection themselves
   on first use.
4. **Turn on Authentication.** Build → Authentication → Get started →
   enable the **Anonymous** sign-in provider. Both apps sign in
   anonymously; the rules below use that to keep the database closed to
   anyone who is not running one of the two apps.
5. **Set the security rules.** Build → Firestore Database → Rules — paste
   exactly this, then Publish:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /approvalRequests/{requestId} {
         // Single-family trust model: only signed-in instances of the two
         // family apps can read or write requests. Any finer split (the
         // approver may decide, the guardian may only create/consume) needs
         // per-user claims, which means named accounts for father and
         // grandfather — see the note under these steps before changing this.
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

   Honest note about this rule: with anonymous auth, the database cannot
   tell the father's phone from the grandfather's. The *apps* enforce the
   split (the guardian build in live mode has no working decision buttons;
   decisions are only made in the Approver app), and the grandfather is
   not a technical adversary in this family's threat model — the plan's
   bypass analysis says the same. If that changes, move both phones to
   named Firebase Auth accounts and tighten the rule to check the
   approver's UID before allowing status changes to APPROVED/DENIED.
6. **Rebuild both apps** so the configuration is picked up (each app's
   build reads whether its `google-services.json` exists and switches
   `FIREBASE_CONFIGURED` on):

   ```
   ./gradlew :guardian:assembleDebug :approver:assembleDebug
   ```

   The guardian dashboard's first line will now say "Live mode", and the
   Approver's "Not connected" card will be gone.
7. **Reinstall both APKs** (guardian on the grandfather's phone, approver
   on the father's phone) and re-check the guardian's setup list — an
   Android update of the app keeps accessibility/overlay grants, but a
   fresh install does not.
8. **Notifications.** FCM is free and needs no extra console step for
   Firestore-driven updates; the apps also keep a live Firestore listener
   while they run, so decisions arrive even if a push is delayed. For push
   to reach the father's phone reliably, set the Approver app's battery to
   Unrestricted on his phone as well.

## What stays free, and the limits

Spark plan quotas (Firestore reads/writes per day, stored data) are far
above what one family's order requests can generate. There is no
per-message charge anywhere in this design because there is no WhatsApp
Cloud API in it — that channel was evaluated and deliberately left out of
the free build; the Approver app is the approval channel.
