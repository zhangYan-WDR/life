import i18n from "./utils/i18n.js";

App({
  onLaunch() {
    const { theme, language } = wx.getSystemInfoSync();
    this.globalData.theme = theme || "light";
    if (language.includes("zh")) {
      i18n.lang = "zh";
    } else {
      i18n.lang = "en";
    }
    this.globalData.language = i18n.lang;
  },
  globalData: {
    theme: null,
    language: "zh",
  },
});
