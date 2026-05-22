/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const OCR_DRAFT_RECIPE_KEY = "life_recipe_import_draft";

function cleanErrorMessage(raw) {
  if (!raw) return "识别失败";
  let msg = String(raw);
  // Strip leading "Maximum upload size exceeded; nested exception is ..." chains and class names
  const semi = msg.indexOf("; nested exception");
  if (semi > 0) msg = msg.substring(0, semi);
  msg = msg.replace(/[a-z]+(?:\.[a-zA-Z_$][\w$]*)+(?:Exception|Error)\s*:?\s*/g, "");
  msg = msg.trim();
  if (/maximum.*size/i.test(msg) || /exceeds.*permitted size/i.test(msg)) {
    return "图片太大了，请压缩到 10MB 以内后再试";
  }
  return msg.length > 120 ? msg.substring(0, 120) + "..." : msg;
}

function showErrorAlert(message) {
  wx.showModal({
    title: "操作失败",
    content: message || "服务异常，请稍后再试",
    showCancel: false,
    confirmText: "我知道了",
  });
}

Page({
  data: {
    recipes: [],
    filteredRecipes: [],
    keyword: "",
    loading: true,
  },

  onShow() {
    this.loadRecipes();
  },

  async loadRecipes() {
    this.setData({
      loading: true,
    });
    try {
      const recipes = await request({ url: "/recipes" });
      this.setData({
        recipes: recipes || [],
      });
      this.applyFilter();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({
        loading: false,
      });
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value });
    this.applyFilter();
  },

  clearKeyword() {
    this.setData({ keyword: "" });
    this.applyFilter();
  },

  applyFilter() {
    const keyword = (this.data.keyword || "").trim().toLowerCase();
    if (!keyword) {
      this.setData({ filteredRecipes: this.data.recipes });
      return;
    }
    const filtered = this.data.recipes.filter((item) => {
      const name = (item.name || "").toLowerCase();
      const note = (item.note || "").toLowerCase();
      return name.includes(keyword) || note.includes(keyword);
    });
    this.setData({ filteredRecipes: filtered });
  },

  createRecipe() {
    wx.navigateTo({
      url: "/pages/recipe-edit/index",
    });
  },

  openRecipe(e) {
    wx.navigateTo({
      url: `/pages/recipe-detail/index?id=${e.currentTarget.dataset.id}`,
    });
  },

  chooseImage() {
    return new Promise((resolve, reject) => {
      wx.chooseImage({
        count: 1,
        sizeType: ["compressed"],
        sourceType: ["album", "camera"],
        success: resolve,
        fail: reject,
      });
    });
  },

  uploadRecognition(url, filePath) {
    return new Promise((resolve, reject) => {
      const token = localStorage.getItem("life_token");
      const app = getApp();
      const baseUrl = app.globalData.baseUrl || "http://127.0.0.1:8080/api";
      const doUpload = (path) => {
        wx.uploadFile({
          url: `${baseUrl}${url}`,
          filePath: path,
          name: "file",
          header: {
            Authorization: `Bearer ${token || ""}`,
          },
          success: (res) => {
            try {
              const payload = JSON.parse(res.data || "{}");
              if (payload.success) {
                resolve(payload.data);
                return;
              }
              const rawMsg = payload.message || "识别失败";
              console.error("[upload] server error", res.statusCode, rawMsg);
              reject(new Error(cleanErrorMessage(rawMsg)));
            } catch (error) {
              console.error("[upload] parse error", res.statusCode, res.data);
              if (res.statusCode && res.statusCode >= 400) {
                reject(new Error(`服务异常 (${res.statusCode})，请稍后再试`));
                return;
              }
              reject(new Error("识别结果解析失败"));
            }
          },
          fail: (err) => {
            console.error("[upload] network fail", err);
            reject(new Error((err && err.errMsg) || "网络异常"));
          },
        });
      };
      wx.compressImage({
        src: filePath,
        quality: 90,
        success: (res) => doUpload(res.tempFilePath || filePath),
        fail: () => doUpload(filePath),
      });
    });
  },

  async showRecipePlanner() {
    try {
      const image = await this.chooseImage();
      const filePath = image.tempFilePaths && image.tempFilePaths[0];
      if (!filePath) {
        return;
      }
      wx.showLoading({ title: "正在识别菜谱...", mask: true });
      const result = await this.uploadRecognition("/image-recognition/recipe", filePath);
      wx.hideLoading();
      localStorage.setItem(OCR_DRAFT_RECIPE_KEY, {
        ...result,
        imagePath: filePath,
      });
      wx.navigateTo({
        url: "/pages/recipe-edit/index?from=imageRecognition",
      });
    } catch (error) {
      wx.hideLoading();
      if (error && error.errMsg && error.errMsg.indexOf("cancel") >= 0) {
        return;
      }
      console.error("[showRecipePlanner] failed", error);
      showErrorAlert(error.message || "识别失败");
    }
  },
});
