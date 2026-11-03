package com.zhiqin.zhiqinpicturebackend.analytics.interfaces;

import com.zhiqin.zhiqinpicturebackend.shared.interfaces.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.ResultUtils;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.*;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.*;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.analytics.application.SpaceAnalyzeAppService;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {
    @Resource
    private SpaceAnalyzeAppService SpaceAnalyzeAppService;
    @Resource
    private UserAppService UserAppService;

    /**
     * 获取空间使用状态
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyzeResponse(@RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, HttpServletRequest request){
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = SpaceAnalyzeAppService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }

    /**
     * 根据分类分析
     * @param spaceCategoryAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        List<SpaceCategoryAnalyzeResponse> resultList = SpaceAnalyzeAppService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 根据标签分析
     * @param spaceTagAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(@RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        List<SpaceTagAnalyzeResponse> resultList = SpaceAnalyzeAppService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 根据大小分析
     * @param spaceSizeAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        List<SpaceSizeAnalyzeResponse> resultList = SpaceAnalyzeAppService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 根据时间分析
     * @param spaceUserAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        List<SpaceUserAnalyzeResponse> resultList = SpaceAnalyzeAppService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 空间使用排行分析
     * @param spaceRankAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(@RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = UserAppService.getLoginUser(request);
        List<Space> resultList = SpaceAnalyzeAppService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

}
