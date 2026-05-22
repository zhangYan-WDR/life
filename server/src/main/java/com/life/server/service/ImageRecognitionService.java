package com.life.server.service;

import com.life.server.dto.response.RecipeImageRecognitionResponse;
import com.life.server.dto.response.ReceiptImageRecognitionResponse;

public interface ImageRecognitionService {

    ReceiptImageRecognitionResponse analyzeReceipt(byte[] imageBytes, String fileName);

    RecipeImageRecognitionResponse analyzeRecipeImage(byte[] imageBytes, String fileName);
}
