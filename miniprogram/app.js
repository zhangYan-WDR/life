import i18n from "./utils/i18n.js";
import localStorage from "./utils/localStorage.js";

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
    this.globalData.baseUrl = "http://127.0.0.1:8080/api";
    this.globalData.token = localStorage.getItem("life_token");
  },
  globalData: {
    theme: null,
    language: "zh",
    baseUrl: "",
    token: "",
  },
});
