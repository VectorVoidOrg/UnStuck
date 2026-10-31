# ============================================================================
# UFO (Unified Fraud Observer) - One-Command GCP Cloud Run Deployment Script
# Deploys the backend to Google Cloud Run in asia-south1 (Mumbai, India)
#
# Usage:
#   .\deploy_gcp_cloud_run.ps1 -ProjectId "unstuck-vectorvoid"
# ============================================================================
param(
    [Parameter(Mandatory=$false)]
    [string]$ProjectId = "unstuck-vectorvoid",
    [string]$Region = "asia-south1",
    [string]$ServiceName = "ufo-backend"
)

Write-Host "[UFO] Preparing GCP Cloud Run deployment for '$ServiceName' in $Region (Mumbai)..." -ForegroundColor Cyan

# Build YAML env file so MONGO_URI with special characters (?, &, =) passes cleanly to gcloud
$envYamlLines = @(
    'UFO_ENV: "production"',
    'MAX_AUTO_REFUND_INR: "5000.0"',
    'MAX_REASONING_LOOPS: "3"'
)

$allowedKeys = @(
    "GEMINI_API_KEY",
    "GROQ_API_KEY",
    "MONGO_URI",
    "MONGO_DB_NAME",
    "UFO_FORCE_MONGO",
    "FRONTIER_LLM_MODEL",
    "GROQ_SLM_MODEL",
    "PII_VAULT_SECRET_KEY"
)

if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and (-not $line.StartsWith("#")) -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim()
            if (($v -ne "") -and ($allowedKeys -contains $k)) {
                $escapedVal = $v.Replace('"', '\"')
                $envYamlLines += "${k}: `"$escapedVal`""
            }
        }
    }
}

$envFile = ".cloudrun.env.yaml"
$envYamlLines | Set-Content -Path $envFile -Encoding Ascii

Write-Host "[UFO] Setting active GCP Project to: $ProjectId" -ForegroundColor Yellow
gcloud config set project $ProjectId

Write-Host "[UFO] Enabling Cloud Run and Cloud Build APIs..." -ForegroundColor Yellow
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

Write-Host "[UFO] Building Dockerfile in the cloud and deploying to Cloud Run ($Region)..." -ForegroundColor Green
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
    --env-vars-file $envFile

if (Test-Path $envFile) {
    Remove-Item $envFile -Force
}

Write-Host "[UFO] Deployment complete! Copy the Service URL above into your Android app BASE_URL." -ForegroundColor Cyan
