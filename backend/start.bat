@echo off
set PATH=D:\develop\maven\apache-maven-3.9.8-bin\apache-maven-3.9.8\bin;D:\jdk-home\jdk-17\bin;%PATH%
cd /d D:\demo\AI\codex\LifeWise\backend
mvn spring-boot:run -q > app.log 2>&1
