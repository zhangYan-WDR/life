/* eslint-disable no-undef */
import request from "../../utils/request";

Page({
  data: {
    familyIngredients: [],
    groupedSystemIngredients: [],
    form: {
      name: "",
      category: "",
      secondaryCategory: "",
      defaultUnit: "",
    },
  },

  onShow() {
    this.loadCatalog();
  },

  async loadCatalog() {
    try {
      const [catalogData, systemGroups] = await Promise.all([
        request({
          url: "/ingredients/catalog",
          data: {
            includeSystem: false,
          },
        }),
        request({
          url: "/ingredients/system-groups",
          data: {
            previewSize: 12,
          },
        }),
      ]);
      this.setData({
        familyIngredients: catalogData.familyIngredients || [],
        groupedSystemIngredients: systemGroups || [],
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
          secondaryCategory: "",
          defaultUnit: "",
        },
      });
      this.loadCatalog();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },
});
