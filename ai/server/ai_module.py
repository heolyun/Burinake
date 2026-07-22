
import cv2
import numpy as np
import base64
from ultralytics import YOLO, RTDETR

# model = YOLO('./weights/yolov11_aug_50.pt')  # YOLOv11 + 50Epochs + Augmentation
# model = YOLO('./weights/yolov12_50.pt')  # YOLOv12 + 50Epochs
# model = YOLO('./weights/yolov12_aug_50.pt')  # YOLOv12 + 50Epochs + Augmentation
model = RTDETR('./weights/rtdetr_aug_10.pt')  # RTDETR + 10Epcohs + Augmentation
#model = YOLO('./weights/best.pt')  # Additional Fine-tuned model

CLASS_NAMES = {
    0: "fire",
    1: "smoke"
}

def detect_fire_smoke(image_bytes: bytes):
    nparr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if img is None:
        raise ValueError("이미지 디코딩 실패.")

    results = model.predict(source=img, conf=0.1, save=False)

    boxes = []
    for box in results[0].boxes:
        x1, y1, x2, y2 = box.xyxy[0].tolist()
        conf = box.conf[0].item()
        cls_id = int(box.cls[0].item())
        
        x = round(x1, 2)
        y = round(y1, 2)
        width = round(x2 - x1, 2)
        height = round(y2 - y1, 2)
        
        boxes.append({
            "x": x,
            "y": y,
            "width": width,
            "height": height,
            "label": CLASS_NAMES.get(cls_id, "unknown"),
            "score": round(conf, 4)
        })

    # =====================================================================
    # annotated_img = results[0].plot()     
    # _, buffer = cv2.imencode('.jpg', annotated_img)     
    # encoded_image_base64 = base64.b64encode(buffer).decode('utf-8')     
    # return boxes, encoded_image_base64
    # =====================================================================

    return boxes
