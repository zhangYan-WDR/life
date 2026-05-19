/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    activeTab: "ALL",
    tabs: [
      { key: "ALL", label: "全部" },
      { key: "EXPIRING", label: "即将过期" },
      { key: "EXPIRED", label: "已过期" },
    ],
    items: [],
  },

  onShow() {
    this.loadItems();
  },

  async loadItems() {
    try {
      const items = await request({
        url: `/fridge/items?status=${this.data.activeTab}`,
      });
      this.setData({ items });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  changeTab(e) {
    this.setData({
      activeTab: e.currentTarget.dataset.key,
    });
    this.loadItems();
  },

  createItem() {
    wx.navigateTo({
      url: "/pages/fridge/edit",
    });
  },

  editItem(e) {
    wx.navigateTo({
      url: `/pages/fridge/edit?id=${e.currentTarget.dataset.id}`,
    });
  },

  consumeItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "消耗库存",
      editable: true,
      placeholderText: "输入消耗数量",
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          await request({
            url: `/fridge/items/${id}/consume`,
            method: "POST",
            data: {
              quantity: Number(res.content || 0),
            },
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
        if (!res.confirm) {
          return;
        }
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
