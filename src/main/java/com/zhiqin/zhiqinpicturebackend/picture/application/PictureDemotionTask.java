package com.zhiqin.zhiqinpicturebackend.picture.application;

import cn.hutool.core.util.StrUtil;
import com.zhiqin.zhiqinpicturebackend.picture.domain.Picture;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureRepository;
import com.zhiqin.zhiqinpicturebackend.picture.domain.PictureTableRouter;
import com.zhiqin.zhiqinpicturebackend.storage.domain.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 冷数据降沉定时任务。
 *
 * <p>每天凌晨 3 点扫描全部图片分表（公共表 + 各空间表），把最近 30 天内
 * 浏览数没有任何变化的图片的 COS 存储类从标准存储降为低频存储——
 * 存储费大约降一半，代价是访问略慢、取回略贵，正适合"没人看的老图"。</p>
 *
 * <p>判断依据是 lastViewTime（reviews 最后一次变化的时间）；
 * 降沉过的图片被再次浏览时会在 {@code PictureAppServiceImpl#onPictureViewed} 自动回温。</p>
 *
 * <p>多实例部署时需要改为分布式锁或依赖 XXL-Job 等调度平台，避免重复扫描。</p>
 */
@Slf4j
@Component
public class PictureDemotionTask {

    /** 浏览数多少天没有变化就算冷数据 */
    private static final long COLD_DAYS = 30;

    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private PictureTableRouter pictureTableRouter;

    @Resource
    private StorageService storageService;

    /**
     * cron 支持配置覆盖（picture.demote.cron），默认每天 03:00。
     */
    @Scheduled(cron = "${picture.demote.cron:0 0 3 * * ?}")
    public void scheduled() {
        demoteColdPictures();
    }

    /**
     * 扫描所有分表并降沉冷图片。公开方法便于运维手动触发或测试。
     */
    public void demoteColdPictures() {
        Date threshold = Date.from(LocalDateTime.now().minusDays(COLD_DAYS).atZone(ZoneId.systemDefault()).toInstant());
        log.info("冷数据降沉任务开始，阈值：{} 之前未浏览的图片降至低频存储", threshold);
        int demotedCount = 0;
        int failedCount = 0;
        for (Long scope : pictureTableRouter.allScopes()) {
            List<Picture> coldPictures = pictureRepository.listColdPictures(threshold, scope);
            for (Picture picture : coldPictures) {
                try {
                    storageService.demoteToLowFrequency(picture.getUrl());
                    if (StrUtil.isNotBlank(picture.getThumbnailUrl())) {
                        storageService.demoteToLowFrequency(picture.getThumbnailUrl());
                    }
                    pictureRepository.markDemoted(picture.getId(), true);
                    demotedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("图片降沉失败，pictureId={} url={}", picture.getId(), picture.getUrl(), e);
                }
            }
        }
        log.info("冷数据降沉任务结束：本轮降沉 {} 张，失败 {} 张", demotedCount, failedCount);
    }
}
