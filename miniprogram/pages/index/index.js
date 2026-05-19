/* eslint-disable no-undef */
import { ensureLogin } from "../../utils/session";
import request from "../../utils/request";

Page({
  data: {
    loading: true,
    message: "正在为你准备家庭空间...",
    error: "",
  },

  onLoad() {
    this.bootstrap();
  },

  async bootstrap() {
    try {
      await ensureLogin();
      const family = await request({
        url: "/families/current",
      });
      wx.reLaunch({
        url: "/pages/home/index",
      });
      getApp().globalData.family = family;
    } catch (error) {
      if ((error.message || "").indexOf("还未加入家庭") >= 0) {
        wx.reLaunch({
          url: "/pages/family/index",
        });
        return;
      }
      this.setData({
        loading: false,
        error: error.message || "启动失败，请稍后重试",
      });
    }
  },

  retry() {
    this.setData({
      loading: true,
      error: "",
    });
    this.bootstrap();
  },
});
