# LifeWise 本地启动示例（不保存真实 Key）
# 用法：复制为 start-local.ps1，再把占位符改成你自己的本地环境值。
# 注意：start-local.ps1 不要提交到 Git。

$env:AI_DASHSCOPE_KEY = "替换成你自己的 DashScope Key"
$env:AI_DASHSCOPE_MODEL = "qwen-plus"
$env:AI_DEEPSEEK_ENABLED = "false"
$env:SERVER_PORT = "8080"

Write-Host "Starting LifeWise local backend..."
Write-Host "Qwen configured:" ([bool]$env:AI_DASHSCOPE_KEY -and $env:AI_DASHSCOPE_KEY -notlike "替换成*")
Write-Host "DeepSeek enabled:" $env:AI_DEEPSEEK_ENABLED

Set-Location "$PSScriptRoot\backend"
mvn spring-boot:run
