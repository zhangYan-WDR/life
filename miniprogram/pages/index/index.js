/* eslint-disable no-undef */
// index.js
// 获取应用实例
import shareAppMessage from "../../utils/common";
import i18n from "../../utils/i18n";

// import i18nBehavior from '../../utils/i18nBehavior';
const app = getApp();
Page({
  // behaviors: [i18nBehavior],
  data: {
    theme: app.globalData.theme,
    isEn: app.globalData.language === "en",
    policyChecked: false,
    showLearn: false,
    i18n: {
      custom: i18n.t("custom"),
      custom_des: i18n.t("custom_des"),
      index_3: i18n.t("index_3"),
      godemo: i18n.t("godemo"),
      native: i18n.t("native"),
      native_des: i18n.t("native_des"),
      learnMore: i18n.t("learnMore"),
      pri1: i18n.t("pri1"),
      pri2: i18n.t("pri2"),
      logoName: i18n.t("logoName"),
      logoDesc: i18n.t("logoDesc"),
      webar: i18n.t("webar"),
    },
  },
  // 事件处理函数
  async goDemo(event) {
    const id = event.currentTarget.id;
    wx.navigateTo({
      url: id === "custom" ? "/pages/camera/camera" : "/pages/native/index",
    });
  },
  onReady() {
    var rect = wx.getMenuButtonBoundingClientRect();
    var totalHeight = rect.bottom;
    this.setData({ totalHeight });
  },
  onLoad() {
    // const {theme} = global
    // this.setData({
    //   theme
    // })
  },
  onShareAppMessage() {
    return shareAppMessage();
  },
});
