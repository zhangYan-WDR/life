/* eslint-disable no-undef */
import request from "../../utils/request";

const STATUS_LABEL = {
  PENDING: "待表态",
  APPROVED: "已确认",
  REJECTED: "已取消",
};

const DECISION_LABEL = {
  PENDING: "待表态",
  APPROVED: "同意",
  REJECTED: "不同意",
};

Page({
  data: {
    id: null,
    detail: null,
    gap: null,
    comment: "",
  },

  onLoad(query) {
    this.setData({
      id: query.id,
    });
  },

  onShow() {
    if (this.data.id) {
      this.loadPage();
    }
  },

  async loadPage() {
    try {
      const detail = await request({ url: `/meal-requests/${this.data.id}` });
      const gap = await request({ url: `/meal-requests/${this.data.id}/ingredient-gap` });
      this.setData({
        detail: {
          ...detail,
          statusCls: (detail.status || "").toLowerCase(),
          statusLabel: STATUS_LABEL[detail.status] || detail.status,
          responses: (detail.responses || []).map((item) => ({
            ...item,
            decisionCls: (item.decision || "").toLowerCase(),
            decisionLabel: DECISION_LABEL[item.decision] || item.decision,
          })),
        },
        gap: {
          ...gap,
          items: (gap.items || []).map((item) => {
            const required = Number(item.requiredQuantity) || 0;
            const available = Number(item.availableQuantity) || 0;
            let fillPercent = 0;
            if (item.status === "SUFFICIENT") {
              fillPercent = 100;
            } else if (item.status === "INSUFFICIENT" && required > 0) {
              fillPercent = Math.min(100, Math.round((available / required) * 100));
            }
            return {
              ...item,
              statusCls: (item.status || "").toLowerCase(),
              statusText: item.status === "SUFFICIENT"
                ? "充足"
                : item.status === "INSUFFICIENT"
                  ? `缺 ${item.missingQuantity}${item.requiredUnit}`
                  : "未找到",
              fillPercent,
            };
          }),
        },
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  onCommentInput(e) {
    this.setData({
      comment: e.detail.value,
    });
  },

  async respond(e) {
    try {
      await request({
        url: `/meal-requests/${this.data.id}/respond`,
        method: "POST",
        data: {
          decision: e.currentTarget.dataset.decision,
          comment: this.data.comment,
        },
      });
      this.setData({
        comment: "",
      });
      this.loadPage();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  createFromAgain() {
    const recipeId = this.data.detail && this.data.detail.recipes && this.data.detail.recipes.length
      ? this.data.detail.recipes[0].recipeId
      : "";
    wx.navigateTo({
      url: recipeId ? `/pages/meal-request-edit/index?recipeId=${recipeId}` : "/pages/meal-request-edit/index",
    });
  },
});
