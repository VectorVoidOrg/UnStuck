"""
UFO One-Command Cloud Deployment to Hugging Face Spaces (Docker) (`deploy_hf_space.py`).
100% Free 24/7 Cloud Hosting (16 GB RAM, 2 vCPU) — NO Credit Card or Billing Account Required!

Usage:
    python deploy_hf_space.py --token hf_YourHuggingFaceWriteToken
"""
import argparse
import sys
from pathlib import Path
from dotenv import dotenv_values
from huggingface_hub import HfApi

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT_DIR = Path(__file__).resolve().parent


def deploy_to_hf_spaces(hf_token: str, space_name: str = "ufo-backend") -> None:
    api = HfApi(token=hf_token)
    user_info = api.whoami()
    username = user_info["name"]
    repo_id = f"{username}/{space_name}"

    print(f"[UFO] Authenticated as Hugging Face user: {username}")
    print(f"[UFO] Creating/updating 24/7 Docker Cloud Space: {repo_id} ...")

    api.create_repo(
        repo_id=repo_id,
        repo_type="space",
        space_sdk="docker",
        exist_ok=True,
        private=False,
    )

    # Read secrets from local .env and push them as Space Secrets
    env_path = ROOT_DIR / ".env"
    if env_path.exists():
        env_vars = dotenv_values(env_path)
        secret_keys = [
            "GEMINI_API_KEY",
            "GROQ_API_KEY",
            "MONGO_URI",
            "MONGO_DB_NAME",
            "UFO_FORCE_MONGO",
            "FRONTIER_LLM_MODEL",
            "GROQ_SLM_MODEL",
            "PII_VAULT_SECRET_KEY",
        ]
        for k in secret_keys:
            val = env_vars.get(k)
            if val and val.strip():
                api.add_space_secret(repo_id=repo_id, key=k, value=val.strip())
                print(f"[UFO] Configured Space Secret: {k}")

    # Create Hugging Face Space README.md frontmatter header in a temp upload or directly
    hf_readme = (
        "---\n"
        "title: UFO Unified Fraud Observer Backend\n"
        "emoji: 🛸\n"
        "colorFrom: blue\n"
        "colorTo: indigo\n"
        "sdk: docker\n"
        "app_port: 7860\n"
        "---\n\n"
        "# UFO — Unified Fraud Observer Backend\n"
        "5-Layer Agentic Real-Time UPI Fraud Detection & Scam Resolution API.\n"
    )
    api.upload_file(
        path_or_fileobj=hf_readme.encode("utf-8"),
        path_in_repo="README.md",
        repo_id=repo_id,
        repo_type="space",
    )

    # Upload Dockerfile
    api.upload_file(
        path_or_fileobj=str(ROOT_DIR / "Dockerfile"),
        path_in_repo="Dockerfile",
        repo_id=repo_id,
        repo_type="space",
    )

    # Upload backend/ directory
    print("[UFO] Uploading backend/ source code, models, and datasets...")
    api.upload_folder(
        folder_path=str(ROOT_DIR / "backend"),
        path_in_repo="backend",
        repo_id=repo_id,
        repo_type="space",
        ignore_patterns=["__pycache__", "*.pyc", ".pytest_cache"],
    )

    live_url = f"https://{username.lower()}-{space_name.lower()}.hf.space"
    print("\n" + "=" * 75)
    print("[UFO] SUCCESS! Your backend is building in the cloud (24/7 online, $0 cost)!")
    print(f"  -> Live API Base URL (for Android App): {live_url}/")
    print(f"  -> Interactive Swagger UI Docs:         {live_url}/docs")
    print(f"  -> Health Check Endpoint:               {live_url}/health")
    print(f"  -> Build Logs Dashboard:                https://huggingface.co/spaces/{repo_id}")
    print("=" * 75)


def main() -> None:
    parser = argparse.ArgumentParser(description="Deploy UFO Backend to Hugging Face Spaces (No Credit Card Needed)")
    parser.add_argument("--token", type=str, required=True, help="Hugging Face Write Token (hf_...)")
    parser.add_argument("--name", type=str, default="ufo-backend", help="Space name (default: ufo-backend)")
    args = parser.parse_args()
    deploy_to_hf_spaces(hf_token=args.token.strip(), space_name=args.name.strip())


if __name__ == "__main__":
    main()
