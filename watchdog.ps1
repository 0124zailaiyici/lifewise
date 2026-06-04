# LifeWise 服务守护脚本
$backendDir = "D:\demo\AI\codex\LifeWise\backend"
$frontendDir = "D:\demo\AI\codex\LifeWise\frontend"
while ($true) {
    $now = Get-Date -Format "HH:mm:ss"
    # 检查后端
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/users/login" -Method POST -ContentType "application/json" -Body '{"phone":"13800138000","password":"test123"}' -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Host "[$now] ✅ 后端"
    } catch {
        Write-Host "[$now] ❌ 后端重启..."
        Start-Process -WindowStyle Hidden -FilePath "mvn.cmd" -ArgumentList "spring-boot:run -q" -WorkingDirectory $backendDir
    }
    # 检查前端
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:5173/" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Host "[$now] ✅ 前端"
    } catch {
        Write-Host "[$now] ❌ 前端重启..."
        Start-Process -WindowStyle Hidden -FilePath "npx.cmd" -ArgumentList "vite --host 0.0.0.0" -WorkingDirectory $frontendDir
    }
    Start-Sleep -Seconds 15
}
