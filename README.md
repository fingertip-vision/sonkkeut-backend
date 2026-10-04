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
