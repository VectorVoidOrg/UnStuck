# ============================================================================
# 🛸 UFO (Unified Fraud Observer) — Local FastAPI + ngrok Launcher
# Usage:
#   .\start_ufo_server.ps1 -NgrokDomain "your-domain.ngrok-free.app"
# ============================================================================
param(
    [string]$NgrokDomain = "",
    [int]$Port = 8000
)

Write-Host "🛸 Starting UFO 5-Layer Fraud Detection Backend on port $Port..." -ForegroundColor Cyan

if ($NgrokDomain -ne "") {
    Write-Host "🌐 Launching ngrok tunnel on static domain: https://$NgrokDomain" -ForegroundColor Green
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "ngrok http --url=$NgrokDomain $Port"
} else {
    Write-Host "💡 Tip: Run 'ngrok http --url=your-domain.ngrok-free.app $Port' in another terminal to expose publicly." -ForegroundColor Yellow
}

uvicorn backend.main:app --host 0.0.0.0 --port $Port --reload
