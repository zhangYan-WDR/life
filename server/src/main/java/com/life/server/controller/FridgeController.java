package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.ConsumeFridgeItemRequest;
import com.life.server.dto.request.UpsertFridgeItemRequest;
import com.life.server.dto.response.FridgeItemView;
import com.life.server.dto.response.FridgeReminderSummaryResponse;
import com.life.server.security.AuthContext;
import com.life.server.service.FridgeService;
import com.life.server.service.ReminderService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fridge")
public class FridgeController {

    private final FridgeService fridgeService;
    private final ReminderService reminderService;

    public FridgeController(FridgeService fridgeService, ReminderService reminderService) {
        this.fridgeService = fridgeService;
        this.reminderService = reminderService;
    }

    @GetMapping("/items")
    public ApiResponse<List<FridgeItemView>> getItems(@RequestParam(defaultValue = "ALL") String status) {
        return ApiResponse.success(fridgeService.listItems(AuthContext.getUserId(), status));
    }

    @PostMapping("/items")
    public ApiResponse<FridgeItemView> createItem(@Valid @RequestBody UpsertFridgeItemRequest request) {
        return ApiResponse.success(fridgeService.saveItem(AuthContext.getUserId(), request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<FridgeItemView> updateItem(@PathVariable Long id, @Valid @RequestBody UpsertFridgeItemRequest request) {
        request.setId(id);
        return ApiResponse.success(fridgeService.saveItem(AuthContext.getUserId(), request));
    }

    @PostMapping("/items/{id}/consume")
    public ApiResponse<FridgeItemView> consumeItem(@PathVariable Long id, @Valid @RequestBody ConsumeFridgeItemRequest request) {
        return ApiResponse.success(fridgeService.consumeItem(AuthContext.getUserId(), id, request));
    }

    @PostMapping("/items/{id}/discard")
    public ApiResponse<FridgeItemView> discardItem(@PathVariable Long id) {
        return ApiResponse.success(fridgeService.discardItem(AuthContext.getUserId(), id));
    }

    @GetMapping("/reminders/summary")
    public ApiResponse<FridgeReminderSummaryResponse> getReminderSummary() {
        return ApiResponse.success(fridgeService.getReminderSummary(AuthContext.getUserId()));
    }

    @PostMapping("/reminders/test")
    public ApiResponse<Void> triggerTestReminder() {
        boolean sent = reminderService.triggerTestReminder(AuthContext.getUserId());
        if (sent) {
            return ApiResponse.success("测试提醒已触发", null);
        }
        return ApiResponse.fail("测试提醒发送失败，请检查微信模板配置");
    }
}
