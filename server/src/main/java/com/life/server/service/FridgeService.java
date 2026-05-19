package com.life.server.service;

import com.life.server.dto.request.ConsumeFridgeItemRequest;
import com.life.server.dto.request.UpsertFridgeItemRequest;
import com.life.server.dto.response.FridgeItemView;
import com.life.server.dto.response.FridgeReminderSummaryResponse;
import java.util.List;

public interface FridgeService {

    List<FridgeItemView> listItems(Long userId, String status);

    FridgeItemView saveItem(Long userId, UpsertFridgeItemRequest request);

    FridgeItemView consumeItem(Long userId, Long itemId, ConsumeFridgeItemRequest request);

    FridgeItemView discardItem(Long userId, Long itemId);

    FridgeReminderSummaryResponse getReminderSummary(Long userId);
}
