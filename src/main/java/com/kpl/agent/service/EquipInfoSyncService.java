package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpl.agent.entity.EquipBaseInfo;
import com.kpl.agent.entity.InscriptionInfo;
import com.kpl.agent.mapper.EquipBaseInfoMapper;
import com.kpl.agent.mapper.InscriptionInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 游戏静态数据同步服务：从 pvp.qq.com 爬取装备、铭文等基础数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipInfoSyncService {

    private final EquipBaseInfoMapper equipBaseInfoMapper;
    private final InscriptionInfoMapper inscriptionInfoMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String ITEM_URL = "https://pvp.qq.com/web201605/js/item.json";
    private static final String MING_URL = "https://pvp.qq.com/web201605/js/ming.json";

    /** 同步装备基础信息（价格、属性等） */
    public int syncEquipInfo() {
        log.info("开始同步装备基础信息: {}", ITEM_URL);
        try {
            String json = restTemplate.getForObject(ITEM_URL, String.class);
            JsonNode root = objectMapper.readTree(json);

            // item.json 返回纯 JSON 数组
            if (!root.isArray()) {
                log.error("item.json 返回格式异常，不是数组");
                return 0;
            }

            int synced = 0;
            for (JsonNode item : root) {
                int itemId = item.path("item_id").asInt(0);
                if (itemId == 0) continue;

                String itemName = item.path("item_name").asText("");
                int itemType = item.path("item_type").asInt(0);
                int price = item.path("price").asInt(0);
                int totalPrice = item.path("total_price").asInt(0);
                String des1 = item.path("des1").asText("");
                String des2 = item.path("des2").asText("");

                // upsert: 先查是否存在
                EquipBaseInfo existing = equipBaseInfoMapper.selectOne(
                        new LambdaQueryWrapper<EquipBaseInfo>()
                                .eq(EquipBaseInfo::getItemId, itemId)
                                .last("LIMIT 1"));

                if (existing != null) {
                    existing.setItemName(itemName);
                    existing.setItemType(itemType);
                    existing.setPrice(price);
                    existing.setTotalPrice(totalPrice);
                    existing.setDes1(des1);
                    existing.setDes2(des2);
                    equipBaseInfoMapper.updateById(existing);
                } else {
                    EquipBaseInfo info = new EquipBaseInfo();
                    info.setItemId(itemId);
                    info.setItemName(itemName);
                    info.setItemType(itemType);
                    info.setPrice(price);
                    info.setTotalPrice(totalPrice);
                    info.setDes1(des1);
                    info.setDes2(des2);
                    equipBaseInfoMapper.insert(info);
                }
                synced++;
            }

            log.info("装备基础信息同步完成: {} 件装备", synced);
            return synced;
        } catch (Exception e) {
            log.error("同步装备基础信息失败", e);
            return 0;
        }
    }

    /** 同步铭文基础信息 */
    public int syncInscriptionInfo() {
        log.info("开始同步铭文基础信息: {}", MING_URL);
        try {
            String json = restTemplate.getForObject(MING_URL, String.class);
            JsonNode root = objectMapper.readTree(json);

            if (!root.isArray()) {
                log.error("ming.json 返回格式异常，不是数组");
                return 0;
            }

            int synced = 0;
            for (JsonNode item : root) {
                String mingId = item.path("ming_id").asText("");
                if (mingId.isEmpty()) continue;

                String mingName = item.path("ming_name").asText("");
                String mingType = item.path("ming_type").asText("");
                int mingGrade = item.path("ming_grade").asInt(0);
                String mingDes = item.path("ming_des").asText("");

                InscriptionInfo existing = inscriptionInfoMapper.selectOne(
                        new LambdaQueryWrapper<InscriptionInfo>()
                                .eq(InscriptionInfo::getMingId, mingId)
                                .last("LIMIT 1"));

                if (existing != null) {
                    existing.setMingName(mingName);
                    existing.setMingType(mingType);
                    existing.setMingGrade(mingGrade);
                    existing.setMingDes(mingDes);
                    inscriptionInfoMapper.updateById(existing);
                } else {
                    InscriptionInfo info = new InscriptionInfo();
                    info.setMingId(mingId);
                    info.setMingName(mingName);
                    info.setMingType(mingType);
                    info.setMingGrade(mingGrade);
                    info.setMingDes(mingDes);
                    inscriptionInfoMapper.insert(info);
                }
                synced++;
            }

            log.info("铭文基础信息同步完成: {} 个铭文", synced);
            return synced;
        } catch (Exception e) {
            log.error("同步铭文基础信息失败", e);
            return 0;
        }
    }
}
