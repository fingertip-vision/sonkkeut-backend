# 백엔드 운영과 연동 안내

## 역할과 실행

Java 21·Spring Boot·MySQL을 사용합니다. 매장 등록, 메뉴·가격·별칭·옵션·품절 관리, 근처 매장 조회, 익명 세션 통계, 모델 메타데이터를 제공합니다. 앱 영상·음성을 서버에서 추론하거나 실제 키오스크 결제를 실행하는 서비스가 아닙니다.

1. 저장소의 `.env.example`을 `.env`로 복사하고 DB 값을 입력합니다.
2. `docker compose up -d`로 MySQL을 시작합니다.
3. `./gradlew bootRun` 또는 Windows의 `.\gradlew.bat bootRun`으로 API를 실행합니다.
4. `http://localhost:8080/actuator/health`에서 상태를 확인합니다.
5. MySQL이 실행 중인 환경에서 `./gradlew test`를 실행합니다.

환경 변수의 실제 이름과 기본값은 `.env.example`을 기준으로 합니다. `.env`와 점주/운영자 키는 저장소에 올리지 않습니다. DB 포트가 충돌하면 `DB_PORT`, API 포트가 충돌하면 `SERVER_PORT`를 조정합니다.

## 앱 연결과 권한

앱에는 서버의 HTTPS 기본 주소와 매장 코드를 설정합니다. `GET /api/stores/{code}`와 `GET /api/stores/{code}/menu`는 공개 조회입니다. 메뉴 수정에는 `X-Owner-Key` 또는 `X-Admin-Key`가 필요합니다. 운영자 API는 `ADMIN_KEY`가 없으면 잠깁니다. 점주 키는 매장 등록 응답에서 한 번만 제공됩니다.

프론트가 받는 메뉴 예시:

```json
{
  "store_code": "K7M3Q2",
  "store_name": "예시 카페",
  "menu_version": 1,
  "categories": ["커피"],
  "items": [{"id": 1, "category": "커피", "name": "아메리카노", "price": 4500,
    "aliases": ["아아"], "options": [{"group": "온도", "values": ["HOT", "ICE"]}], "sold_out": false}]
}
```

코드는 예시이며 실제 매장 등록 결과로 바꿉니다. 필드 제한·오류·ETag·통계 형식은 [API 명세](api.md)를 따릅니다. 앱에서 선택적으로 읽는 `search_terms`와 `description`이 현재 서버에서 저장·제공된다고 가정하지 마세요. 앱의 로컬 관련 표현 DB는 별도 기능입니다.

## AWS 배포

현재 경로는 GitHub Actions 테스트 → OIDC 역할 인증 → ECR 이미지 → SSM으로 EC2 재배포입니다. `main` 반영은 실제 배포를 실행할 수 있습니다. 문서 변경도 워크플로의 경로 제외 조건이 없으므로 main push 시 배포 조건에 해당합니다. 운영과 무관한 변경은 PR에서 먼저 검토합니다.

- 저장소 변수: `AWS_DEPLOY_ROLE_ARN`, `EC2_INSTANCE_ID`.
- 지역·이미지 저장소: `.github/workflows/deploy.yml` 참조.
- 서버 설정: `/opt/sonkkeut/.env`에 DB 값과 `ECR_REGISTRY` 설정.
- 운영 Compose: `compose.prod.yaml`, 재배포: `scripts/deploy.sh`.
- 공개 HTTPS 주소: 배포 Actions 실행 요약의 터널 주소.

Cloudflare quick tunnel 주소는 재시작하면 바뀔 수 있습니다. 문서나 APK에 임시 주소를 영구 운영 주소로 기재하지 않습니다. 고정 주소가 필요하면 도메인·고정 터널 구성이 추가로 필요합니다.

## 배포 후 점검

1. Actions의 테스트·이미지·SSM 결과와 배포 커밋을 확인합니다.
2. 공개 HTTPS `/actuator/health`와 매장·메뉴 조회를 확인합니다.
3. 앱의 설정 주소·매장 코드가 유효한지 확인합니다.
4. 메뉴 수정 권한, 잘못된 키, 없는 매장, 메뉴 버전 갱신을 확인합니다.
5. 익명 통계의 중복 `event_id`, 잘못된 필드, 요청 한도를 검사합니다.
6. 장애 시 SSM·컨테이너·DB 상태와 로그를 확인하되 로그 공유 전에 키·개인정보를 가립니다.

## 모델·데이터 운영

`GET /api/models/latest`는 모델 버전과 파일 URL·해시·크기를 알려 줍니다. 모델 파일을 API 서버가 직접 중계하는 것과는 다릅니다. 현재 Android Whisper 설치기는 AI GitHub 릴리스에서 다운로드합니다. 서버 매니페스트만 바꿔도 앱의 고정 설치 URL이 자동 변경된다고 가정하지 않습니다.

통계 API는 허용된 익명 필드만 받으며 원본 영상·음성·위치·기기 ID를 추가하지 않습니다. DB 백업·복구, 접근 권한, 영구 HTTPS 주소와 실제 사용량 부하 검증은 운영 전 별도 점검이 필요합니다.
