@echo off
echo ========================================
echo  LifeWise - MySQL 模式启动
echo ========================================
echo.
echo 第一步：启动 MySQL (Docker)
docker-compose up -d mysql
echo.
echo 等待 MySQL 就绪...
timeout /t 10
echo.
echo 第二步：启动后端 (MySQL profile)
cd backend
call mvn spring-boot:run -Dspring-boot.run.profiles=mysql