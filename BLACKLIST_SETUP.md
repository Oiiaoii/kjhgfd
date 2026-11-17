# Accounts and Blacklist (owner only)

## Accounts
Sign up = a username (3-20 letters, numbers, _) and a password (6+). Everything stays on the phone; only a salted hash of the password is saved.

## Blacklist
- Blocked usernames get the "Access blocked" screen (at sign up / log in, at app start, before the macro starts, and every 5 minutes while it runs).
- Only the OWNER sees the Blacklist screen (account picture > Blacklist). The owner username (`owner_username` in app/src/main/res/values/auth.xml)
  is reserved: it can only be used with the owner password, whose hash is `owner_hash`. Nobody else can sign up with that name.
- Make the hash on any computer with Python (choose a password used ONLY for this, letters and numbers):
  `python3 -c "import hashlib;print(hashlib.pbkdf2_hmac('sha256',b'YOURPASSWORD',b'luminfish-owner-v1',60000,32).hex())"`
- Put your raw Gist link in `blacklist_url` (a gist file `blacklist.json` with `{"version":1,"banned":[]}`, Raw button, without the commit part).
- To block: Blacklist screen > username > Block account > Publish (Gist ID + a GitHub token with only the gist permission, typed once, kept on your phone).

## Honest limits
No server: a blocked person can make a new username, or patch the APK. This stops normal users. Not tested against GitHub.
