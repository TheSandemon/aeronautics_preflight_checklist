import os
import re
import urllib.request
import json

def main():
    # Path to gradle.properties in home dir
    home_gradle = os.path.expanduser("~/.gradle/gradle.properties")
    token = None
    if os.path.exists(home_gradle):
        with open(home_gradle, "r") as f:
            for line in f:
                m = re.match(r"^\s*modrinth_token\s*=\s*(.+)$", line)
                if m:
                    token = m.group(1).strip()
                    break

    if not token:
        # Fallback to the one we verified
        token = "mrp_1fDvMshdPqv86ctCEPrj0jrHLFg4FKgi0U2OEYvfior1tAxD3DhYrZvcSPk1"

    # Read description file
    desc_path = os.path.join(os.path.dirname(__file__), "..", "Curseforge_Description.md")
    with open(desc_path, "r", encoding="utf-8") as f:
        description = f.read()

    # Make PATCH request to Modrinth API
    url = "https://api.modrinth.com/v2/project/l2LYH0FN"
    data = json.dumps({"body": description}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": token,
            "Content-Type": "application/json",
            "User-Agent": "AeronauticsPreflightChecklistPublisher/1.0.1 (kyletouchet@gmail.com)"
        },
        method="PATCH"
    )

    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            print(f"Modrinth project description updated successfully. Status: {status}")
    except Exception as e:
        print(f"Error updating Modrinth project description: {e}")
        if hasattr(e, 'read'):
            print(e.read().decode('utf-8'))

if __name__ == "__main__":
    main()
