/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    recipe: null,
    id: null,
  },

  onLoad(query) {
    this.setData({
      id: query.id,
    });
  },

  onShow() {
    if (this.data.id) {
      this.loadRecipe(this.data.id);
    }
  },

  async loadRecipe(id) {
    try {
      const recipe = await request({ url: `/recipes/${id}` });
      this.setData({
        recipe,
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  editRecipe() {
    wx.navigateTo({
      url: `/pages/recipe-edit/index?id=${this.data.id}`,
    });
  },

  orderRecipe() {
    wx.navigateTo({
      url: `/pages/meal-request-edit/index?recipeId=${this.data.id}`,
    });
  },
});
