package com.zhiqin.zhiqinpicturebackend.manager;
import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;

import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.zhiqin.zhiqinpicturebackend.config.CosConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {


    @Resource
    private CosConfig cosConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) throws Exception {

        PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucket(), key, file);

        return cosClient.putObject(putObjectRequest);


    }

    public COSObject getObject(String key) throws Exception {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /*上传并解析*/
    public PutObjectResult putPictureObject(String key, File file) throws Exception {

        PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucket(), key, file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1); //1表示原图信息
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 小图(低于阈值)不处理,直接用原图,避免 webp 转换后体积反而变大
        if (file.length() > 20 * 1024) {
            //图片压缩(转换为webp格式)
            String webpKey = FileUtil.mainName(key)+".webp";
            PicOperations.Rule compressRule = new PicOperations.Rule();
            compressRule.setRule("imageMogr2/format/webp");
            compressRule.setBucket(cosConfig.getBucket());
            compressRule.setFileId(webpKey);
            rules.add(compressRule);

            //缩略图处理
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosConfig.getBucket());
            String thumbnailKtey  = FileUtil.mainName(key)+".thumbnail"+FileUtil.mainName(key);
            thumbnailRule.setFileId(thumbnailKtey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>",512,512));
            rules.add(thumbnailRule);
        }
        //构造处理函数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);

        //图片处理
        return  cosClient.putObject(putObjectRequest);


    }

    public void deleteObject(String key) throws Exception {
        DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(cosConfig.getBucket(), key);
        cosClient.deleteObject(deleteObjectRequest);
    }
}
