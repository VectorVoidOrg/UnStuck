import logging
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import AppError as ConfigAppError, get_settings
from app.routers import cases, reference

try:
    from app.schemas import AppError
except ImportError:
    AppError = ConfigAppError

logger = logging.getLogger("unstuck.api")
settings = get_settings()

app = FastAPI(title="Unstuck API")

# Configure CORS for Next.js frontend (default port 8444)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(AppError)
@app.exception_handler(ConfigAppError)
async def app_error_handler(request: Request, exc: AppError):
    return JSONResponse(
        status_code=exc.status,
        content={"detail": {"code": exc.code, "message": exc.message}},
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
):
    errors = exc.errors()
    msg = "; ".join(
        f"{' -> '.join(str(loc) for loc in err['loc'])}: {err['msg']}"
        for err in errors
    )
    return JSONResponse(
        status_code=422,
        content={"detail": {"code": "VALIDATION_ERROR", "message": msg}},
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    # Check if the exception has code and status attributes like AppError
    if hasattr(exc, "code") and hasattr(exc, "status") and hasattr(exc, "message"):
        return JSONResponse(
            status_code=getattr(exc, "status", 422),
            content={
                "detail": {
                    "code": getattr(exc, "code", "VALIDATION_ERROR"),
                    "message": getattr(exc, "message", str(exc)),
                }
            },
        )

    logger.exception("Unhandled server exception: %s", exc)
    return JSONResponse(
        status_code=500,
        content={
            "detail": {
                "code": "INTERNAL_SERVER_ERROR",
                "message": "An unexpected server error occurred.",
            }
        },
    )


@app.get("/health")
def health_check():
    return {"status": "ok"}


app.include_router(cases.router)
app.include_router(reference.router)

# Include Dhawal's extract and letters routers only if importable
try:
    from app.routers import extract

    app.include_router(extract.router)
except (ImportError, AttributeError):
    pass

try:
    from app.routers import letters

    app.include_router(letters.router)
except (ImportError, AttributeError):
    pass
