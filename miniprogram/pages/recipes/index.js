/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const OCR_DRAFT_RECIPE_KEY = "life_recipe_import_draft";

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
      wx.uploadFile({
        url: `${baseUrl}${url}`,
        filePath,
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
            reject(new Error(payload.message || "识别失败"));
          } catch (error) {
            reject(new Error("识别结果解析失败"));
          }
        },
        fail: reject,
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
      wx.showToast({ title: error.message || "识别失败", icon: "none" });
    }
  },
});
