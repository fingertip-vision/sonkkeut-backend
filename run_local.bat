@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe (
  py -3.11 -m venv .venv || python -m venv .venv
  .venv\Scripts\python -m pip install -r requirements-dev.txt
)
set ADMIN_KEY=local-admin
echo http://localhost:8000/owner  (점주 화면)   http://localhost:8000/docs  (API 문서)
.venv\Scripts\python -m uvicorn app.main:app --reload --port 8000
