@echo off
cd /d C:\lvyou\frontend
npx vite --host 0.0.0.0 > run.log 2> run.err.log
