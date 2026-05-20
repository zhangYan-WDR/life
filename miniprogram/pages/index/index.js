/* eslint-disable no-undef */
import { ensureLogin } from "../../utils/session";
import request from "../../utils/request";

Page({
  data: {
    loading: true,
    ready: false,
    routeTarget: "/pages/family/index",
    buttonText: "欢迎来到小家",
    error: "",
    autoEntering: false,
    sceneReady: false,
  },

  onLoad() {
    setTimeout(() => {
      this.setData({ sceneReady: true });
    }, 300);
    this.bootstrap();
  },

  onUnload() {
    this.clearEnterTimer();
  },

  async bootstrap() {
    try {
      await ensureLogin();
      const family = await request({ url: "/families/current" });
      getApp().globalData.family = family;
      this.setData({
        loading: false,
        ready: true,
        routeTarget: "/pages/home/index",
        buttonText: "欢迎回来，进入小家",
      });
    } catch (error) {
      if ((error.message || "").indexOf("还未加入家庭") >= 0) {
        this.setData({
          loading: false,
          ready: true,
          routeTarget: "/pages/family/index",
          buttonText: "欢迎来到小家",
        });
        return;
      }
      this.setData({
        loading: false,
        ready: false,
        error: error.message || "启动失败，请稍后重试",
      });
    }
  },

  clearEnterTimer() {
    if (this.enterTimer) {
      clearTimeout(this.enterTimer);
      this.enterTimer = null;
    }
  },

  enterNextStep() {
    if (!this.data.ready) return;
    wx.reLaunch({ url: this.data.routeTarget });
  },

  retry() {
    this.setData({
      loading: true,
      ready: false,
      error: "",
    });
    this.bootstrap();
  },
});
