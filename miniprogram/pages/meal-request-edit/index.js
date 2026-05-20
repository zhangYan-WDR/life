/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    title: "",
    note: "",
    recipes: [],
    selectedCount: 0,
  },

  async onLoad(query) {
    await this.loadRecipes(query.recipeId || "");
  },

  async loadRecipes(preselectedId) {
    try {
      const recipes = await request({ url: "/recipes" });
      const mapped = (recipes || []).map((item) => ({
        ...item,
        selected: `${item.id}` === `${preselectedId}`,
        targetServings: `${item.baseServings || 2}`,
      }));
      this.setData({
        recipes: mapped,
        selectedCount: mapped.filter((r) => r.selected).length,
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [field]: e.detail.value,
    });
  },

  toggleRecipe(e) {
    const index = Number(e.currentTarget.dataset.index);
    const recipes = this.data.recipes.slice();
    recipes[index] = { ...recipes[index], selected: !recipes[index].selected };
    this.setData({
      recipes,
      selectedCount: recipes.filter((r) => r.selected).length,
    });
  },

  noop() {},

  onServingsInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({
      [`recipes[${index}].targetServings`]: e.detail.value,
    });
  },

  async saveMealRequest() {
    const selectedRecipes = (this.data.recipes || [])
      .filter((item) => item.selected)
      .map((item) => ({
        recipeId: item.id,
        targetServings: Number(item.targetServings),
      }));
    try {
      const detail = await request({
        url: "/meal-requests",
        method: "POST",
        data: {
          title: this.data.title,
          note: this.data.note,
          recipes: selectedRecipes,
        },
      });
      wx.redirectTo({
        url: `/pages/meal-request-detail/index?id=${detail.id}`,
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },
});
