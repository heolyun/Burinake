
from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
import uvicorn
import traceback

from ai_module import detect_fire_smoke

app = FastAPI(title="AI Server")

@app.get("/health")
async def health_check():
    return {"status": "ok", "message": "Server is running smoothly."}

@app.post("/api/detect")
async def detect_endpoint(
    imageId: str = Form(default=""),
    capturedAt: str = Form(default=""),
    
    image: UploadFile = File(...)
):
    try:
        image_bytes = await image.read()
        boxes = detect_fire_smoke(image_bytes)
        
        response_data = {
            "result": {
                "detected": len(boxes) > 0,  
                "boxes": boxes               
            },
            "metadata": {
                "imageId": imageId,
                "capturedAt": capturedAt
            }
        }
        
        return JSONResponse(content=response_data)
        
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"error": str(e)})

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
