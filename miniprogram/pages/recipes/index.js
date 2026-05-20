/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    recipes: [],
    loading: true,
    randomRecipe: null,
    mode: "",
  },

  onLoad(query) {
    this.setData({
      mode: query.mode || "",
    });
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
      if (this.data.mode === "random" && (recipes || []).length && !this.data.randomRecipe) {
        this.pickRandomRecipe();
      }
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({
        loading: false,
      });
    }
  },

  async pickRandomRecipe() {
    try {
      const randomRecipe = await request({ url: "/recipes/random" });
      this.setData({
        randomRecipe,
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  closeRandom() {
    this.setData({ randomRecipe: null });
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

  orderRandomRecipe() {
    const recipe = this.data.randomRecipe;
    if (!recipe) {
      return;
    }
    wx.navigateTo({
      url: `/pages/meal-request-edit/index?recipeId=${recipe.id}`,
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
