#!/usr/bin/env python3
"""
bilibili_full_scraper 单元测试
测试标题解析、队伍匹配、分P提取等核心方法
"""

import sys
from datetime import datetime, date
from unittest.mock import MagicMock
from pathlib import Path

import pytest

# 确保 scripts 目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).resolve().parent))

# 在 import bilibili_full_scraper 之前 mock 掉外部依赖
sys.modules.setdefault('pymysql', MagicMock())
sys.modules.setdefault('requests', MagicMock())

from bilibili_full_scraper import BilibiliScraper


@pytest.fixture
def scraper():
    """创建 BilibiliScraper 实例（不连接数据库）"""
    return BilibiliScraper()


# ============================================================
# extract_date_from_title 测试
# ============================================================

class TestExtractDateFromTitle:
    """测试从视频标题提取日期"""

    def test_full_date_yyyy_mm_dd(self, scraper):
        title = "【KPL】2024年3月15日 成都AG vs 重庆狼队"
        result = scraper.extract_date_from_title(title)
        assert result == date(2024, 3, 15)

    def test_full_date_yyyy_m_d_no_leading_zero(self, scraper):
        title = "【KPL】2024年1月5日 广州TTG vs 武汉eStarPro"
        result = scraper.extract_date_from_title(title)
        assert result == date(2024, 1, 5)

    def test_full_date_yyyy_12_31(self, scraper):
        title = "【KPL】2023年12月31日 年终总决赛"
        result = scraper.extract_date_from_title(title)
        assert result == date(2023, 12, 31)

    def test_partial_date_md(self, scraper):
        """仅包含月日时，年份取当前年"""
        title = "【KPL】6月20日 佛山DRG vs 南京Hero"
        result = scraper.extract_date_from_title(title)
        expected_year = datetime.now().year
        assert result == date(expected_year, 6, 20)

    def test_partial_date_single_digit_month(self, scraper):
        title = "【KPL】9月3日 比赛回放"
        result = scraper.extract_date_from_title(title)
        expected_year = datetime.now().year
        assert result == date(expected_year, 9, 3)

    def test_no_date_returns_none(self, scraper):
        title = "【KPL】精彩集锦 高光时刻"
        result = scraper.extract_date_from_title(title)
        assert result is None

    def test_empty_title(self, scraper):
        result = scraper.extract_date_from_title("")
        assert result is None

    def test_full_date_takes_priority_over_partial(self, scraper):
        """包含完整日期时，优先匹配 YYYY年M月D日"""
        title = "2025年6月1日 重播 6月1日活动"
        result = scraper.extract_date_from_title(title)
        assert result == date(2025, 6, 1)


# ============================================================
# extract_teams_from_title 测试
# ============================================================

class TestExtractTeamsFromTitle:
    """测试从视频标题提取队伍名称"""

    def test_standard_format(self, scraper):
        title = "【KPL】3月15日 成都AG VS 重庆狼队"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str == "3月15日"
        assert team1 == "成都AG"
        assert team2 == "重庆狼队"

    def test_simple_team_names(self, scraper):
        title = "【2024KPL春季赛】6月1日 TTG VS Hero"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str == "6月1日"
        assert team1 == "TTG"
        assert team2 == "Hero"

    def test_team_names_with_vs_uppercase(self, scraper):
        title = "【赛事】12月25日 广州TTG VS 武汉eStarPro"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str == "12月25日"
        assert team1 == "广州TTG"
        assert team2 == "武汉eStarPro"

    def test_no_match_returns_none(self, scraper):
        title = "【KPL】精彩集锦 十佳球"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str is None
        assert team1 is None
        assert team2 is None

    def test_empty_title(self, scraper):
        date_str, team1, team2 = scraper.extract_teams_from_title("")
        assert date_str is None
        assert team1 is None
        assert team2 is None

    def test_team_with_space_around_vs(self, scraper):
        """VS 前后有多个空格"""
        title = "【KPL】6月1日 佛山DRG  VS  重庆狼队"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str == "6月1日"
        assert team1 == "佛山DRG"
        assert team2 == "重庆狼队"

    def test_team_name_with_numbers(self, scraper):
        title = "【KPL】9月10日 AG超玩会 VS XYG"
        date_str, team1, team2 = scraper.extract_teams_from_title(title)
        assert date_str == "9月10日"
        assert team1 == "AG超玩会"
        assert team2 == "XYG"


# ============================================================
# match_team_name 测试
# ============================================================

class TestMatchTeamName:
    """测试队伍名称匹配"""

    def test_exact_match(self, scraper):
        assert scraper.match_team_name("成都AG", "成都AG") is True

    def test_subset_match_video_in_db(self, scraper):
        """视频标题中的队名是数据库队名的子串"""
        assert scraper.match_team_name("AG", "成都AG超玩会") is True

    def test_subset_match_db_in_video(self, scraper):
        """数据库队名是视频标题队名的子串"""
        assert scraper.match_team_name("成都AG超玩会", "AG") is True

    def test_city_prefix_removal(self, scraper):
        """去除城市前缀后匹配"""
        assert scraper.match_team_name("成都AG", "重庆AG") is True

    def test_city_prefix_different_teams(self, scraper):
        """去除城市前缀后不同队伍不匹配"""
        assert scraper.match_team_name("成都AG", "武汉eStar") is False

    def test_full_name_match(self, scraper):
        assert scraper.match_team_name("武汉eStarPro", "武汉eStarPro") is True

    def test_partial_match_with_city(self, scraper):
        assert scraper.match_team_name("佛山DRG", "佛山DRG Gaming") is True

    def test_empty_video_team(self, scraper):
        assert scraper.match_team_name("", "成都AG") is False

    def test_empty_db_team(self, scraper):
        assert scraper.match_team_name("成都AG", "") is False

    def test_both_empty(self, scraper):
        assert scraper.match_team_name("", "") is False

    def test_none_values(self, scraper):
        assert scraper.match_team_name(None, "成都AG") is False
        assert scraper.match_team_name("成都AG", None) is False

    def test_city_list_coverage(self, scraper):
        """验证多个城市前缀的去除匹配"""
        cases = [
            ("南京Hero", "Hero久竞"),
            ("杭州LGD", "LGD大鹅"),
            ("深圳DYG", "DYG"),
            ("广州TTG", "TTG"),
            ("上海EDG", "EDGM"),
            ("北京WB", "WB"),
            ("西安WE", "WE"),
            ("长沙滔搏", "滔搏"),
        ]
        for video_name, db_name in cases:
            assert scraper.match_team_name(video_name, db_name) is True, \
                f"Should match: {video_name} vs {db_name}"

    def test_no_false_positive(self, scraper):
        """不同队伍不应匹配"""
        assert scraper.match_team_name("成都AG", "武汉eStarPro") is False
        assert scraper.match_team_name("广州TTG", "南京Hero") is False


# ============================================================
# extract_game_pages 测试
# ============================================================

class TestExtractGamePages:
    """测试从分P列表提取比赛局数"""

    def test_chinese_numbers(self, scraper):
        """中文数字局数"""
        pages = [
            {'part': '第1局', 'page': 1},
            {'part': '第2局', 'page': 2},
            {'part': '第3局', 'page': 3},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 2, 3: 3}

    def test_chinese_numeral_text(self, scraper):
        """中文汉字局数（一二三）"""
        pages = [
            {'part': '第一局', 'page': 1},
            {'part': '第二局', 'page': 2},
            {'part': '第三局', 'page': 3},
            {'part': '第四局', 'page': 4},
            {'part': '第五局', 'page': 5},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 2, 3: 3, 4: 4, 5: 5}

    def test_pure_number_parts(self, scraper):
        """纯数字分P名称"""
        pages = [
            {'part': '1', 'page': 1},
            {'part': '2', 'page': 2},
            {'part': '3', 'page': 3},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 2, 3: 3}

    def test_mixed_format(self, scraper):
        """混合格式（中文+数字）"""
        pages = [
            {'part': '第一局', 'page': 1},
            {'part': '第2局', 'page': 2},
            {'part': '3', 'page': 3},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 2, 3: 3}

    def test_no_game_pages(self, scraper):
        """没有局数信息的分P"""
        pages = [
            {'part': '精彩集锦', 'page': 1},
            {'part': '赛后采访', 'page': 2},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {}

    def test_empty_pages(self, scraper):
        result = scraper.extract_game_pages([])
        assert result == {}

    def test_large_chinese_number(self, scraper):
        """较大中文数字"""
        pages = [
            {'part': '第十一局', 'page': 11},
            {'part': '第十二局', 'page': 12},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {11: 11, 12: 12}

    def test_mixed_with_irrelevant_parts(self, scraper):
        """包含无关分P的情况"""
        pages = [
            {'part': '第一局', 'page': 1},
            {'part': '精彩回放', 'page': 2},
            {'part': '第二局', 'page': 3},
            {'part': 'MVP集锦', 'page': 4},
            {'part': '第三局', 'page': 5},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 3, 3: 5}

    def test_part_with_surrounding_text(self, scraper):
        """分P名称中包含其他文字但有'第X局'"""
        pages = [
            {'part': '【第一局】成都AG vs 重庆狼队', 'page': 1},
            {'part': '【第二局】成都AG vs 重庆狼队', 'page': 2},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {1: 1, 2: 2}

    def test_missing_part_key(self, scraper):
        """缺少 part 字段的条目应被跳过"""
        pages = [
            {'page': 1},
            {'part': '第一局', 'page': 2},
        ]
        result = scraper.extract_game_pages(pages)
        # 第一条无part被跳过；第二条"第一局"映射为 game 1 -> page 2
        assert result == {1: 2}

    def test_bo7_series(self, scraper):
        """七局制比赛"""
        pages = [
            {'part': '第1局', 'page': 1},
            {'part': '第2局', 'page': 2},
            {'part': '第3局', 'page': 3},
            {'part': '第4局', 'page': 4},
            {'part': '第5局', 'page': 5},
            {'part': '第6局', 'page': 6},
            {'part': '第7局', 'page': 7},
        ]
        result = scraper.extract_game_pages(pages)
        assert result == {i: i for i in range(1, 8)}


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
