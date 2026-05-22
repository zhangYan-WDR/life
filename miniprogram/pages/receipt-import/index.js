/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const OCR_DRAFT_RECEIPT_KEY = "life_receipt_import_draft";

Page({
  data: {
    imagePath: "",
    items: [],
    rawText: "",
    location: "",
    note: "",
    saving: false,
  },

  onLoad() {
    const draft = localStorage.getItem(OCR_DRAFT_RECEIPT_KEY);
    if (!draft) {
      wx.showToast({ title: "没有找到识别结果", icon: "none" });
      return;
    }
    localStorage.removeItem(OCR_DRAFT_RECEIPT_KEY);
    this.setData({
      imagePath: draft.imagePath || "",
      rawText: draft.rawText || "",
      items: (draft.items || []).map((item) => ({
        name: item.name || "",
        quantity: item.quantity || "1",
        unit: item.unit || "份",
        enabled: true,
      })),
    });
  },

  onItemFieldInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`items[${index}].${field}`]: e.detail.value,
    });
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [field]: e.detail.value,
    });
  },

  toggleItem(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({
      [`items[${index}].enabled`]: !this.data.items[index].enabled,
    });
  },

  addItem() {
    this.setData({
      items: (this.data.items || []).concat([{
        name: "",
        quantity: "1",
        unit: "份",
        enabled: true,
      }]),
    });
  },

  removeItem(e) {
    const index = Number(e.currentTarget.dataset.index);
    const items = (this.data.items || []).slice();
    items.splice(index, 1);
    this.setData({ items });
  },

  async importItems() {
    if (this.data.saving) {
      return;
    }
    const enabledItems = (this.data.items || []).filter((item) => item.enabled && item.name);
    if (!enabledItems.length) {
      wx.showToast({ title: "至少保留一条食材", icon: "none" });
      return;
    }
    this.setData({ saving: true });
    wx.showLoading({ title: "正在导入...", mask: true });
    try {
      for (const item of enabledItems) {
        await request({
          url: "/fridge/items",
          method: "POST",
          data: {
            sourceType: "CUSTOM",
            customName: item.name,
            quantity: Number(item.quantity || 1),
            unit: item.unit || "份",
            producedAt: null,
            expiresAt: null,
            location: this.data.location,
            note: this.data.note,
          },
        });
      }
      wx.hideLoading();
      wx.showToast({ title: "已导入冰箱", icon: "success" });
      setTimeout(() => {
        wx.navigateBack();
      }, 500);
    } catch (error) {
      wx.hideLoading();
      wx.showToast({ title: error.message || "导入失败", icon: "none" });
    } finally {
      this.setData({ saving: false });
    }
  },
});
