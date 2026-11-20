#!/usr/bin/env python3
"""
Aile Ekranı - aracı (relay) sunucu. Sadece Python 3.9+ standart kütüphanesi gerekir.

Çalıştırma:
    AILE_KEY="uzun-gizli-bir-anahtar" \
    TELEGRAM_TOKEN="123:ABC..." TELEGRAM_CHAT_ID="123456789" \
    python3 server.py

Ortam değişkenleri:
    AILE_KEY           (zorunlu) iki telefonla ortak anahtar
    TELEGRAM_TOKEN     BotFather'dan alınan bot token'ı (kilit bildirimi için)
    TELEGRAM_CHAT_ID   bildirimin gideceği sohbet numarası
    PORT               varsayılan 8080
    TZ_NAME            varsayılan Europe/Istanbul
"""
import hmac
import json
import os
import sys
import threading
import time
import urllib.parse
import urllib.request
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from zoneinfo import ZoneInfo

KEY = os.environ.get("AILE_KEY", "")
PORT = int(os.environ.get("PORT", "8080"))
TG_TOKEN = os.environ.get("TELEGRAM_TOKEN", "")
TG_CHAT = os.environ.get("TELEGRAM_CHAT_ID", "")
try:
    TZ = ZoneInfo(os.environ.get("TZ_NAME", "Europe/Istanbul"))
except Exception:
    TZ = None
MAX_FRAME = 2_000_000
CHILD_TIMEOUT = 10   # sn: çocuk bu kadar süre ses vermezse "bağlı değil"
WATCH_TIMEOUT = 8    # sn: ebeveyn bu kadar süre sorgulamazsa "izlemiyor"

cond = threading.Condition()
state = {
    "seq": int(time.time() * 1000),  # sunucu yeniden başlasa da artan sayı
    "frame": None,
    "frame_ts": 0.0,
    "child_seen": 0.0,
    "watch_seen": 0.0,
    "events": [],
}


def fmt_time(ts_ms=None):
    t = datetime.fromtimestamp((ts_ms / 1000.0) if ts_ms else time.time(), TZ)
    return t.strftime("%d.%m.%Y %H:%M:%S")


def telegram(text):
    if not (TG_TOKEN and TG_CHAT):
        print("[telegram ayarlı değil] " + text)
        return
    try:
        data = urllib.parse.urlencode({"chat_id": TG_CHAT, "text": text}).encode()
        urllib.request.urlopen(
            "https://api.telegram.org/bot%s/sendMessage" % TG_TOKEN, data=data, timeout=15
        ).read()
    except Exception as e:
        print("Telegram hatası:", e)


class Handler(BaseHTTPRequestHandler):
    server_version = "AileEkrani"

    def log_message(self, fmt, *args):
        pass  # sessiz

    def _send(self, code, body=b"", ctype="text/plain; charset=utf-8", headers=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _auth(self):
        k = self.headers.get("X-Key", "")
        if hmac.compare_digest(k.encode(), KEY.encode()):
            return True
        self._send(401, b"yetkisiz")
        return False

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        if u.path == "/":
            return self._send(200, "Aile Ekrani sunucusu calisiyor".encode())
        if not self._auth():
            return
        now = time.time()

        if u.path == "/cmd":  # çocuk telefonu sorar: "izleniyor muyum?"
            state["child_seen"] = now
            return self._send(200, b"1" if now - state["watch_seen"] < WATCH_TIMEOUT else b"0")

        if u.path == "/frame":  # ebeveyn telefonu: yeni kare bekler (long-poll)
            try:
                since = int(q.get("since", ["0"])[0])
            except ValueError:
                since = 0
            state["watch_seen"] = now
            with cond:
                cond.wait_for(lambda: state["seq"] > since and state["frame"] is not None, timeout=6)
                seq, frame, fts = state["seq"], state["frame"], state["frame_ts"]
            state["watch_seen"] = time.time()
            child = "1" if time.time() - state["child_seen"] < CHILD_TIMEOUT else "0"
            if frame is None or seq <= since:
                return self._send(204, b"", headers={"X-Child": child})
            return self._send(200, frame, "image/jpeg", {
                "X-Seq": str(seq),
                "X-Age": "%.1f" % (time.time() - fts),
                "X-Child": child,
            })

        if u.path == "/status":
            ev = state["events"]
            body = json.dumps({
                "child_online": now - state["child_seen"] < CHILD_TIMEOUT,
                "watching": now - state["watch_seen"] < WATCH_TIMEOUT,
                "last_unlock": ev[-1] if ev else "",
                "recent_unlocks": ev[-20:],
            }, ensure_ascii=False).encode()
            return self._send(200, body, "application/json; charset=utf-8")

        self._send(404, b"yok")

    def do_POST(self):
        if not self._auth():
            return
        u = urllib.parse.urlparse(self.path)
        try:
            n = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            n = 0
        if n > MAX_FRAME:
            return self._send(413, b"cok buyuk")
        body = self.rfile.read(n) if n else b""

        if u.path == "/frame":
            with cond:
                state["frame"] = body
                state["frame_ts"] = time.time()
                state["seq"] += 1
                cond.notify_all()
            state["child_seen"] = time.time()
            return self._send(200, b"ok")

        if u.path == "/event":
            try:
                ev = json.loads(body.decode("utf-8"))
            except Exception:
                return self._send(400, b"bozuk json")
            if ev.get("type") == "unlock":
                when = fmt_time(ev.get("ts"))
                state["events"].append(when)
                state["events"] = state["events"][-200:]
                state["child_seen"] = time.time()
                print("Kilit açıldı:", when)
                threading.Thread(
                    target=telegram,
                    args=("🔓 Çocuğun telefonu açıldı (%s)\nEkranı görmek için Aile Ekranı uygulamasını aç." % when[11:],),
                    daemon=True,
                ).start()
            return self._send(200, b"ok")

        self._send(404, b"yok")


def main():
    if not KEY or len(KEY) < 8:
        sys.exit("AILE_KEY ortam değişkenini (en az 8 karakter) ayarla.")
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    srv.daemon_threads = True
    print("Aile Ekranı sunucusu :%d portunda çalışıyor. Telegram: %s" % (
        PORT, "açık" if (TG_TOKEN and TG_CHAT) else "ayarlı değil"))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
