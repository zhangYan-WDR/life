/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    systemIngredients: [],
    familyIngredients: [],
    form: {
      name: "",
      category: "",
      defaultUnit: "",
    },
  },

  onShow() {
    this.loadCatalog();
  },

  async loadCatalog() {
    try {
      const data = await request({
        url: "/ingredients/catalog",
      });
      this.setData({
        systemIngredients: data.systemIngredients || [],
        familyIngredients: data.familyIngredients || [],
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`form.${field}`]: e.detail.value,
    });
  },

  async createIngredient() {
    try {
      await request({
        url: "/ingredients/family",
        method: "POST",
        data: this.data.form,
      });
      this.setData({
        form: {
          name: "",
          category: "",
          defaultUnit: "",
        },
      });
      this.loadCatalog();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },
});
