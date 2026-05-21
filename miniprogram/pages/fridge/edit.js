/* eslint-disable no-undef */
import request from "../../utils/request";

function flattenCatalog(data) {
  return [
    ...(data.systemIngredients || []),
    ...(data.familyIngredients || []),
  ];
}

Page({
  data: {
    id: null,
    catalog: [],
    sourceType: "SYSTEM",
    sourceId: null,
    sourceIndex: 0,
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
    });
    const catalog = flattenCatalog(catalogData);
    const first = catalog[0];
    this.setData({
      catalog,
      unit: this.data.unit || (first ? first.defaultUnit : ""),
      sourceId: this.data.sourceId || (first ? first.id : null),
      sourceType: this.data.sourceId ? this.data.sourceType : (first ? first.sourceType : "SYSTEM"),
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
    const index = this.data.catalog.findIndex((item) => `${item.id}` === `${current.sourceId}` && item.sourceType === current.sourceType);
    this.setData({
      sourceType: current.sourceType,
      sourceId: current.sourceId,
      sourceIndex: index >= 0 ? index : 0,
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
    const index = this.data.catalog.findIndex(
      (it) => `${it.id}` === `${selected.id}` && it.sourceType === selected.sourceType,
    );
    this.setData({
      sourceIndex: index >= 0 ? index : 0,
      sourceId: selected.id,
      sourceType: selected.sourceType,
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
