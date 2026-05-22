/* eslint-disable no-undef */
import request from "../../utils/request";

function flattenCatalog(data) {
  return [
    ...(data.systemIngredients || []),
    ...(data.familyIngredients || []),
  ];
}

function toSelectedItem(item, fallbackUnit) {
  if (!item) {
    return null;
  }
  return {
    id: item.id,
    sourceType: item.sourceType,
    name: item.name,
    category: item.category,
    secondaryCategory: item.secondaryCategory || "",
    defaultUnit: item.defaultUnit || fallbackUnit || "",
  };
}

Page({
  data: {
    id: null,
    catalog: [],
    sourceType: "SYSTEM",
    sourceId: null,
    selectedItem: null,
    quantity: "1",
    unit: "",
    producedAt: "",
    expiresAt: "",
    location: "",
    note: "",
    customName: "",
  },

  async onLoad(query) {
    this.setData({
      id: query.id || null,
    });
    await this.loadCatalog();
    if (query.id) {
      await this.loadCurrentItem(query.id);
    }
  },

  async loadCatalog() {
    const catalogData = await request({
      url: "/ingredients/catalog",
      data: {
        includeSystem: false,
      },
    });
    const catalog = flattenCatalog(catalogData);
    const first = catalog[0];
    this.setData({
      catalog,
      unit: this.data.unit || (first ? first.defaultUnit : ""),
      sourceId: this.data.sourceId || (first ? first.id : null),
      sourceType: this.data.sourceId ? this.data.sourceType : (first ? first.sourceType : "SYSTEM"),
      selectedItem: this.data.selectedItem || toSelectedItem(first),
    });
  },

  async loadCurrentItem(id) {
    const all = await request({
      url: "/fridge/items?status=ALL",
    });
    const current = all.find((item) => `${item.id}` === `${id}`);
    if (!current) {
      return;
    }
    this.setData({
      sourceType: current.sourceType,
      sourceId: current.sourceId,
      selectedItem: toSelectedItem({
        id: current.sourceId,
        sourceType: current.sourceType,
        name: current.name,
        category: current.category,
        defaultUnit: current.unit,
      }, current.unit),
      quantity: `${current.quantity}`,
      unit: current.unit,
      producedAt: current.producedAt || "",
      expiresAt: current.expiresAt || "",
      location: current.location || "",
      note: current.note || "",
      customName: current.sourceType === "CUSTOM" ? current.name : "",
    });
  },

  onSourceChange(e) {
    const selected = e.detail.item;
    if (!selected) return;
    this.setData({
      sourceId: selected.id,
      sourceType: selected.sourceType,
      selectedItem: toSelectedItem(selected),
      unit: selected.defaultUnit,
    });
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [field]: e.detail.value,
    });
  },

  onDateChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [field]: e.detail.value,
    });
  },

  async saveItem() {
    if (!this.data.sourceId) {
      wx.showToast({ title: "请选择食材", icon: "none" });
      return;
    }
    const payload = {
      sourceType: this.data.sourceType,
      sourceId: this.data.sourceId,
      customName: this.data.customName,
      quantity: Number(this.data.quantity),
      unit: this.data.unit,
      producedAt: this.data.producedAt || null,
      expiresAt: this.data.expiresAt || null,
      location: this.data.location,
      note: this.data.note,
    };
    try {
      if (this.data.id) {
        await request({
          url: `/fridge/items/${this.data.id}`,
          method: "PUT",
          data: payload,
        });
      } else {
        await request({
          url: "/fridge/items",
          method: "POST",
          data: payload,
        });
      }
      wx.navigateBack();
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  goIngredients() {
    wx.navigateTo({
      url: "/pages/ingredients/index",
    });
  },
});
