import urllib.request
import urllib.parse
import json
import base64
import sys
import os

# Defaults
DEFAULT_CLIENT_ID = "REDACTED"
DEFAULT_CLIENT_SECRET = "REDACTED"
REDIRECT_URI = "https://developer.schwab.com/oauth2-redirect.html"

def get_tokens():
    print("--- Schwab Token Generator ---")
    
    # 1. Client ID & Secret (Hardcoded)
    client_id = DEFAULT_CLIENT_ID
    client_secret = DEFAULT_CLIENT_SECRET
    token_url = "https://api.schwabapi.com/v1/oauth/token"
    headers = {
        "Authorization": "Basic " + base64.b64encode(f"{client_id}:{client_secret}".encode()).decode(),
        "Content-Type": "application/x-www-form-urlencoded"
    }

    if os.path.exists("schwab_tokens.json"):
        try:
            with open("schwab_tokens.json", "r") as f:
                old_tokens = json.load(f)
            refresh_token = old_tokens.get("refresh_token")
            if refresh_token:
                print("Found existing refresh token. Attempting to silently fetch a new Access Token...")
                data = {"grant_type": "refresh_token", "refresh_token": refresh_token}
                data_bytes = urllib.parse.urlencode(data).encode('utf-8')
                req = urllib.request.Request(token_url, data=data_bytes, headers=headers, method="POST")
                with urllib.request.urlopen(req) as response:
                    if response.getcode() == 200:
                        body = response.read().decode('utf-8')
                        new_tokens = json.loads(body)
                        if "refresh_token" not in new_tokens:
                            new_tokens["refresh_token"] = refresh_token
                        with open("schwab_tokens.json", "w") as f:
                            json.dump(new_tokens, f)
                        print("SUCCESS: Generated new Access Token in the background!")
                        return
                    else:
                        print("Refresh token expired. Falling back to browser prompt.")
        except Exception as e:
            print(f"Failed to refresh token: {e}. Falling back to browser prompt.")
    
    print(f"Using App Key: {client_id}")
    print(f"Using Secret:  {client_secret[:5]}...")

    # 3. Redirect URL
    print("\n1. Go to this URL in your browser and log in:")
    print(f"https://api.schwabapi.com/v1/oauth/authorize?response_type=code&client_id={client_id}&scope=readonly&redirect_uri={REDIRECT_URI}")
    print("\n2. After authorizing, you will be redirected to a blank page.")
    print("3. Copy the entire URL from the address bar and paste it below.")
    
    try:
        redirect_url = input("\nPaste Redirect URL here: ").strip()
    except EOFError:
        print("Error: Input stream closed.")
        return
    
    # Extract code
    try:
        parsed = urllib.parse.urlparse(redirect_url)
        qs = urllib.parse.parse_qs(parsed.query)
        code = qs.get('code', [None])[0]
        if not code:
            # Maybe it's just the code pasted?
            if "http" not in redirect_url and len(redirect_url) > 20:
                code = redirect_url
            else:
                print("Error: Could not find 'code' in the URL.")
                return
                
        # Handle the weird decoding issue Schwab sometimes has (code ending in @ needs careful handling? No, usually code is standard)
        # Actually, the code needs to be decoded if it was URL encoded? parse_qs handles that.
        
    except Exception as e:
        print(f"Error parsing URL: {e}")
        return

    # 4. Exchange for Tokens
    print("\nExchanging code for tokens...")
    
    token_url = "https://api.schwabapi.com/v1/oauth/token"
    headers = {
        "Authorization": "Basic " + base64.b64encode(f"{client_id}:{client_secret}".encode()).decode(),
        "Content-Type": "application/x-www-form-urlencoded"
    }
    
    data = {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI
    }
    
    data_bytes = urllib.parse.urlencode(data).encode('utf-8')
    
    req = urllib.request.Request(token_url, data=data_bytes, headers=headers, method="POST")
    
    try:
        with urllib.request.urlopen(req) as response:
            if response.getcode() == 200:
                body = response.read().decode('utf-8')
                tokens = json.loads(body)
                
                print("\nSUCCESS!")
                print("-" * 60)
                print(f"Refresh Token (7-Day): {tokens.get('refresh_token')}")
                print("-" * 60)
                print(f"Access Token (30-Min): {tokens.get('access_token')}")
                print("-" * 60)
                
                # Save to file
                with open("schwab_tokens.json", "w") as f:
                    f.write(body)
                print("\nTokens saved to 'schwab_tokens.json'")
                
            else:
                print(f"Failed with code {response.getcode()}")
                print(response.read().decode())
                
    except urllib.error.HTTPError as e:
        print(f"HTTP Error: {e.code}")
        err_body = e.read()
        try:
            import gzip
            err_body = gzip.decompress(err_body)
        except Exception:
            pass
        print(err_body.decode('utf-8', errors='replace'))
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    get_tokens()
