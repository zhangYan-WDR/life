package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceiptImageRecognitionResponse {

    private String rawText;
    private List<String> lines;
    private List<ReceiptRecognizedItemResponse> items;
}
