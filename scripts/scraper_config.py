# scraper_config.py
"""B站视频爬取配置"""

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'kpl_agent',
    'charset': 'utf8mb4',
}

# B站配置
BILIBILI_UID = 392836434  # KPL官方账号
BILIBILI_API_BASE = "https://api.bilibili.com"

# 请求频率控制
REQUEST_DELAY = 0.5  # 每次请求间隔（秒）
BATCH_REST_SECONDS = 5  # 每100次请求休息时间（秒）
REQUESTS_PER_BATCH = 100  # 每批请求数量

# 匹配规则
DATE_TOLERANCE_DAYS = 3  # 日期匹配容差（天）

# 日志配置
LOG_FILE = "scraper.log"
PROGRESS_FILE = "scraper_progress.json"
