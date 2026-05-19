/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

Page({
  data: {
    family: null,
    summary: {
      total: 0,
      expiringSoon: 0,
      expired: 0,
    },
    loading: true,
    errorText: "",
  },

  onLoad() {
    const cachedFamily = localStorage.getItem("life_current_family");
    if (cachedFamily) {
      this.setData({
        family: cachedFamily,
      });
    }
  },

  onShow() {
    this.loadPage();
  },

  async loadPage() {
    this.setData({
      loading: true,
      errorText: "",
    });
    try {
      const family = await request({ url: "/families/current" });
      this.setData({
        family,
      });
      localStorage.setItem("life_current_family", family);
    } catch (error) {
      this.setData({
        errorText: error.message || "家庭信息加载失败",
      });
    }

    try {
      const summary = await request({ url: "/fridge/reminders/summary" });
      this.setData({
        summary,
      });
    } catch (error) {
      const fallbackMessage = error.message || "库存汇总加载失败";
      this.setData({
        errorText: this.data.errorText || fallbackMessage,
      });
    } finally {
      this.setData({
        loading: false,
      });
    }
  },

  goFridge() {
    wx.navigateTo({
      url: "/pages/fridge/index",
    });
  },

  goIngredients() {
    wx.navigateTo({
      url: "/pages/ingredients/index",
    });
  },

  subscribeReminder() {
    request({
      url: "/subscriptions/expiry-reminder",
      method: "POST",
      data: { accepted: true },
    }).then(() => {
      wx.showToast({ title: "已标记提醒订阅", icon: "success" });
    }).catch((error) => {
      wx.showToast({ title: error.message, icon: "none" });
    });
  },

  retryLoad() {
    this.loadPage();
  },
});
