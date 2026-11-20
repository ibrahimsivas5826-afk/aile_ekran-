@echo off
chcp 65001 >nul
REM === Aşağıdaki 3 satırı kendine göre düzenle ===
set AILE_KEY=buraya-en-az-8-karakter-gizli-anahtar
set TELEGRAM_TOKEN=buraya-botfather-token
set TELEGRAM_CHAT_ID=buraya-chat-id
REM ===============================================
cd /d "%~dp0"
python server.py
pause
