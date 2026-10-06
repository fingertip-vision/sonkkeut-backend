# 손끝길 · sonkkeut-backend

시각장애인의 키오스크 조작을 돕는 손끝길 서비스의 매장·메뉴·익명 통계 API 저장소입니다.

- [서비스 소개와 전체 구조](docs/service-overview.md)
- [저장소 간 연동 계약](docs/integration.md)
- [개발·실행 안내](docs/operations.md)
- [Fingertip Vision 조직](https://github.com/fingertip-vision)

## 기존 저장소 안내

# sonkkeut-backend
손끝길 - API 서버, 키오스크 화면 데이터·사용자 관리 백엔드

Java 21, Spring Boot, MySQL

## 로컬 실행

1. Java 21: `mise install` (버전은 `mise.toml`에 고정)
2. 환경 변수: `.env.example`을 복사해 `.env`를 만들고 값을 채운다. `.env`는 커밋하지 않는다.
3. MySQL: `docker compose up -d`
   - 로컬에 MySQL이 이미 3306을 쓰고 있으면 `.env`의 `DB_PORT`를 3307 등으로 바꾼다.
4. 서버: `./gradlew bootRun`
5. 상태 확인: `curl localhost:8080/actuator/health` → `{"status":"UP"}`
   - 8080이 사용 중이면 `.env`의 `SERVER_PORT`를 8081 등으로 바꾼다.

테스트는 실제 MySQL에 붙어서 돌기 때문에 3번까지 한 뒤 `./gradlew test`를 실행한다.

## 배포

`main`에 merge되면 GitHub Actions(`.github/workflows/deploy.yml`)가 테스트 → 이미지 빌드(ECR) → EC2 재배포(SSM)를 한다. PR에서는 테스트만 돈다.

- 저장소 변수: `AWS_DEPLOY_ROLE_ARN`(OIDC 배포 역할), `EC2_INSTANCE_ID`
- 서버: `/opt/sonkkeut/.env`에 `.env.example`의 DB 값과 `ECR_REGISTRY`를 둔다.
- 인바운드 포트를 열지 않고 Cloudflare 터널로만 노출한다. 주소는 Actions 실행 요약에 나온다.
