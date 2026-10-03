"""Bound input before JSON parsing and keep private API replies out of caches."""
from fastapi.responses import JSONResponse

from . import config


class RequestBodyLimitMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["method"] not in {"POST", "PUT", "PATCH"}:
            return await self.app(scope, receive, send)
        headers = dict(scope.get("headers", []))
        try:
            content_length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            return await JSONResponse({"detail": "Invalid Content-Length"}, status_code=400)(scope, receive, send)
        chunks, size = [], 0
        if content_length < 0:
            return await JSONResponse({"detail": "Invalid Content-Length"}, status_code=400)(scope, receive, send)
        if content_length > config.MAX_REQUEST_BYTES:
            return await JSONResponse({"detail": "요청이 너무 큽니다"}, status_code=413)(scope, receive, send)
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            size += len(chunk)
            if size > config.MAX_REQUEST_BYTES:
                return await JSONResponse({"detail": "요청이 너무 큽니다"}, status_code=413)(scope, receive, send)
            chunks.append(chunk)
            if not message.get("more_body", False):
                break
        body = b"".join(chunks)
        delivered = False

        async def limited_receive():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": body, "more_body": False}
            return await receive()

        return await self.app(scope, limited_receive, send)


class SecurityHeadersMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        async def secured_send(message):
            if message["type"] == "http.response.start":
                headers = list(message.get("headers", []))
                headers.extend([(b"x-content-type-options", b"nosniff"), (b"x-frame-options", b"DENY"),
                                (b"referrer-policy", b"no-referrer"),
                                (b"content-security-policy", b"frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'")])
                if config.PRODUCTION:
                    headers.append((b"strict-transport-security", b"max-age=31536000"))
                private = (scope.get("method") not in {"GET", "HEAD"} or
                           scope.get("path", "") == "/api/stores" or
                           scope.get("path", "").startswith("/api/stats/summary") or
                           any(k.lower() in {b"x-owner-key", b"x-admin-key"} for k, _ in scope.get("headers", [])))
                if private:
                    headers = [(k, v) for k, v in headers if k.lower() != b"cache-control"]
                    headers.append((b"cache-control", b"no-store"))
                message = {**message, "headers": headers}
            await send(message)
        await self.app(scope, receive, secured_send)
