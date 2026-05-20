/* eslint-disable no-undef */
import request from "../../utils/request";

function flattenCatalog(data) {
  return [
    ...(data.systemIngredients || []),
    ...(data.familyIngredients || []),
  ];
}

function createIngredientLine(catalog) {
  const first = catalog[0] || {};
  return {
    catalogIndex: 0,
    sourceType: first.sourceType || "SYSTEM",
    sourceId: first.id || null,
    quantity: "1",
    unit: first.defaultUnit || "",
  };
}

Page({
  data: {
    id: null,
    catalog: [],
    form: {
      name: "",
      baseServings: "2",
      instructions: "",
      note: "",
      ingredients: [],
    },
  },

  async onLoad(query) {
    this.setData({
      id: query.id || null,
    });
    await this.loadCatalog();
    if (query.id) {
      await this.loadRecipe(query.id);
    } else {
      this.setData({
        "form.ingredients": [createIngredientLine(this.data.catalog)],
      });
    }
  },

  async loadCatalog() {
    const data = await request({ url: "/ingredients/catalog" });
    this.setData({
      catalog: flattenCatalog(data),
    });
  },

  async loadRecipe(id) {
    try {
      const recipe = await request({ url: `/recipes/${id}` });
      const ingredients = (recipe.ingredients || []).map((item) => ({
        catalogIndex: Math.max(this.data.catalog.findIndex((catalogItem) => catalogItem.id === item.sourceId && catalogItem.sourceType === item.sourceType), 0),
        sourceType: item.sourceType,
        sourceId: item.sourceId,
        quantity: `${item.quantity}`,
        unit: item.unit,
      }));
      this.setData({
        form: {
          name: recipe.name || "",
          baseServings: `${recipe.baseServings || 2}`,
          instructions: recipe.instructions || "",
          note: recipe.note || "",
          ingredients: ingredients.length ? ingredients : [createIngredientLine(this.data.catalog)],
        },
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

  onIngredientFieldInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`form.ingredients[${index}].${field}`]: e.detail.value,
    });
  },

  onIngredientSourceChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    const selected = this.data.catalog[Number(e.detail.value)];
    if (!selected) {
      return;
    }
    this.setData({
      [`form.ingredients[${index}].catalogIndex`]: Number(e.detail.value),
      [`form.ingredients[${index}].sourceType`]: selected.sourceType,
      [`form.ingredients[${index}].sourceId`]: selected.id,
      [`form.ingredients[${index}].unit`]: selected.defaultUnit,
    });
  },

  addIngredient() {
    const next = (this.data.form.ingredients || []).concat([createIngredientLine(this.data.catalog)]);
    this.setData({
      "form.ingredients": next,
    });
  },

  removeIngredient(e) {
    const index = Number(e.currentTarget.dataset.index);
    const ingredients = (this.data.form.ingredients || []).slice();
    ingredients.splice(index, 1);
    this.setData({
      "form.ingredients": ingredients.length ? ingredients : [createIngredientLine(this.data.catalog)],
    });
  },

  goIngredients() {
    wx.navigateTo({
      url: "/pages/ingredients/index",
    });
  },

  async saveRecipe() {
    const payload = {
      name: this.data.form.name,
      baseServings: Number(this.data.form.baseServings),
      instructions: this.data.form.instructions,
      note: this.data.form.note,
      ingredients: (this.data.form.ingredients || []).map((item) => ({
        sourceType: item.sourceType,
        sourceId: item.sourceId,
        quantity: Number(item.quantity),
        unit: item.unit,
      })),
    };
    try {
      if (this.data.id) {
        await request({
          url: `/recipes/${this.data.id}`,
          method: "PUT",
          data: payload,
        });
      } else {
        await request({
          url: "/recipes",
          method: "POST",
          data: payload,
        });
      }
      wx.navigateBack();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },
});
