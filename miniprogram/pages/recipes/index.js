/* eslint-disable no-undef */
import request from "../../utils/request";

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

  showRecipePlanner() {
    wx.showModal({
      title: "功能正在规划中",
      content: "功能正在规划中，先用手动添加吧",
      confirmText: "去手动添加",
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        wx.navigateTo({
          url: "/pages/recipe-edit/index",
        });
      },
    });
  },
});
