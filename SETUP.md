# AlertUp — Setup Guide

## Prerequisites

- Android Studio installed
- Supabase CLI: `npm install -g supabase`
- Firebase project already created (google-services.json is in place)

---

## 1. Firebase Setup

### Get Service Account Key

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select project **alertup-cf5ce**
3. Click gear icon → **Project Settings**
4. Go to **Service accounts** tab
5. Click **Generate new private key** → download the JSON file
6. Keep this file safe — you'll paste its contents into Supabase

---

## 2. Supabase Database Setup

### Run the schema

1. Go to [Supabase Dashboard](https://supabase.com/dashboard/project/djrxojuamnwhqajubzab)
2. SQL Editor → New query
3. Paste and run `supabase/schema.sql` (creates all tables)
4. Paste and run `supabase/policies.sql` (enables RLS)
5. Paste and run `supabase/update.sql` (adds device_tokens table)

---

## 3. Supabase Edge Function Setup

### Login and link

```bash
supabase login
supabase link --project-ref djrxojuamnwhqajubzab
```

### Deploy the function

```bash
cd supabase/functions
supabase functions deploy send-alert-notification
```

### Set the FCM secret

Open the service account JSON you downloaded from Firebase, then:

```bash
supabase secrets set FCM_SERVICE_ACCOUNT='<paste entire JSON as one line>'
```

Example:
```bash
supabase secrets set FCM_SERVICE_ACCOUNT='{"type":"service_account","project_id":"alertup-cf5ce","private_key":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n","client_email":"firebase-adminsdk-...@alertup-cf5ce.iam.gserviceaccount.com",...}'
```

---

## 4. Create the Database Webhook

1. Supabase Dashboard → **Database** → **Webhooks**
2. Click **Create a new hook**
3. Fill in:
   - **Name**: `on_alert_insert`
   - **Table**: `public.alerts`
   - **Events**: Check **INSERT**
   - **Type**: Select **Supabase Edge Functions**
   - **Function**: `send-alert-notification`
4. Click **Create webhook**

---

## 5. Test the Notification Flow

### Option A — Via the app

1. Build and run the app on a device (not emulator — FCM doesn't work on emulators without Google Play Services)
2. Login as a resident → the FCM token is saved to `device_tokens`
3. Login as an official → post an alert
4. The resident device should receive a push notification

### Option B — Manual test

```bash
supabase functions invoke send-alert-notification --body '{
  "record": {
    "title": "Test Alert",
    "body": "This is a test notification",
    "level": "danger",
    "area": "Camarines Norte",
    "is_active": true
  }
}'
```

Check the logs:
```bash
supabase functions logs send-alert-notification
```

---

## 6. Verify Everything Works

- [ ] Resident can register a household with municipality + barangay dropdowns
- [ ] Official can post an alert targeting "Entire Province" or "Specific Location"
- [ ] Resident receives a push notification when an alert is posted
- [ ] Notification shows the alert title, body, and area
- [ ] Tapping the notification opens the app

---

## Troubleshooting

**"No tokens" in Edge Function logs**
- Check that residents have logged in at least once after the `device_tokens` table was created
- Run `SELECT * FROM device_tokens;` in Supabase SQL Editor to verify tokens exist

**"Failed to get access token"**
- Verify the `FCM_SERVICE_ACCOUNT` secret is set correctly (entire JSON as one line)
- Check that the private key has `\n` escaped as `\\n` in the JSON

**Notifications not showing on device**
- Ensure the device has Google Play Services (FCM doesn't work on emulators without it)
- Check Android 13+ notification permission is granted
- Verify the app is not in battery optimization / doze mode

**Edge Function not firing**
- Check the webhook is created correctly in Supabase Dashboard → Database → Webhooks
- Verify the webhook URL matches your function: `https://djrxojuamnwhqajubzab.supabase.co/functions/v1/send-alert-notification`
- Check Edge Function logs: `supabase functions logs send-alert-notification`
