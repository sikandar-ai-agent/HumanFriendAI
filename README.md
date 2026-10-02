# HumanFriendAI v0.9 — refreshed source package

## Included
- Android Compose chat UI with Gemini/Hugging Face provider selector, configurable backend URL, language selector, theme toggle, and New Chat action.
- Flask backend in `backend/` supporting Gemini and Hugging Face Inference Router.
- GitHub Actions workflow at `.github/workflows/android.yml` to build and upload a debug APK artifact.

## Important setup
1. Deploy `backend/` to a Python HTTPS hosting service.
2. Set `GEMINI_API_KEY` and/or `HF_TOKEN` as host environment secrets. Optional model variables: `GEMINI_MODEL`, `HF_MODEL`.
3. In app Settings, replace `https://YOUR-HTTPS-SERVER.example` with the deployed backend base URL, save, and select provider.
4. Push the project to GitHub, open Actions, run **Build HumanFriendAI APK**, and download the `HumanFriendAI-debug-APK` artifact.

No real hosted endpoint or provider credentials are included. The APK is not prebuilt or verified in this package. Do not commit API keys. For production, add authentication, rate limits, and request quotas before exposing the backend publicly.
