# Architecture Overview

Burinake는 React frontend, Spring Boot backend, Python AI servers를 분리한 모노레포 구조입니다.

기본 흐름:

1. CCTV 프레임 또는 이벤트 이미지가 Backend로 전달됩니다.
2. Backend가 원본/스냅샷을 Azure Blob Storage에 저장합니다.
3. Backend가 YOLO Server에 화재 후보 탐지를 요청합니다.
4. 필요 시 VLM Server가 탐지 결과를 검증하고 설명을 생성합니다.
5. Backend가 이벤트 상태와 신고 지원 정보를 Frontend에 제공합니다.
