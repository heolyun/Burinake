from fastapi import FastAPI

from app.api.fire import router as fire_router
from app.api.health import router as health_router

app = FastAPI(title="Burinake VLM Server", version="0.1.0")
app.include_router(health_router)
app.include_router(fire_router)
