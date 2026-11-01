from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import extract, cases, letters, reference

app = FastAPI(
    title="Unstuck API",
    description="Automated assistance platform for stuck/failed financial transactions and RBI compensation enforcement.",
    version="1.0.0"
)

# CORS middleware configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(extract.router)
app.include_router(cases.router)
app.include_router(letters.router)
app.include_router(reference.router)

@app.get("/health", tags=["System"])
async def health_check():
    """
    Health check endpoint returning system status and configuration summary.
    """
    return {
        "status": "healthy",
        "service": "Unstuck Backend API",
        "gemini_configured": bool(settings.GEMINI_API_KEY),
        "cloud_vision_configured": bool(settings.GOOGLE_APPLICATION_CREDENTIALS),
        "rules_loaded": settings.RULES_FILE_PATH.exists(),
        "banks_loaded": settings.BANKS_FILE_PATH.exists()
    }

@app.get("/", tags=["System"])
async def root():
    return {
        "message": "Welcome to Unstuck API - Team Vector Void",
        "docs": "/docs",
        "health": "/health"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)

