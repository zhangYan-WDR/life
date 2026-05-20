package com.life.server.service.impl;

import com.life.server.common.BizException;
import com.life.server.service.ImageRecognitionService;
import org.springframework.stereotype.Service;

@Service
public class ImageRecognitionServiceImpl implements ImageRecognitionService {

    @Override
    public void analyzeReceipt() {
        throw new BizException("功能正在规划中，请先手动处理");
    }

    @Override
    public void analyzeRecipeImage() {
        throw new BizException("功能正在规划中，请先手动处理");
    }
}
