"""HumanFriendAI API backend. Keep provider credentials in environment variables."""
import os
from flask import Flask, request, jsonify
import requests

app = Flask(__name__)

@app.get("/health")
def health():
    return jsonify(status="ok", service="HumanFriendAI backend")

@app.post("/chat")
def chat():
    data = request.get_json(silent=True) or {}
    prompt = str(data.get("message", "")).strip()
    provider = str(data.get("provider", "gemini")).lower()
    if not prompt:
        return jsonify(error="message is required"), 400
    user_key = str(data.get("api_key", "")).strip()
    try:
        if provider == "gemini":
            key = user_key or os.getenv("GEMINI_API_KEY", "")
            model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
            if not key:
                return jsonify(error="Configure GEMINI_API_KEY in backend environment or provide a personal key in app settings."), 503
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
            r = requests.post(url, json={"contents":[{"parts":[{"text":prompt}]}]}, timeout=75)
            r.raise_for_status()
            body = r.json()
            answer = body["candidates"][0]["content"]["parts"][0]["text"]
        elif provider in ("huggingface", "hf"):
            key = user_key or os.getenv("HF_TOKEN", "")
            model = os.getenv("HF_MODEL", "HuggingFaceH4/zephyr-7b-beta")
            if not key:
                return jsonify(error="Configure HF_TOKEN in backend environment or provide a personal key in app settings."), 503
            r = requests.post("https://router.huggingface.co/v1/chat/completions", headers={"Authorization": f"Bearer {key}"}, json={"model":model,"messages":[{"role":"user","content":prompt}],"max_tokens":700}, timeout=90)
            r.raise_for_status()
            answer = r.json()["choices"][0]["message"]["content"]
        else:
            return jsonify(error="Unsupported provider. Use gemini or huggingface."), 400
        return jsonify(response=answer, provider=provider)
    except requests.HTTPError as e:
        detail = e.response.text[:800] if e.response is not None else str(e)
        return jsonify(error=f"Provider request failed: {detail}"), 502
    except Exception as e:
        app.logger.exception("chat failure")
        return jsonify(error=f"Backend error: {type(e).__name__}"), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "8080")))
