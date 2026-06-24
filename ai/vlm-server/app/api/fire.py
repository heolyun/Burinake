from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, Field

router = APIRouter()


class YoloResult(BaseModel):
    detected: bool
    confidence: float | None = None
    boxes: list[dict] = Field(default_factory=list)


class FireSummaryResponse(BaseModel):
    summary: str
    riskLevel: str
    recommendedAction: str


@router.post("/api/v1/fire/summary", response_model=FireSummaryResponse)
async def summarize_fire(
    imageId: str = Form(...),
    blobPath: str = Form(...),
    yoloResult: str = Form(...),
    image: UploadFile = File(...),
) -> FireSummaryResponse:
    parsed = YoloResult.model_validate_json(yoloResult)

    if parsed.detected:
        confidence_text = ""
        if parsed.confidence is not None:
            confidence_text = f" (confidence: {parsed.confidence:.2f})"

        return FireSummaryResponse(
            summary=f"화재 징후가 확인되었습니다{confidence_text}. 연기와 불꽃이 관측된 영역이 있습니다.",
            riskLevel="HIGH",
            recommendedAction="즉시 대피하고 119에 신고하세요.",
        )

    return FireSummaryResponse(
        summary="화재 징후가 뚜렷하지 않습니다. 현재 입력 기준으로는 이상 징후가 보이지 않습니다.",
        riskLevel="LOW",
        recommendedAction="추가 모니터링을 권장합니다.",
    )
