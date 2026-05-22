package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.response.RecipeImageRecognitionResponse;
import com.life.server.dto.response.ReceiptImageRecognitionResponse;
import com.life.server.service.ImageRecognitionService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/image-recognition")
public class ImageRecognitionController {

    private final ImageRecognitionService imageRecognitionService;

    public ImageRecognitionController(ImageRecognitionService imageRecognitionService) {
        this.imageRecognitionService = imageRecognitionService;
    }

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReceiptImageRecognitionResponse> analyzeReceipt(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(imageRecognitionService.analyzeReceipt(file.getBytes(), file.getOriginalFilename()));
    }

    @PostMapping(value = "/recipe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecipeImageRecognitionResponse> analyzeRecipe(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(imageRecognitionService.analyzeRecipeImage(file.getBytes(), file.getOriginalFilename()));
    }
}
