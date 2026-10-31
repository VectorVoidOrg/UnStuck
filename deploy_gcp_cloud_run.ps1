# ============================================================================
# 🛸 UFO (Unified Fraud Observer) — One-Command GCP Cloud Run Deployment Script
# Deploys the backend to Google Cloud Run in `asia-south1` (Mumbai, India)
# for RBI Data Localization Compliance & 24/7 availability without your PC on.
#
# Usage:
#   .\deploy_gcp_cloud_run.ps1 -ProjectId "your-gcp-project-id"
# ============================================================================
param(
    [Parameter(Mandatory=$true)]
    [string]$ProjectId,
    [string]$Region = "asia-south1",
    [string]$ServiceName = "ufo-backend"
)

Write-Host "🛸 Preparing GCP Cloud Run deployment for '$ServiceName' in $Region (Mumbai)..." -ForegroundColor Cyan

# Read keys from local .env if present
$envVars = @{
    "UFO_ENV" = "production"
    "MAX_AUTO_REFUND_INR" = "5000.0"
    "MAX_REASONING_LOOPS" = "3"
    "UFO_FORCE_MONGO" = "false"
}

if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim()
            if ($v -ne "" -and $k -in @("GEMINI_API_KEY", "GROQ_API_KEY", "MONGO_URI", "UFO_FORCE_MONGO", "FRONTIER_LLM_MODEL", "GROQ_SLM_MODEL", "PII_VAULT_SECRET_KEY")) {
                $envVars[$k] = $v
            }
        }
    }
}

$envString = ($envVars.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ","

Write-Host "⚙️ Setting active GCP Project to: $ProjectId" -ForegroundColor Yellow
gcloud config set project $ProjectId

Write-Host "🚀 Enabling Cloud Run & Cloud Build APIs..." -ForegroundColor Yellow
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

Write-Host "📦 Building Dockerfile in the cloud & deploying to Cloud Run ($Region)..." -ForegroundColor Green
gcloud run deploy $ServiceName `
    --source . `
    --region $Region `
    --platform managed `
    --allow-unauthenticated `
    --port 8000 `
    --memory 1Gi `
    --cpu 1 `
    --min-instances 1 `
    --max-instances 5 `
    --set-env-vars "$envString"

Write-Host "✅ Deployment complete! Copy the Service URL above into your Android app's BASE_URL." -ForegroundColor Cyan
