package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientGroupView {

    private String key;
    private String category;
    private String secondaryCategory;
    private Integer count;
    private List<IngredientView> previewItems;
}
