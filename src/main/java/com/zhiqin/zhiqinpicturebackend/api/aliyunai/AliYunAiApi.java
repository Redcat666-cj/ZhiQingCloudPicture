package com.zhiqin.zhiqinpicturebackend.api.aliyunai;


import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.exception.BusinessException;
import com.zhiqin.zhiqinpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {
    @Value("${aliYunAi.apikey}")
    private String apiKey;

    /**
     * image-out-painting 已从当前账号的模型列表下架。
     * 改用仍可用的万相图像编辑模型，按扩展比例生成更大的画面。
     */
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation";

    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    private static final String OUT_PAINTING_MODEL = "wan2.7-image";

    /**
     * 创建扩图任务。xScale、yScale 为相对原图的扩展倍数，范围建议 1.0 到 3.0。
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(String imageUrl, float xScale, float yScale, Integer width, Integer height, String userPrompt) {
        if (StringUtils.isBlank(imageUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "扩图图片地址为空");
        }
        String prompt = StringUtils.isNotBlank(userPrompt)
                ? userPrompt.trim()
                : String.format(
                "对图1做画面扩展，只在四周自然补全场景，保持中心主体、风格和光线不变。水平方向扩展为原来的%.1f倍，垂直方向扩展为原来的%.1f倍。不要添加文字。",
                xScale, yScale);
        JSONObject image = new JSONObject();
        image.set("image", imageUrl);
        JSONObject text = new JSONObject();
        text.set("text", prompt);
        JSONArray content = new JSONArray();
        content.add(image);
        content.add(text);
        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", content);
        JSONArray messages = new JSONArray();
        messages.add(message);
        JSONObject input = new JSONObject();
        input.set("messages", messages);
        JSONObject parameters = new JSONObject();
        parameters.set("size", buildOutputSize(width, height, xScale, yScale));
        parameters.set("n", 1);
        parameters.set("watermark", false);
        JSONObject body = new JSONObject();
        body.set("model", OUT_PAINTING_MODEL);
        body.set("input", input);
        body.set("parameters", parameters);

        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(body.toString());

        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, readErrorMessage(httpResponse.body()));
            }
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (StringUtils.isNotBlank(response.getCode())) {
                log.error(response.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, response.getMessage());
            }
            return response;
        }
    }

    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务id不能为空");
        }
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, readErrorMessage(httpResponse.body()));
            }
            String responseBody = httpResponse.body();
            GetOutPaintingTaskResponse response = JSONUtil.toBean(responseBody, GetOutPaintingTaskResponse.class);
            fillOutputImageUrl(response, responseBody);
            return response;
        }
    }

    /**
     * 万相异步结果把图片放在 choices 里，转成原来的 outputImageUrl，前端轮询不用改。
     */
    private void fillOutputImageUrl(GetOutPaintingTaskResponse response, String responseBody) {
        if (response.getOutput() == null || StringUtils.isNotBlank(response.getOutput().getOutputImageUrl())) {
            return;
        }
        JSONObject output = JSONUtil.parseObj(responseBody).getJSONObject("output");
        if (output == null) {
            return;
        }
        JSONArray choices = output.getJSONArray("choices");
        if (choices == null) {
            return;
        }
        for (int i = 0; i < choices.size(); i++) {
            JSONObject message = choices.getJSONObject(i).getJSONObject("message");
            if (message == null) {
                continue;
            }
            JSONArray content = message.getJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.size(); j++) {
                JSONObject item = content.getJSONObject(j);
                if ("image".equals(item.getStr("type")) && StringUtils.isNotBlank(item.getStr("image"))) {
                    response.getOutput().setOutputImageUrl(item.getStr("image"));
                    return;
                }
            }
        }
    }

    private String buildOutputSize(Integer width, Integer height, float xScale, float yScale) {
        double targetW = (width == null || width < 1 ? 1024 : width) * xScale;
        double targetH = (height == null || height < 1 ? 1024 : height) * yScale;
        double aspect = targetW / targetH;
        if (aspect > 8) {
            targetW = targetH * 8;
        } else if (aspect < 0.125) {
            targetH = targetW * 8;
        }
        double pixels = targetW * targetH;
        double maxPixels = 2048d * 2048d;
        double minPixels = 768d * 768d;
        if (pixels > maxPixels) {
            double scale = Math.sqrt(maxPixels / pixels);
            targetW *= scale;
            targetH *= scale;
        } else if (pixels < minPixels) {
            double scale = Math.sqrt(minPixels / pixels);
            targetW *= scale;
            targetH *= scale;
        }
        return Math.max(1, (int) Math.round(targetW)) + "*" + Math.max(1, (int) Math.round(targetH));
    }

    private String readErrorMessage(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return "Ai扩图失败";
        }
        String message = JSONUtil.parseObj(responseBody).getStr("message");
        return StringUtils.isNotBlank(message) ? message : "Ai扩图失败";
    }
}
