import os
import tempfile
from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np
from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, Field
from ultralytics import YOLO

router = APIRouter()


class FireAnalyzeRequest(BaseModel):
    imageId: str
    blobPath: str
    imageUrl: str | None = None


class BoundingBox(BaseModel):
    x: int
    y: int
    width: int
    height: int
    label: str
    score: float | None = None


class FireAnalyzeResponse(BaseModel):
    detected: bool
    confidence: float | None = None
    boxes: list[BoundingBox] = Field(default_factory=list)


@lru_cache
def load_model() -> YOLO | None:
    model_path = os.getenv("MODEL_PATH")
    if model_path and Path(model_path).exists():
        return YOLO(model_path)
    return None


def infer_with_model(image_path: str) -> FireAnalyzeResponse | None:
    model = load_model()
    if model is None:
        return None

    results = model.predict(source=image_path, conf=0.25, verbose=False)
    if not results:
        return FireAnalyzeResponse(detected=False, confidence=None, boxes=[])

    result = results[0]
    names = result.names
    boxes: list[BoundingBox] = []
    fire_confidences: list[float] = []

    if result.boxes is not None:
        for box in result.boxes:
            cls_id = int(box.cls.item())
            label = names.get(cls_id, str(cls_id))
            score = float(box.conf.item())
            xyxy = box.xyxy[0].tolist()
            x1, y1, x2, y2 = [int(value) for value in xyxy]
            boxes.append(
                BoundingBox(
                    x=x1,
                    y=y1,
                    width=max(0, x2 - x1),
                    height=max(0, y2 - y1),
                    label=label,
                    score=score,
                )
            )
            if "fire" in label.lower() or "flame" in label.lower() or "smoke" in label.lower():
                fire_confidences.append(score)

    detected = bool(fire_confidences) or any(
        "fire" in box.label.lower() or "flame" in box.label.lower() or "smoke" in box.label.lower()
        for box in boxes
    )
    confidence = max(fire_confidences) if fire_confidences else None
    return FireAnalyzeResponse(detected=detected, confidence=confidence, boxes=boxes)


def infer_with_heuristic(image_path: str) -> FireAnalyzeResponse:
    image = cv2.imread(image_path)
    if image is None:
        return FireAnalyzeResponse(detected=False, confidence=None, boxes=[])

    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)

    mask = (r > 150) & (r > (g * 1.25)) & (r > (b * 1.25))
    fire_pixels = int(mask.sum())
    total_pixels = int(mask.size)
    ratio = fire_pixels / total_pixels if total_pixels else 0.0

    if fire_pixels == 0:
        return FireAnalyzeResponse(detected=False, confidence=0.0, boxes=[])

    ys, xs = np.where(mask)
    x1, x2 = int(xs.min()), int(xs.max())
    y1, y2 = int(ys.min()), int(ys.max())

    confidence = min(0.95, 0.2 + ratio * 6.0)
    return FireAnalyzeResponse(
        detected=ratio >= 0.01,
        confidence=confidence,
        boxes=[
            BoundingBox(
                x=x1,
                y=y1,
                width=max(0, x2 - x1),
                height=max(0, y2 - y1),
                label="fire",
                score=confidence,
            )
        ],
    )


@router.post("/api/v1/fire/analyze", response_model=FireAnalyzeResponse)
async def analyze_fire(
    imageId: str = Form(...),
    blobPath: str = Form(...),
    image: UploadFile = File(...),
) -> FireAnalyzeResponse:
    suffix = Path(image.filename or "image.jpg").suffix or ".jpg"
    temp_path = None

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_path = temp_file.name
            temp_file.write(await image.read())

        model_response = infer_with_model(temp_path)
        if model_response is not None:
            return model_response

        return infer_with_heuristic(temp_path)
    finally:
        if temp_path and Path(temp_path).exists():
            Path(temp_path).unlink(missing_ok=True)
