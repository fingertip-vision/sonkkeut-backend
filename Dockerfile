FROM python:3.12-slim
WORKDIR /srv
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 APP_ENV=production
COPY requirements.lock.txt .
RUN pip install --no-cache-dir -r requirements.lock.txt \
    && useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser
COPY --chown=appuser:appuser app ./app
USER appuser
# Forwarded addresses are not an authentication or quota trust boundary.
# Render's proxy peer shares a bounded quota; one worker keeps it consistent.
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-10000} --workers 1 --no-proxy-headers --no-server-header --limit-concurrency 64 --timeout-keep-alive 5"]
