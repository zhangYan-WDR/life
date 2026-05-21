package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeCoverUploadPolicyResponse {

    private String uploadHost;
    private String accessKeyId;
    private String policy;
    private String signature;
    private String objectKey;
    private String publicUrl;
    private String successActionStatus;
    private Long expireAt;
    private Long maxSizeBytes;
}
