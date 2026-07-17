# Azure 운영 및 종료 기록

## 개요

Burinake는 화재 감지 관제 서비스를 Azure 기반 컨테이너 환경에서 운영했다. 프로젝트 운영 종료 후에는 포트폴리오 시연을 정적 데모로 분리하고, 운영 데이터를 로컬에 백업한 다음 지속 과금을 방지하기 위해 Azure 리소스를 제거했다.

## 운영 아키텍처

- Azure Kubernetes Service: 프런트엔드, Spring Boot 백엔드, YOLO 추론 서버, VLM 추론 서버 운영
- Azure Container Registry Basic: 컨테이너 이미지 저장 및 배포
- Azure Database for PostgreSQL Flexible Server: 운영·개발 데이터베이스
- Azure Blob Storage: 화재 감지 스냅샷, 모델 및 빌드 컨텍스트 저장
- Azure Key Vault: PostgreSQL, Storage, Azure OpenAI 자격 증명 관리
- Azure OpenAI 및 Azure AI Services: 이미지 분석 및 화재 상황 설명 생성
- Application Insights 및 Log Analytics: 애플리케이션·클러스터 관측
- Kubernetes Ingress, HPA, Secret Store CSI Driver 구성

인프라 재현에 필요한 Bicep, Kubernetes 매니페스트, Dockerfile과 배포 스크립트는 저장소에 남아 있다.

## 비용 절감형 포트폴리오 전환

실제 클라우드 API와 119 신고 연동을 호출하지 않는 포트폴리오 데모 모드를 구현하고 Vercel에 배포했다.

- 데모: <https://burinake-demo.vercel.app>
- 검증된 시나리오 데이터를 사용해 대시보드, 이슈 상세, 감지 이미지, VLM 분석과 신고서 흐름 재현
- Azure 운영 서버 종료 후에도 프로젝트의 사용자 경험을 확인할 수 있도록 분리

## 종료 전 백업

2026-07-17에 다음 자료를 로컬 오프라인 백업으로 보존하고 무결성을 검증했다.

- PostgreSQL custom-format 덤프 2개: 운영 DB와 개발 DB
- Blob Storage 파일 61개, 총 10,088,826 bytes
- Azure Resource Manager 리소스 그룹 내보내기
- 리소스명, 유형, 지역, SKU 목록
- 호출자와 구독 식별자를 제거한 Azure Activity Log
- 전체 백업 파일 SHA-256 체크섬 67개

데이터베이스 덤프, Blob 원본, Key Vault 비밀값 및 연결 문자열은 공개 저장소에 포함하지 않는다.

## 종료 결과

백업 검증 후 다음 리소스를 제거했다.

- AKS와 AKS 관리 리소스 그룹
- PostgreSQL Flexible Server
- Container Registry
- Blob Storage 계정
- Key Vault
- Azure OpenAI 및 Azure AI Services
- Application Insights, Log Analytics와 데이터 수집 규칙
- 고정 Public IP, 디스크, 네트워크 리소스

삭제 후 Azure CLI로 기본 리소스 그룹과 AKS 관리 리소스 그룹이 존재하지 않으며, 구독에 Burinake 이름의 잔존 리소스가 없음을 확인했다.

## 관련 코드

- `infra/azure/bicep/main.bicep`
- `infra/k8s/`
- `docs/deployment/aks-deployment.md`
- `docs/deployment/acr-cicd.md`
- `scripts/deploy-aks.sh`
- 각 서비스의 `Dockerfile`

이 기록은 서버가 현재 실행 중이라는 사실보다 인프라 설계, 배포 자동화, 보안 정보 관리, 관측, 비용 통제 및 안전한 서비스 종료 경험을 입증하는 용도로 사용한다.
