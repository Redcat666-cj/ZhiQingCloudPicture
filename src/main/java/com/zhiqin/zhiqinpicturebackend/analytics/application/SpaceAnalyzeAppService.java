package com.zhiqin.zhiqinpicturebackend.analytics.application;

import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.dto.*;
import com.zhiqin.zhiqinpicturebackend.space.domain.Space;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.analytics.interfaces.vo.*;

import java.util.List;

public interface SpaceAnalyzeAppService {

    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest request, User loginUser);

    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
