/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const OCR_DRAFT_RECEIPT_KEY = "life_receipt_import_draft";

function pad2(n) {
  return n < 10 ? `0${n}` : String(n);
}

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function addDaysStr(days) {
  const d = new Date();
  d.setDate(d.getDate() + Number(days || 0));
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

Page({
  data: {
    imagePath: "",
    items: [],
    rawText: "",
    location: "",
    note: "",
    bulkExpiresAt: "",
    saving: false,
    today: todayStr(),
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
        expiresAt: "",
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

  onItemExpiresChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({
      [`items[${index}].expiresAt`]: e.detail.value,
    });
  },

  setItemExpiresQuick(e) {
    const index = Number(e.currentTarget.dataset.index);
    const days = Number(e.currentTarget.dataset.days);
    this.setData({
      [`items[${index}].expiresAt`]: addDaysStr(days),
    });
  },

  clearItemExpires(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({
      [`items[${index}].expiresAt`]: "",
    });
  },

  onBulkExpiresChange(e) {
    this.setData({ bulkExpiresAt: e.detail.value });
  },

  setBulkExpiresQuick(e) {
    const days = Number(e.currentTarget.dataset.days);
    this.setData({ bulkExpiresAt: addDaysStr(days) });
  },

  clearBulkExpires() {
    this.setData({ bulkExpiresAt: "" });
  },

  applyBulkExpires() {
    const bulk = this.data.bulkExpiresAt;
    if (!bulk) {
      wx.showToast({ title: "请先选择统一保质期", icon: "none" });
      return;
    }
    const items = (this.data.items || []).map((item) => {
      if (item.expiresAt) return item;
      return { ...item, expiresAt: bulk };
    });
    this.setData({ items });
    wx.showToast({ title: "已应用到未设置的项目", icon: "success" });
  },

  addItem() {
    this.setData({
      items: (this.data.items || []).concat([{
        name: "",
        quantity: "1",
        unit: "份",
        expiresAt: "",
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
            expiresAt: item.expiresAt || null,
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
