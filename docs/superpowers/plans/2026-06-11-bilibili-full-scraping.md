# B站视频BV号全量爬取实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 爬取B站KPL官方账号的所有比赛视频，自动匹配数据库中的比赛，并获取每个视频的分P结构

**Architecture:** 使用Python脚本通过浏览器获取B站视频列表，调用B站API获取分P信息，与数据库比赛进行匹配

**Tech Stack:** Python 3.x, pymysql, requests, re, gstack browse/Playwright

---

## 文件结构

```
scripts/
├── bilibili_full_scraper.py      # 主爬取脚本
├── scraper_config.py             # 配置文件
└── test_scraper.py               # 测试文件
```

## 实现任务

### Task 1: 创建配置文件

**Files:**
- Create: `scripts/scraper_config.py`

- [ ] **Step 1: 创建配置文件**

```python
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
BATCH休息 = 5  # 每100次请求休息时间（秒）
REQUESTS_PER_BATCH = 100  # 每批请求数量

# 匹配规则
DATE_TOLERANCE_DAYS = 3  # 日期匹配容差（天）

# 日志配置
LOG_FILE = "scraper.log"
PROGRESS_FILE = "scraper_progress.json"
```

- [ ] **Step 2: 提交配置文件**

```bash
git add scripts/scraper_config.py
git commit -m "feat: 添加B站爬取配置文件"
```

---

### Task 2: 创建主爬取脚本框架

**Files:**
- Create: `scripts/bilibili_full_scraper.py`

- [ ] **Step 1: 创建脚本框架**

```python
#!/usr/bin/env python3
"""
B站视频BV号全量爬取脚本
爬取KPL官方账号的所有比赛视频，匹配数据库比赛，获取分P结构
"""

import json
import re
import time
import logging
from datetime import datetime, timedelta
from pathlib import Path

import pymysql
import requests

from scraper_config import (
    DB_CONFIG, BILIBILI_UID, BILIBILI_API_BASE,
    REQUEST_DELAY, BATCH休息, REQUESTS_PER_BATCH,
    DATE_TOLERANCE_DAYS, LOG_FILE, PROGRESS_FILE
)

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE, encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class BilibiliScraper:
    """B站视频爬取器"""
    
    def __init__(self):
        self.conn = None
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'https://www.bilibili.com'
        })
        self.progress = self.load_progress()
    
    def connect_db(self):
        """连接数据库"""
        self.conn = pymysql.connect(**DB_CONFIG)
        logger.info("数据库连接成功")
    
    def close_db(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
            logger.info("数据库连接关闭")
    
    def load_progress(self):
        """加载进度"""
        if Path(PROGRESS_FILE).exists():
            with open(PROGRESS_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        return {
            'last_processed_league_id': None,
            'last_processed_match_id': None,
            'total_processed': 0,
            'total_matched': 0
        }
    
    def save_progress(self):
        """保存进度"""
        with open(PROGRESS_FILE, 'w', encoding='utf-8') as f:
            json.dump(self.progress, f, ensure_ascii=False, indent=2)
    
    def fetch_video_list_from_browser(self, league_keyword):
        """
        使用浏览器获取B站视频列表
        这是一个占位方法，实际实现需要使用gstack browse或Playwright
        """
        # TODO: 实现浏览器爬取
        logger.warning("浏览器爬取功能尚未实现")
        return []
    
    def fetch_video_info(self, bvid):
        """获取单个视频的详细信息"""
        url = f"{BILIBILI_API_BASE}/x/web-interface/view?bvid={bvid}"
        try:
            response = self.session.get(url, timeout=10)
            data = response.json()
            if data.get('code') == 0:
                return data['data']
            else:
                logger.warning(f"获取视频信息失败: {bvid}, {data.get('message')}")
                return None
        except Exception as e:
            logger.error(f"请求失败: {bvid}, {e}")
            return None
    
    def extract_teams_from_title(self, title):
        """从视频标题提取队伍名称"""
        # 匹配模式：【赛事】日期 队伍1 VS 队伍2
        pattern = r'【.+?】(\d+月\d+日)\s+(.+?)\s+VS\s+(.+)'
        match = re.search(pattern, title)
        if match:
            date_str = match.group(1)
            team1 = match.group(2).strip()
            team2 = match.group(3).strip()
            return date_str, team1, team2
        return None, None, None
    
    def extract_date_from_title(self, title):
        """从视频标题提取日期"""
        # 匹配 YYYY年M月D日 或 M月D日
        pattern = r'(\d{4})年(\d{1,2})月(\d{1,2})日'
        match = re.search(pattern, title)
        if match:
            year = int(match.group(1))
            month = int(match.group(2))
            day = int(match.group(3))
            return datetime(year, month, day).date()
        
        # 匹配 M月D日
        pattern = r'(\d{1,2})月(\d{1,2})日'
        match = re.search(pattern, title)
        if match:
            month = int(match.group(1))
            day = int(match.group(2))
            # 假设是当前年份
            return datetime(datetime.now().year, month, day).date()
        
        return None
    
    def match_team_name(self, video_team, db_team):
        """匹配队伍名称"""
        if not video_team or not db_team:
            return False
        
        # 完全包含
        if video_team in db_team or db_team in video_team:
            return True
        
        # 去除城市前缀后匹配
        cities = ['成都', '武汉', '佛山', '南京', '杭州', '重庆', '深圳', '广州', '上海', '北京', '西安', '长沙']
        v_team = video_team
        b_team = db_team
        for city in cities:
            v_team = v_team.replace(city, '')
            b_team = b_team.replace(city, '')
        
        return v_team in b_team or b_team in v_team
    
    def find_matching_match(self, video_date, team1, team2):
        """查找匹配的比赛"""
        if not self.conn:
            return None
        
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            # 查询日期范围内的比赛
            start_date = video_date - timedelta(days=DATE_TOLERANCE_DAYS)
            end_date = video_date + timedelta(days=DATE_TOLERANCE_DAYS)
            
            cursor.execute("""
                SELECT match_id, camp1_team_name, camp2_team_name, start_time
                FROM `match`
                WHERE DATE(start_time) BETWEEN %s AND %s
                AND (camp1_team_name IS NOT NULL AND camp2_team_name IS NOT NULL)
            """, (start_date, end_date))
            
            matches = cursor.fetchall()
            
            for match in matches:
                # 验证队伍匹配
                if (self.match_team_name(team1, match['camp1_team_name']) and 
                    self.match_team_name(team2, match['camp2_team_name'])):
                    return match
                elif (self.match_team_name(team1, match['camp2_team_name']) and 
                      self.match_team_name(team2, match['camp1_team_name'])):
                    return match
        
        return None
    
    def extract_game_pages(self, pages):
        """从分P列表中提取比赛局数"""
        game_pages = {}
        cn_to_num = {'一':1, '二':2, '三':3, '四':4, '五':5, '六':6, '七':7, '八':8, '九':9, '十':10}
        
        for p in pages:
            part = p.get('part', '')
            # 匹配"第X局"
            match = re.search(r'第([一二三四五六七八九十\d]+)局', part)
            if match:
                cn_num = match.group(1)
                if cn_num in cn_to_num:
                    game_num = cn_to_num[cn_num]
                elif cn_num.isdigit():
                    game_num = int(cn_num)
                else:
                    continue
                game_pages[game_num] = p['page']
        
        return game_pages
    
    def update_battle_page_num(self, match_id, battle_seq, page_num):
        """更新比赛的page_num"""
        if not self.conn:
            return False
        
        with self.conn.cursor() as cursor:
            # 查找对应的battle_id
            cursor.execute("""
                SELECT battle_id FROM battle
                WHERE match_id = %s AND battle_seq = %s
            """, (match_id, battle_seq))
            
            result = cursor.fetchone()
            if result:
                battle_id = result[0]
                cursor.execute("""
                    UPDATE battle SET page_num = %s WHERE battle_id = %s
                """, (page_num, battle_id))
                self.conn.commit()
                return True
        
        return False
    
    def process_video(self, video_info):
        """处理单个视频"""
        bvid = video_info.get('bvid')
        title = video_info.get('title', '')
        
        # 提取日期和队伍
        video_date = self.extract_date_from_title(title)
        _, team1, team2 = self.extract_teams_from_title(title)
        
        if not video_date or not team1 or not team2:
            logger.warning(f"无法解析视频标题: {title}")
            return False
        
        # 查找匹配的比赛
        match = self.find_matching_match(video_date, team1, team2)
        if not match:
            logger.info(f"未找到匹配比赛: {title}")
            return False
        
        # 获取视频分P信息
        video_data = self.fetch_video_info(bvid)
        if not video_data:
            return False
        
        pages = video_data.get('pages', [])
        game_pages = self.extract_game_pages(pages)
        
        # 更新数据库
        match_id = match['match_id']
        updated_count = 0
        
        for battle_seq, page_num in game_pages.items():
            if self.update_battle_page_num(match_id, battle_seq, page_num):
                updated_count += 1
        
        logger.info(f"匹配成功: {title} -> {match_id}, 更新{updated_count}条记录")
        return True
    
    def run(self):
        """运行爬取"""
        logger.info("开始爬取B站视频...")
        
        try:
            self.connect_db()
            
            # TODO: 实现浏览器爬取视频列表
            # 这里需要先获取B站视频列表
            videos = self.fetch_video_list_from_browser("KPL")
            
            logger.info(f"获取到 {len(videos)} 个视频")
            
            processed = 0
            matched = 0
            
            for video in videos:
                try:
                    if self.process_video(video):
                        matched += 1
                    processed += 1
                    
                    # 保存进度
                    if processed % 10 == 0:
                        self.progress['total_processed'] = processed
                        self.progress['total_matched'] = matched
                        self.save_progress()
                    
                    # 控制请求频率
                    time.sleep(REQUEST_DELAY)
                    
                    # 每100次请求休息
                    if processed % REQUESTS_PER_BATCH == 0:
                        logger.info(f"休息{BATCH休息}秒...")
                        time.sleep(BATCH休息)
                
                except Exception as e:
                    logger.error(f"处理视频失败: {e}")
                    continue
            
            logger.info(f"爬取完成: 处理{processed}个视频, 匹配{matched}个比赛")
        
        finally:
            self.close_db()


if __name__ == '__main__':
    scraper = BilibiliScraper()
    scraper.run()
```

- [ ] **Step 2: 提交脚本框架**

```bash
git add scripts/bilibili_full_scraper.py
git commit -m "feat: 添加B站爬取脚本框架"
```

---

### Task 3: 实现浏览器爬取功能

**Files:**
- Modify: `scripts/bilibili_full_scraper.py:80-95`

- [ ] **Step 1: 实现浏览器爬取方法**

在 `BilibiliScraper` 类中添加以下方法：

```python
def fetch_video_list_from_browser(self, league_keyword):
    """
    使用浏览器获取B站视频列表
    返回: [{'bvid': 'BV1xxx', 'title': '标题', 'date': '日期'}, ...]
    """
    videos = []
    page = 1
    max_pages = 50  # 最大页数限制
    
    while page <= max_pages:
        url = f"https://space.bilibili.com/{BILIBILI_UID}/video?keyword={league_keyword}&pn={page}"
        logger.info(f"获取视频列表第{page}页: {url}")
        
        # 这里需要使用浏览器工具
        # 由于gstack browse工具的限制，我们需要使用其他方式
        # 暂时返回空列表，需要手动补充视频列表
        logger.warning(f"浏览器爬取需要手动实现，请使用以下方式获取视频列表:")
        logger.warning(f"1. 访问 {url}")
        logger.warning(f"2. 提取视频列表")
        logger.warning(f"3. 保存到 videos.json 文件")
        
        # 尝试从本地文件读取视频列表
        videos_file = Path(f"videos_{league_keyword}.json")
        if videos_file.exists():
            with open(videos_file, 'r', encoding='utf-8') as f:
                saved_videos = json.load(f)
                videos.extend(saved_videos)
                logger.info(f"从文件加载了 {len(saved_videos)} 个视频")
                break
        
        break  # 暂时只处理一页
    
    return videos
```

- [ ] **Step 2: 提交修改**

```bash
git add scripts/bilibili_full_scraper.py
git commit -m "feat: 实现浏览器爬取方法（占位）"
```

---

### Task 4: 实现分P获取和更新

**Files:**
- Modify: `scripts/bilibili_full_scraper.py:150-180`

- [ ] **Step 1: 完善分P提取逻辑**

确保 `extract_game_pages` 方法正确处理中文数字和阿拉伯数字：

```python
def extract_game_pages(self, pages):
    """从分P列表中提取比赛局数"""
    game_pages = {}
    cn_to_num = {
        '一':1, '二':2, '三':3, '四':4, '五':5,
        '六':6, '七':7, '八':8, '九':9, '十':10,
        '十一':11, '十二':12
    }
    
    for p in pages:
        part = p.get('part', '')
        # 匹配"第X局"或纯数字"1"、"2"等
        match = re.search(r'第([一二三四五六七八九十\d]+)局', part)
        if match:
            cn_num = match.group(1)
            if cn_num in cn_to_num:
                game_num = cn_to_num[cn_num]
            elif cn_num.isdigit():
                game_num = int(cn_num)
            else:
                continue
            game_pages[game_num] = p['page']
        else:
            # 尝试匹配纯数字（如"1"、"2"）
            match = re.match(r'^(\d+)$', part.strip())
            if match:
                game_num = int(match.group(1))
                game_pages[game_num] = p['page']
    
    return game_pages
```

- [ ] **Step 2: 提交修改**

```bash
git add scripts/bilibili_full_scraper.py
git commit -m "feat: 完善分P提取逻辑"
```

---

### Task 5: 创建测试文件

**Files:**
- Create: `scripts/test_scraper.py`

- [ ] **Step 1: 创建测试文件**

```python
#!/usr/bin/env python3
"""B站爬取脚本测试"""

import pytest
from bilibili_full_scraper import BilibiliScraper


@pytest.fixture
def scraper():
    """创建测试用的scraper实例"""
    return BilibiliScraper()


def test_extract_date_from_title(scraper):
    """测试从标题提取日期"""
    # 测试完整日期
    assert scraper.extract_date_from_title("【2026KPL春季赛】5月23日 决赛") is not None
    
    # 测试无日期
    assert scraper.extract_date_from_title("无日期标题") is None


def test_extract_teams_from_title(scraper):
    """测试从标题提取队伍"""
    title = "【2026KPL春季赛】5月23日 决赛 成都AG超玩会 vs 重庆狼队"
    date_str, team1, team2 = scraper.extract_teams_from_title(title)
    
    assert date_str == "5月23日"
    assert team1 == "成都AG超玩会"
    assert team2 == "重庆狼队"


def test_match_team_name(scraper):
    """测试队伍名称匹配"""
    # 完全匹配
    assert scraper.match_team_name("成都AG超玩会", "成都AG超玩会") == True
    
    # 部分匹配
    assert scraper.match_team_name("AG超玩会", "成都AG超玩会") == True
    
    # 不匹配
    assert scraper.match_team_name("AG超玩会", "北京WB") == False


def test_extract_game_pages(scraper):
    """测试分P提取"""
    pages = [
        {'page': 1, 'part': '第一局'},
        {'page': 2, 'part': '第二局'},
        {'page': 3, 'part': '第三局'},
        {'page': 4, 'part': '赛前评论席'},
    ]
    
    game_pages = scraper.extract_game_pages(pages)
    
    assert game_pages == {1: 1, 2: 2, 3: 3}


def test_extract_game_pages_cn_numbers(scraper):
    """测试中文数字分P提取"""
    pages = [
        {'page': 1, 'part': '一'},
        {'page': 2, 'part': '二'},
        {'page': 3, 'part': '三'},
    ]
    
    game_pages = scraper.extract_game_pages(pages)
    
    assert game_pages == {1: 1, 2: 2, 3: 3}


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
```

- [ ] **Step 2: 运行测试**

```bash
cd E:/kpl-data-agent/scripts
pytest test_scraper.py -v
```

- [ ] **Step 3: 提交测试文件**

```bash
git add scripts/test_scraper.py
git commit -m "test: 添加B站爬取脚本测试"
```

---

### Task 6: 创建视频列表文件

**Files:**
- Create: `scripts/videos_2026.json` (示例)

- [ ] **Step 1: 创建示例视频列表文件**

```json
[
  {
    "bvid": "BV16iGi6qEAv",
    "title": "【2026王者荣耀挑战者杯】5月23日 决赛 成都AG超玩会 vs 重庆狼队"
  },
  {
    "bvid": "BV11HLn6kE44",
    "title": "【2026王者荣耀挑战者杯】5月17日 重庆狼队 VS 北京JDG"
  },
  {
    "bvid": "BV1gqLG6bEjm",
    "title": "【2026王者荣耀挑战者杯】5月16日 北京JDG VS 成都AG超玩会"
  }
]
```

- [ ] **Step 2: 提交示例文件**

```bash
git add scripts/videos_2026.json
git commit -m "feat: 添加示例视频列表文件"
```

---

### Task 7: 运行完整爬取

**Files:**
- None (运行脚本)

- [ ] **Step 1: 准备视频列表**

手动从B站获取视频列表并保存到文件：
1. 访问 https://space.bilibili.com/392836434/video
2. 按赛季/年份筛选视频
3. 提取视频BV号和标题
4. 保存到 `videos_YYYY.json` 文件

- [ ] **Step 2: 运行爬取脚本**

```bash
cd E:/kpl-data-agent/scripts
python bilibili_full_scraper.py
```

- [ ] **Step 3: 验证结果**

```sql
-- 检查更新后的BV号覆盖情况
SELECT 
  (SELECT COUNT(DISTINCT match_id) FROM battle WHERE bvid IS NOT NULL) as matches_with_bvid,
  (SELECT COUNT(DISTINCT match_id) FROM battle) as total_matches,
  (SELECT COUNT(*) FROM battle WHERE bvid IS NOT NULL) as battles_with_bvid,
  (SELECT COUNT(*) FROM battle) as total_battles;
```

---

## 预期结果

- **覆盖率**：从0.6%提升到80%+
- **耗时**：全量爬取预计2-4小时
- **准确率**：自动匹配准确率约80%，剩余20%需要手动补充

## 注意事项

1. **B站风控**：控制请求频率，避免被封IP
2. **数据量大**：分批处理，支持断点续传
3. **匹配错误**：记录错误日志，支持手动修正
4. **浏览器限制**：由于B站API限制，需要手动获取视频列表
