/* eslint-disable no-undef */
import request from "../../utils/request";

const STATUS_MAP = {
  NORMAL: { label: "新鲜", cls: "fresh" },
  EXPIRING: { label: "即将过期", cls: "expiring" },
  EXPIRED: { label: "已过期", cls: "expired" },
};

Page({
  data: {
    activeTab: "ALL",
    tabs: [
      { key: "ALL", label: "全部" },
      { key: "EXPIRING", label: "即将过期" },
      { key: "EXPIRED", label: "已过期" },
    ],
    items: [],
    loading: true,
  },

  onShow() {
    this.loadItems();
  },

  async loadItems() {
    this.setData({ loading: true });
    try {
      const items = await request({
        url: `/fridge/items?status=${this.data.activeTab}`,
      });
      const enriched = items.map((item) => {
        const status = STATUS_MAP[item.reminderState] || STATUS_MAP.NORMAL;
        return {
          ...item,
          statusLabel: status.label,
          statusCls: status.cls,
        };
      });
      this.setData({ items: enriched });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  },

  changeTab(e) {
    const key = e.currentTarget.dataset.key;
    if (key === this.data.activeTab) return;
    this.setData({ activeTab: key });
    this.loadItems();
  },

  createItem() {
    wx.navigateTo({ url: "/pages/fridge/edit" });
  },

  editItem(e) {
    wx.navigateTo({ url: `/pages/fridge/edit?id=${e.currentTarget.dataset.id}` });
  },

  consumeItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "消耗库存",
      editable: true,
      placeholderText: "输入消耗数量",
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await request({
            url: `/fridge/items/${id}/consume`,
            method: "POST",
            data: { quantity: Number(res.content || 0) },
          });
          this.loadItems();
        } catch (error) {
          wx.showToast({ title: error.message, icon: "none" });
        }
      },
    });
  },

  discardItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "确认丢弃",
      content: "这会把该库存标记为已丢弃。",
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await request({
            url: `/fridge/items/${id}/discard`,
            method: "POST",
          });
          this.loadItems();
        } catch (error) {
          wx.showToast({ title: error.message, icon: "none" });
        }
      },
    });
  },
});
