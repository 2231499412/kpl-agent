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
    REQUEST_DELAY, BATCH_REST_SECONDS, REQUESTS_PER_BATCH,
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
        try:
            self.conn = pymysql.connect(**DB_CONFIG)
            logger.info("数据库连接成功")
        except pymysql.Error as e:
            logger.error(f"数据库连接失败: {e}")
            raise

    def close_db(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
            logger.info("数据库连接关闭")

    def load_progress(self):
        """加载进度"""
        default_progress = {
            'last_processed_league_id': None,
            'last_processed_match_id': None,
            'total_processed': 0,
            'total_matched': 0
        }
        try:
            if Path(PROGRESS_FILE).exists():
                with open(PROGRESS_FILE, 'r', encoding='utf-8') as f:
                    return json.load(f)
        except (json.JSONDecodeError, IOError) as e:
            logger.warning(f"加载进度文件失败，使用默认进度: {e}")
        return default_progress

    def save_progress(self):
        """保存进度"""
        try:
            with open(PROGRESS_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.progress, f, ensure_ascii=False, indent=2)
        except IOError as e:
            logger.error(f"保存进度文件失败: {e}")

    def load_video_list(self, league_keyword):
        """
        从本地文件加载B站视频列表
        返回: [{'bvid': 'BV1xxx', 'title': '标题'}, ...]
        """
        videos_file = Path(f"videos_{league_keyword}.json")
        logger.info(f"尝试加载视频列表文件: {videos_file}")

        # 检查文件是否存在
        if not videos_file.exists():
            logger.warning(f"视频列表文件不存在: {videos_file}")
            logger.warning(f"请按以下步骤创建视频列表文件:")
            logger.warning(f"  1. 访问 B站空间页面: https://space.bilibili.com/{BILIBILI_UID}/video")
            logger.warning(f"  2. 搜索关键词: {league_keyword}")
            logger.warning(f"  3. 提取视频列表，格式: [{{'bvid': 'BV1xxx', 'title': '标题'}}, ...]")
            logger.warning(f"  4. 保存到文件: {videos_file}")
            return []

        # 读取并验证文件
        try:
            with open(videos_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            # 验证数据格式
            if not isinstance(data, list):
                logger.error(f"视频列表文件格式错误: {videos_file}，期望JSON数组格式")
                return []

            # 验证每个视频条目
            valid_videos = []
            for i, item in enumerate(data):
                if not isinstance(item, dict):
                    logger.warning(f"跳过无效条目（索引{i}）: 非字典类型")
                    continue
                if 'bvid' not in item:
                    logger.warning(f"跳过无效条目（索引{i}）: 缺少bvid字段")
                    continue
                if 'title' not in item:
                    logger.warning(f"跳过无效条目（索引{i}）: 缺少title字段")
                    continue
                valid_videos.append(item)

            if not valid_videos:
                logger.warning(f"视频列表文件为空或所有条目无效: {videos_file}")
                return []

            logger.info(f"从文件加载了 {len(valid_videos)} 个视频")

        except json.JSONDecodeError as e:
            logger.error(f"视频列表文件JSON格式错误: {videos_file}")
            logger.error(f"错误详情: {e}")
            return []
        except IOError as e:
            logger.error(f"读取视频列表文件失败: {videos_file}, {e}")
            return []

        return valid_videos

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
        # 匹配 YYYY年M月D日
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
        cn_to_num = {
            '一': 1, '二': 2, '三': 3, '四': 4, '五': 5,
            '六': 6, '七': 7, '八': 8, '九': 9, '十': 10,
            '十一': 11, '十二': 12
        }

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
            else:
                # 尝试匹配纯数字（如"1"、"2"）
                match = re.match(r'^(\d+)$', part.strip())
                if match:
                    game_num = int(match.group(1))
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

            # 获取视频列表
            videos = self.load_video_list("KPL")

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

                    # 每批请求后休息
                    if processed % REQUESTS_PER_BATCH == 0:
                        logger.info(f"休息{BATCH_REST_SECONDS}秒...")
                        time.sleep(BATCH_REST_SECONDS)

                except Exception as e:
                    logger.error(f"处理视频失败: {e}")
                    continue

            logger.info(f"爬取完成: 处理{processed}个视频, 匹配{matched}个比赛")

        finally:
            self.close_db()


if __name__ == '__main__':
    scraper = BilibiliScraper()
    scraper.run()
